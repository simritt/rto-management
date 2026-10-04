package com.rto.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.core.ApiException;
import com.rto.core.Clock;
import com.rto.core.CurrentUser;
import com.rto.domain.AppointmentSlot;
import com.rto.domain.Citizen;
import com.rto.domain.DocumentType;
import com.rto.service.AppointmentService;
import com.rto.service.UserLoader;
import com.rto.support.Api.Resp;
import com.rto.support.BaseIT;
import com.rto.support.Flows;
import com.rto.support.Flows.Env;
import com.rto.support.Fx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.UncategorizedSQLException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static com.rto.support.Api.m;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentAppointmentTests extends BaseIT {
    @Autowired AppointmentService appointments;
    @Autowired UserLoader loader;

    Env env;

    @BeforeEach
    void setup() {
        env = flows.makeEnv();
    }

    private long id(JsonNode n) {
        return n.get("application_id").asLong();
    }

    private Resp book(long appId, long slotId, String token) {
        return api.post("/api/v1/applications/" + appId + "/appointments", token != null ? token : env.token, m("slot_id", slotId));
    }

    private long slotBooked(AppointmentSlot s) {
        return fx.count("SELECT booked_count FROM appointment_slots WHERE slot_id = ?", s.getSlotId());
    }

    // ------------------------------- documents -------------------------------

    @Test
    void uploadStoresMetadataAndFilePathNotBytes() {
        JsonNode a = flows.newApp(env);
        Resp r = flows.upload(env, id(a));
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
        assertThat(r.s("verification_status")).isEqualTo("PENDING");
        assertThat(r.s("file_path")).startsWith("applications/" + id(a) + "/").endsWith(".png");
        assertThat(fx.jdbc.queryForObject("SELECT file_path FROM documents WHERE document_id = ?", String.class, r.l("document_id"))).isEqualTo(r.s("file_path"));
        Resp dl = api.get("/api/v1/documents/" + r.l("document_id") + "/file", env.token);
        assertThat(dl.status()).isEqualTo(200);
        assertThat(dl.raw()).isEqualTo(Flows.PNG);
        assertThat(fx.count("SELECT COUNT(*) FROM audit_logs WHERE table_name = 'documents' AND record_id = ?", r.l("document_id"))).isGreaterThan(0);
    }

    @Test
    void uploadValidation() {
        JsonNode a = flows.newApp(env);
        long aid = id(a);
        assertThat(flows.upload(env, aid, "MZ-not-a-png".getBytes(), "x.png", null).status()).isEqualTo(400);
        assertThat(flows.upload(env, aid, Flows.PNG, "x.exe", null).status()).isEqualTo(400);
        assertThat(flows.upload(env, aid, new byte[0], "x.pdf", null).status()).isEqualTo(400);
        assertThat(flows.upload(env, aid, Flows.PDF, "scan.PDF", null).status()).isEqualTo(201);
        assertThat(api.upload("/api/v1/applications/" + aid + "/documents", env.token, java.util.Map.of("document_type_id", "99999999"), "a.png", Flows.PNG).status()).isEqualTo(404);
        assertThat(api.upload("/api/v1/applications/99999999/documents", env.token, java.util.Map.of("document_type_id", String.valueOf(env.docType.getDocumentTypeId())), "a.png", Flows.PNG).status()).isEqualTo(404);
        flows.setStatus(env, aid, "CANCELLED", "withdrawn");
        assertThat(flows.upload(env, aid).status()).isEqualTo(409);
    }

    @Test
    void verifyRecordsEmployeeAndTimestamp() {
        JsonNode a = flows.newApp(env);
        Resp d = flows.upload(env, id(a));
        Resp v = api.patch("/api/v1/documents/" + d.l("document_id") + "/verify", env.token);
        assertThat(v.status()).isEqualTo(200);
        assertThat(v.s("verification_status")).isEqualTo("VERIFIED");
        assertThat(v.l("verified_by_employee_id")).isEqualTo(env.emp.getEmployeeId());
        assertThat(v.isNull("verified_at")).isFalse();
        assertThat(api.patch("/api/v1/documents/" + d.l("document_id") + "/verify", env.token).status()).isEqualTo(409);
    }

    @Test
    void onlyAuthorisedEmployeesVerify() {
        JsonNode a = flows.newApp(env);
        Resp d = flows.upload(env, id(a));
        String url = "/api/v1/documents/" + d.l("document_id") + "/verify";
        assertThat(api.patch(url, fx.citizenToken(env.citizen)).status()).isEqualTo(403);
        Resp nonEmployee = api.patch(url, fx.withPermissions("document.verify"));
        assertThat(nonEmployee.status()).isEqualTo(403);
        assertThat(nonEmployee.code()).isEqualTo("NOT_AN_EMPLOYEE");
    }

    @Test
    void rejectNeedsReasonAndRejectedNeverCountsAsVerified() {
        JsonNode a = flows.newApp(env);
        Resp d = flows.upload(env, id(a));
        String url = "/api/v1/documents/" + d.l("document_id");
        assertThat(api.patch(url + "/reject", env.token, m("reason", "no")).status()).isEqualTo(422);
        Resp r = api.patch(url + "/reject", env.token, m("reason", "Photo is not legible"));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.s("rejection_reason")).isEqualTo("Photo is not legible");
        assertThat(r.s("verification_status")).isEqualTo("REJECTED");
        assertThat(api.patch(url + "/verify", env.token).status()).isEqualTo(409);   // cannot be re-verified
        assertThat(api.patch(url + "/reject", env.token, m("reason", "second time")).status()).isEqualTo(409);
    }

    @Test
    void expiredDocumentCannotSatisfyTheWorkflow() {
        env.docType = fx.makeDocumentType(30);
        JsonNode a = flows.underVerification(env);
        Resp d = flows.upload(env, id(a));
        api.patch("/api/v1/documents/" + d.l("document_id") + "/verify", env.token);
        fx.jdbc.update("UPDATE documents SET verified_at = ? WHERE document_id = ?", java.sql.Timestamp.valueOf(Clock.now().minusDays(45)), d.l("document_id"));   // validity lapsed 15 days ago
        JsonNode docs = api.get("/api/v1/applications/" + id(a) + "/documents", env.token).json();
        assertThat(docs.get(0).get("verification_status").asText()).isEqualTo("VERIFIED");
        assertThat(docs.get(0).get("effective_status").asText()).isEqualTo("EXPIRED");
        Resp r = flows.setStatus(env, id(a), "AWAITING_PAYMENT", null);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.s("detail")).contains("EXPIRED");
        assertThat(api.patch("/api/v1/documents/" + d.l("document_id") + "/expire", env.token).status()).isEqualTo(200);
        assertThat(fx.jdbc.queryForObject("SELECT verification_status FROM documents WHERE document_id = ?", String.class, d.l("document_id"))).isEqualTo("EXPIRED");
    }

    @Test
    void citizenCanUploadToOwnApplicationButNotOthers() {
        Citizen other = fx.makeCitizen();
        JsonNode mine = flows.newApp(env);
        JsonNode theirs = flows.newApp(env.with(other));
        String h = fx.citizenToken(env.citizen);
        assertThat(flows.upload(env, id(mine), Flows.PNG, "id.png", h).status()).isEqualTo(201);
        assertThat(flows.upload(env, id(theirs), Flows.PNG, "id.png", h).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/applications/" + id(theirs) + "/documents", h).status()).isEqualTo(403);
    }

    // ------------------------------- slots -------------------------------

    @Test
    void slotCreationRules() {
        java.util.Map<String, Object> base = m("office_id", env.office.getOfficeId(), "slot_date", Clock.today().plusDays(2).toString(),
                "start_time", "09:00:00", "end_time", "09:30:00", "capacity", 3);
        Resp r = api.post("/api/v1/appointment-slots", env.token, base);
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.i("available")).isEqualTo(3);
        assertThat(r.i("booked_count")).isEqualTo(0);
        assertThat(api.post("/api/v1/appointment-slots", env.token, base).status()).isEqualTo(409);   // duplicate, even with a NULL counter
        assertThat(api.post("/api/v1/appointment-slots", env.token, flows.put(base, "end_time", "08:00:00")).status()).isEqualTo(422);
        assertThat(api.post("/api/v1/appointment-slots", env.token, flows.put(base, "slot_date", Clock.today().minusDays(1).toString(), "start_time", "10:00:00", "end_time", "10:30:00")).status()).isEqualTo(409);
        var inactive = fx.makeOffice(false);
        assertThat(api.post("/api/v1/appointment-slots", env.token, flows.put(base, "office_id", inactive.getOfficeId(), "start_time", "11:00:00", "end_time", "11:30:00")).status()).isEqualTo(409);
        Resp c = api.post("/api/v1/offices/" + env.office.getOfficeId() + "/counters", env.token, m("counter_number", "K1"));
        Resp foreign = api.post("/api/v1/offices/" + fx.makeOffice().getOfficeId() + "/counters", env.token, m("counter_number", "K1"));
        assertThat(api.post("/api/v1/appointment-slots", env.token, flows.put(base, "start_time", "12:00:00", "end_time", "12:30:00", "counter_id", c.l("counter_id"))).status()).isEqualTo(201);
        Resp bad = api.post("/api/v1/appointment-slots", env.token, flows.put(base, "start_time", "13:00:00", "end_time", "13:30:00", "counter_id", foreign.l("counter_id")));
        assertThat(bad.status()).isEqualTo(409);
        assertThat(bad.code()).isEqualTo("COUNTER_OFFICE_MISMATCH");
        assertThat(api.get("/api/v1/appointment-slots", env.token, m("office_id", env.office.getOfficeId(), "available_only", true)).l("total")).isGreaterThanOrEqualTo(2);
    }

    @Test
    void capacityCannotDropBelowBooked() {
        AppointmentSlot slot = fx.makeSlot(env.office, 2, 3, 10);
        JsonNode a = flows.underVerification(env);
        book(id(a), slot.getSlotId(), null);
        assertThat(api.patch("/api/v1/appointment-slots/" + slot.getSlotId(), env.token, m("capacity", 0)).status()).isEqualTo(422);
        assertThat(api.patch("/api/v1/appointment-slots/" + slot.getSlotId(), env.token, m("capacity", 5)).i("capacity")).isEqualTo(5);
    }

    // ------------------------------- booking -------------------------------

    @Test
    void bookingTakesASeatIssuesATokenAndAdvancesTheApplication() {
        AppointmentSlot slot = fx.makeSlot(env.office, 2, 3, 10);
        JsonNode a = flows.underVerification(env);
        Resp r = book(id(a), slot.getSlotId(), null);
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
        assertThat(r.s("status")).isEqualTo("BOOKED");
        assertThat(r.s("token_number")).isEqualTo("S" + slot.getSlotId() + "-001");
        assertThat(slotBooked(slot)).isEqualTo(1);
        JsonNode detail = api.get("/api/v1/applications/" + id(a), env.token).json();
        assertThat(detail.get("current_status").asText()).isEqualTo("APPOINTMENT_SCHEDULED");
        assertThat(detail.at("/appointment/token_number").asText()).endsWith("001");
        JsonNode hist = api.get("/api/v1/applications/" + id(a) + "/history", env.token).json();
        assertThat(hist.get(hist.size() - 1).get("new_status").asText()).isEqualTo("APPOINTMENT_SCHEDULED");
        assertThat(hist.get(hist.size() - 1).get("reason").asText()).isEqualTo("Appointment booked");
        JsonNode b = flows.underVerification(env.with(fx.makeCitizen()));
        assertThat(book(id(b), slot.getSlotId(), null).s("token_number")).endsWith("002");
    }

    @Test
    void bookingEligibilityAndDuplicates() {
        AppointmentSlot slot = fx.makeSlot(env.office, 5, 3, 10);
        JsonNode fresh = flows.newApp(env);   // still SUBMITTED
        Resp r = book(id(fresh), slot.getSlotId(), null);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("APPLICATION_NOT_ELIGIBLE");
        JsonNode a = flows.underVerification(env.with(fx.makeCitizen()));
        assertThat(book(id(a), slot.getSlotId(), null).status()).isEqualTo(201);
        Resp again = book(id(a), fx.makeSlot(env.office, 2, 3, 11).getSlotId(), null);
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isEqualTo("ALREADY_BOOKED");
        AppointmentSlot otherOffice = fx.makeSlot(fx.makeOffice(), 1, 3, 10);
        JsonNode b = flows.underVerification(env.with(fx.makeCitizen()));
        Resp mismatch = book(id(b), otherOffice.getSlotId(), null);
        assertThat(mismatch.status()).isEqualTo(409);
        assertThat(mismatch.code()).isEqualTo("SLOT_OFFICE_MISMATCH");
        assertThat(book(id(b), 99999999, null).status()).isEqualTo(409);
    }

    @Test
    void fullSlotReturns409AndBookedCountNeverExceedsCapacity() {
        AppointmentSlot slot = fx.makeSlot(env.office, 1, 3, 10);
        JsonNode first = flows.underVerification(env);
        JsonNode second = flows.underVerification(env.with(fx.makeCitizen()));
        assertThat(book(id(first), slot.getSlotId(), null).status()).isEqualTo(201);
        Resp r = book(id(second), slot.getSlotId(), null);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("SLOT_FULL");
        assertThat(slotBooked(slot)).isEqualTo(1);
        assertThat(api.get("/api/v1/applications/" + id(second), env.token).s("current_status")).isEqualTo("UNDER_VERIFICATION");
    }

    @Test
    void databaseCheckConstraintIsTheLastDefence() {
        AppointmentSlot slot = fx.makeSlot(env.office, 1, 3, 10);
        assertThatThrownBy(() -> fx.jdbc.update("UPDATE appointment_slots SET booked_count = 2 WHERE slot_id = ?", slot.getSlotId()))
                .hasMessageContaining("chk_slot_capacity");
    }

    @Test
    void concurrentBookingsNeverOverbook() throws Exception {
        int capacity = 3, contenders = 10;
        AppointmentSlot slot = fx.makeSlot(env.office, capacity, 3, 10);
        List<Long> appIds = new ArrayList<>();
        for (int i = 0; i < contenders; i++) appIds.add(id(flows.underVerification(env.with(fx.makeCitizen()))));
        List<String> outcomes = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (long appId : appIds) {
            Thread t = new Thread(() -> {
                try {
                    CurrentUser user = loader.load(env.user.getUserId());
                    start.await();
                    appointments.book(appId, slot.getSlotId(), user);
                    outcomes.add("ok");
                } catch (ApiException e) {
                    outcomes.add(e.code());
                } catch (Exception e) {
                    outcomes.add("ERR " + e);
                }
            });
            threads.add(t);
            t.start();
        }
        start.countDown();
        for (Thread t : threads) t.join();
        assertThat(outcomes.stream().filter("ok"::equals).count()).isEqualTo(capacity);
        assertThat(outcomes.stream().filter("SLOT_FULL"::equals).count()).isEqualTo(contenders - capacity);
        assertThat(slotBooked(slot)).isEqualTo(capacity);
        List<String> tokens = fx.jdbc.queryForList("SELECT token_number FROM appointments WHERE slot_id = ?", String.class, slot.getSlotId());
        assertThat(new HashSet<>(tokens)).hasSize(capacity).hasSize(tokens.size());   // no duplicate tokens either
    }

    @Test
    void cancelReleasesCapacityRevertsStatusAndAllowsRebooking() {
        AppointmentSlot slot = fx.makeSlot(env.office, 1, 3, 10);
        JsonNode a = flows.underVerification(env);
        Resp appt = book(id(a), slot.getSlotId(), null);
        Resp r = api.patch("/api/v1/appointments/" + appt.l("appointment_id") + "/cancel", env.token);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.s("status")).isEqualTo("CANCELLED");
        assertThat(slotBooked(slot)).isEqualTo(0);
        assertThat(api.get("/api/v1/applications/" + id(a), env.token).s("current_status")).isEqualTo("UNDER_VERIFICATION");
        assertThat(api.patch("/api/v1/appointments/" + appt.l("appointment_id") + "/cancel", env.token).status()).isEqualTo(409);
        Resp again = book(id(a), slot.getSlotId(), null);   // re-uses the unique appointment row
        assertThat(again.status()).isEqualTo(201);
        assertThat(again.l("appointment_id")).isEqualTo(appt.l("appointment_id"));
        assertThat(again.s("status")).isEqualTo("BOOKED");
        JsonNode other = flows.underVerification(env.with(fx.makeCitizen()));
        assertThat(book(id(other), slot.getSlotId(), null).status()).isEqualTo(409);
    }

    @Test
    void rescheduleMovesTheSeatAndAFailureChangesNothing() {
        AppointmentSlot s1 = fx.makeSlot(env.office, 2, 3, 9), s2 = fx.makeSlot(env.office, 2, 3, 10), full = fx.makeSlot(env.office, 1, 3, 11);
        JsonNode a = flows.underVerification(env);
        Resp appt = book(id(a), s1.getSlotId(), null);
        JsonNode blocker = flows.underVerification(env.with(fx.makeCitizen()));
        book(id(blocker), full.getSlotId(), null);
        String url = "/api/v1/appointments/" + appt.l("appointment_id") + "/reschedule";
        Resp r = api.patch(url, env.token, m("slot_id", full.getSlotId()));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("SLOT_FULL");
        assertThat(slotBooked(s1)).isEqualTo(1);
        assertThat(slotBooked(full)).isEqualTo(1);   // nothing moved
        assertThat(api.patch(url, env.token, m("slot_id", s1.getSlotId())).status()).isEqualTo(409);   // same slot
        Resp ok = api.patch(url, env.token, m("slot_id", s2.getSlotId()));
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.s("status")).isEqualTo("RESCHEDULED");
        assertThat(ok.l("slot_id")).isEqualTo(s2.getSlotId());
        assertThat(slotBooked(s1)).isEqualTo(0);
        assertThat(slotBooked(s2)).isEqualTo(1);
        assertThat(api.patch(url, env.token, m("slot_id", s1.getSlotId())).status()).isEqualTo(200);   // RESCHEDULED can move again
    }

    @Test
    void rejectingOrCancellingAnApplicationReleasesItsSeat() {
        AppointmentSlot slot = fx.makeSlot(env.office, 1, 3, 10);
        JsonNode a = flows.underVerification(env);
        book(id(a), slot.getSlotId(), null);
        assertThat(flows.setStatus(env, id(a), "REJECTED", "ineligible").status()).isEqualTo(200);
        assertThat(slotBooked(slot)).isEqualTo(0);
        assertThat(fx.jdbc.queryForObject("SELECT status FROM appointments WHERE application_id = ?", String.class, id(a))).isEqualTo("CANCELLED");
    }

    @Test
    void citizenBooksForSelfOnlyAndOutcomes() {
        AppointmentSlot slot = fx.makeSlot(env.office, 3, 3, 10);
        JsonNode mine = flows.underVerification(env);
        JsonNode theirs = flows.underVerification(env.with(fx.makeCitizen()));
        String h = fx.citizenToken(env.citizen);
        Resp r = book(id(mine), slot.getSlotId(), h);
        assertThat(r.status()).isEqualTo(201);
        assertThat(book(id(theirs), slot.getSlotId(), h).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/appointments", h).l("total")).isEqualTo(1);
        String url = "/api/v1/appointments/" + r.l("appointment_id") + "/outcome";
        Resp out = api.patch(url, env.token, m("status", "NO_SHOW"));
        assertThat(out.status()).isEqualTo(200);
        assertThat(out.s("status")).isEqualTo("NO_SHOW");
        assertThat(api.patch(url, env.token, m("status", "COMPLETED")).status()).isEqualTo(409);
        assertThat(api.patch(url, h, m("status", "COMPLETED")).status()).isEqualTo(403);
    }
}
