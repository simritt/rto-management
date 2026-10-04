package com.rto.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.core.Clock;
import com.rto.domain.Citizen;
import com.rto.domain.LicenceClass;
import com.rto.service.NotificationProvider;
import com.rto.support.Api.Resp;
import com.rto.support.BaseIT;
import com.rto.support.Flows.Env;
import com.rto.support.Fx;
import com.rto.support.TestBeans.SwitchableProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.rto.support.Api.m;
import static org.assertj.core.api.Assertions.assertThat;

class SupportTests extends BaseIT {
    @Autowired SwitchableProvider provider;

    Env env;

    @BeforeEach
    void setup() {
        env = flows.makeEnv();
        provider.reset();
    }

    @AfterEach
    void cleanup() {
        provider.reset();
    }

    // ------------------------------- complaints -------------------------------

    private Resp complaint(String token, Object... over) {
        return api.post("/api/v1/complaints", token, flows.put(m("office_id", env.office.getOfficeId(), "subject", "Long queue", "description", "Waited four hours at counter 3"), over));
    }

    @Test
    void citizenFilesOwnComplaintStaffCanFileOnBehalf() {
        String h = fx.citizenToken(env.citizen);
        Resp r = complaint(h);
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.s("status")).isEqualTo("OPEN");
        assertThat(r.l("citizen_id")).isEqualTo(env.citizen.getCitizenId());
        Citizen other = fx.makeCitizen();
        assertThat(complaint(h, "citizen_id", other.getCitizenId()).status()).isEqualTo(403);
        Resp staff = complaint(env.token, "citizen_id", other.getCitizenId());
        assertThat(staff.status()).isEqualTo(201);
        assertThat(staff.l("citizen_id")).isEqualTo(other.getCitizenId());
        assertThat(api.post("/api/v1/complaints", h, m("office_id", fx.makeOffice(false).getOfficeId(), "subject", "Long queue", "description", "Waited four hours")).status()).isEqualTo(409);
        assertThat(api.post("/api/v1/complaints", h, m("office_id", env.office.getOfficeId(), "subject", "x", "description", "short")).status()).isEqualTo(422);
        assertThat(fx.count("SELECT COUNT(*) FROM audit_logs WHERE table_name = 'complaints' AND record_id = ?", r.l("complaint_id"))).isGreaterThan(0);
    }

    @Test
    void complaintVisibilityAndListing() {
        Citizen mine = fx.makeCitizen(), theirs = fx.makeCitizen();
        String h = fx.citizenToken(mine);
        Resp c1 = complaint(h);
        Resp c2 = complaint(env.token, "citizen_id", theirs.getCitizenId());
        JsonNode own = api.get("/api/v1/complaints", h).json().get("items");
        assertThat(own).hasSize(1);
        assertThat(own.get(0).get("complaint_id").asLong()).isEqualTo(c1.l("complaint_id"));
        assertThat(api.get("/api/v1/complaints/" + c2.l("complaint_id"), h).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/complaints/" + c1.l("complaint_id"), h).status()).isEqualTo(200);
        Resp staff = api.get("/api/v1/complaints", env.token, m("office_id", env.office.getOfficeId(), "status", "OPEN", "page_size", 100));
        Set<Long> ids = new HashSet<>();
        staff.json().get("items").forEach(i -> ids.add(i.get("complaint_id").asLong()));
        assertThat(ids).contains(c1.l("complaint_id"), c2.l("complaint_id"));
        assertThat(api.get("/api/v1/complaints", env.token, m("search", "four hours", "citizen_id", theirs.getCitizenId())).l("total")).isEqualTo(1);
        assertThat(api.get("/api/v1/complaints", fx.withPermissions()).status()).isEqualTo(403);
    }

    @Test
    void complaintStatusWorkflowHasNoArbitraryBackwardsMoves() {
        Resp c = complaint(env.token, "citizen_id", env.citizen.getCitizenId());
        long cid = c.l("complaint_id");
        java.util.function.BiFunction<String, String, Resp> move = (s, note) -> api.patch("/api/v1/complaints/" + cid + "/status", env.token, m("status", s, "note", note));
        assertThat(move.apply("RESOLVED", null).status()).isEqualTo(409);   // cannot skip IN_PROGRESS
        assertThat(move.apply("OPEN", null).status()).isEqualTo(409);       // not a move
        assertThat(move.apply("IN_PROGRESS", "assigned to Ramesh").s("status")).isEqualTo("IN_PROGRESS");
        assertThat(move.apply("OPEN", null).status()).isEqualTo(409);       // backwards
        assertThat(move.apply("RESOLVED", null).s("status")).isEqualTo("RESOLVED");
        assertThat(move.apply("OPEN", null).status()).isEqualTo(409);
        assertThat(move.apply("IN_PROGRESS", null).s("status")).isEqualTo("IN_PROGRESS");   // explicit re-open is the only backwards step
        move.apply("RESOLVED", null);
        Resp done = move.apply("CLOSED", null);
        assertThat(done.s("status")).isEqualTo("CLOSED");
        assertThat(done.s("filed_at")).isEqualTo(c.s("filed_at"));   // filed_at preserved
        for (String s : new String[]{"OPEN", "IN_PROGRESS", "RESOLVED"}) {
            Resp r = move.apply(s, null);
            assertThat(r.status()).isEqualTo(409);
            assertThat(r.code()).isEqualTo("INVALID_TRANSITION");
        }
        assertThat(api.patch("/api/v1/complaints/" + cid + "/status", fx.citizenToken(env.citizen), m("status", "CLOSED")).status()).isEqualTo(403);
        List<String> audits = fx.jdbc.queryForList("SELECT CAST(new_values AS CHAR) FROM audit_logs WHERE table_name = 'complaints' AND record_id = ? AND action = 'UPDATE' ORDER BY audit_id", String.class, cid);
        assertThat(audits).hasSize(5);
        assertThat(audits.get(0)).contains("assigned to Ramesh");
    }

    // ------------------------------- appeals -------------------------------

    private Resp appeal(String token, String type, long id, Object... over) {
        return api.post("/api/v1/appeals", token, flows.put(m("against_type", type, "against_id", id, "grounds", "I was not driving the vehicle at that time"), over));
    }

    private Resp decide(long aid, String action, String note) {
        return api.post("/api/v1/appeals/" + aid + "/" + action, env.token, m("note", note));
    }

    /** A challan whose violation names env.citizen as the driver. */
    private Resp challanCase() {
        Fx.Refs refs = fx.makeVehicleRefs(false);
        Map<String, Object> b = m("registration_number", "MH12" + Fx.u(6), "chassis_number", "CH" + Fx.u(12), "engine_number", "EN" + Fx.u(12),
                "manufacture_year", 2020, "registering_office_id", env.office.getOfficeId(), "owner_citizen_id", env.citizen.getCitizenId(), "registration_date", "2020-01-01");
        b.putAll(refs.body());
        long vid = api.post("/api/v1/vehicles", env.token, b).l("vehicle_id");
        Resp v = api.post("/api/v1/violations", env.token, m("vehicle_id", vid, "driver_citizen_id", env.citizen.getCitizenId(),
                "violation_type_id", fx.makeViolationType("500.00").getViolationTypeId(), "location", "NH48",
                "occurred_at", LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(2).withNano(0).toString()));
        return api.post("/api/v1/challans", env.token, m("violation_ids", List.of(v.l("violation_id"))));
    }

    @Test
    void challanAppealUpheldCancelsTheChallan() {
        Resp ch = challanCase();
        String h = fx.citizenToken(env.citizen);
        Resp r = appeal(h, "CHALLAN", ch.l("challan_id"));
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.s("status")).isEqualTo("FILED");
        assertThat(r.isNull("resolved_at")).isTrue();
        long aid = r.l("appeal_id");
        assertThat(decide(aid, "uphold", null).code()).isEqualTo("INVALID_TRANSITION");   // must be reviewed first
        assertThat(api.post("/api/v1/appeals/" + aid + "/review", h).status()).isEqualTo(403);   // citizens cannot review
        assertThat(api.post("/api/v1/appeals/" + aid + "/review", env.token).s("status")).isEqualTo("UNDER_REVIEW");
        assertThat(api.post("/api/v1/appeals/" + aid + "/review", env.token).status()).isEqualTo(409);
        Resp up = decide(aid, "uphold", "driver was someone else");
        assertThat(up.status()).isEqualTo(200);
        assertThat(up.s("status")).isEqualTo("UPHELD");
        assertThat(up.isNull("resolved_at")).isFalse();
        assertThat(api.get("/api/v1/challans/" + ch.l("challan_id"), env.token).s("status")).isEqualTo("CANCELLED");
        for (String action : new String[]{"uphold", "dismiss", "review"}) assertThat(decide(aid, action, null).status()).as(action).isEqualTo(409);
        assertThat(decide(aid, "dismiss", null).code()).isEqualTo("APPEAL_ALREADY_RESOLVED");
        List<String> audit = fx.jdbc.queryForList("SELECT CAST(new_values AS CHAR) FROM audit_logs WHERE table_name = 'appeals' AND record_id = ? AND action = 'UPDATE' ORDER BY audit_id", String.class, aid);
        assertThat(audit.get(audit.size() - 1)).contains("UPHELD").contains("challan cancelled");
    }

    @Test
    void challanAppealDismissedReturnsADisputedChallanToIssued() {
        Resp ch = challanCase();
        long cid = ch.l("challan_id");
        api.post("/api/v1/challans/" + cid + "/dispute", env.token, m("reason", "citizen disputes"));
        long aid = appeal(fx.citizenToken(env.citizen), "CHALLAN", cid).l("appeal_id");
        api.post("/api/v1/appeals/" + aid + "/review", env.token);
        Resp d = decide(aid, "dismiss", "evidence is clear");
        assertThat(d.s("status")).isEqualTo("DISMISSED");
        assertThat(d.isNull("resolved_at")).isFalse();
        assertThat(api.get("/api/v1/challans/" + cid, env.token).s("status")).isEqualTo("ISSUED");
    }

    @Test
    void challanAppealTargetValidation() {
        Resp ch = challanCase();
        String h = fx.citizenToken(env.citizen);
        long cid = ch.l("challan_id");
        Resp missing = appeal(h, "CHALLAN", 99999999);
        assertThat(missing.status()).isEqualTo(409);
        assertThat(missing.code()).isEqualTo("INVALID_APPEAL_TARGET");
        Resp notEntitled = appeal(fx.citizenToken(fx.makeCitizen()), "CHALLAN", cid);
        assertThat(notEntitled.status()).isEqualTo(409);
        assertThat(notEntitled.code()).isEqualTo("APPEAL_NOT_ENTITLED");
        assertThat(appeal(h, "BOGUS", cid).status()).isEqualTo(422);
        assertThat(appeal(h, "CHALLAN", cid, "grounds", "short").status()).isEqualTo(422);
        assertThat(appeal(h, "CHALLAN", cid).status()).isEqualTo(201);
        Resp dup = appeal(h, "CHALLAN", cid);
        assertThat(dup.status()).isEqualTo(409);
        assertThat(dup.code()).isEqualTo("APPEAL_EXISTS");
        api.post("/api/v1/challans/" + cid + "/cancel", env.token, m("reason", "withdrawn fine"));
        assertThat(appeal(fx.citizenToken(env.citizen), "CHALLAN", cid).status()).isEqualTo(409);
    }

    @Test
    void applicationRejectionAppealReopensTheApplication() {
        JsonNode a = flows.newApp(env);
        long aid = a.get("application_id").asLong();
        String h = fx.citizenToken(env.citizen);
        Resp early = appeal(h, "APPLICATION_REJECTION", aid);
        assertThat(early.status()).isEqualTo(409);
        assertThat(early.code()).isEqualTo("INVALID_APPEAL_TARGET");   // not rejected yet
        flows.setStatus(env, aid, "REJECTED", "address proof invalid");
        assertThat(appeal(fx.citizenToken(fx.makeCitizen()), "APPLICATION_REJECTION", aid).code()).isEqualTo("APPEAL_NOT_ENTITLED");
        long appealId = appeal(h, "APPLICATION_REJECTION", aid).l("appeal_id");
        api.post("/api/v1/appeals/" + appealId + "/review", env.token);
        assertThat(decide(appealId, "uphold", "documents were valid").s("status")).isEqualTo("UPHELD");
        assertThat(api.get("/api/v1/applications/" + aid, env.token).s("current_status")).isEqualTo("UNDER_VERIFICATION");
        List<Map<String, Object>> hist = fx.jdbc.queryForList("SELECT previous_status, new_status, reason FROM application_status_history WHERE application_id = ? ORDER BY history_id", aid);
        assertThat(hist.get(hist.size() - 2).get("new_status")).isEqualTo("REJECTED");
        assertThat(hist.get(hist.size() - 1).get("previous_status")).isEqualTo("REJECTED");
        assertThat(hist.get(hist.size() - 1).get("new_status")).isEqualTo("UNDER_VERIFICATION");
        assertThat((String) hist.get(hist.size() - 1).get("reason")).contains("Appeal upheld");
        assertThat(flows.setStatus(env, aid, "SUBMITTED", null).status()).isEqualTo(409);   // reopening is the only exception
    }

    @Test
    void applicationRejectionAppealDismissedKeepsTheRejection() {
        JsonNode a = flows.newApp(env);
        long aid = a.get("application_id").asLong();
        flows.setStatus(env, aid, "REJECTED", "ineligible applicant");
        long appealId = appeal(fx.citizenToken(env.citizen), "APPLICATION_REJECTION", aid).l("appeal_id");
        api.post("/api/v1/appeals/" + appealId + "/review", env.token);
        assertThat(decide(appealId, "dismiss", null).s("status")).isEqualTo("DISMISSED");
        assertThat(api.get("/api/v1/applications/" + aid, env.token).s("current_status")).isEqualTo("REJECTED");
    }

    @Test
    void licenceSuspensionAppealReinstatesTheLicence() {
        LicenceClass cls = fx.makeLicenceClass();
        JsonNode app = flows.approvedApp(env);
        Resp dl = api.post("/api/v1/driving-licences", env.token, m("citizen_id", env.citizen.getCitizenId(), "application_id", app.get("application_id").asLong(),
                "office_id", env.office.getOfficeId(), "licence_class_ids", List.of(cls.getLicenceClassId())));
        long lid = dl.l("driving_licence_id");
        String h = fx.citizenToken(env.citizen);
        assertThat(appeal(h, "LICENCE_SUSPENSION", lid).code()).isEqualTo("INVALID_APPEAL_TARGET");   // still ACTIVE
        api.post("/api/v1/driving-licences/" + lid + "/suspend", env.token, m("reason", "Over-speeding"));
        assertThat(appeal(fx.citizenToken(fx.makeCitizen()), "LICENCE_SUSPENSION", lid).code()).isEqualTo("APPEAL_NOT_ENTITLED");
        long aid = appeal(h, "LICENCE_SUSPENSION", lid).l("appeal_id");
        api.post("/api/v1/appeals/" + aid + "/review", env.token);
        assertThat(decide(aid, "uphold", "suspension unjustified").s("status")).isEqualTo("UPHELD");
        assertThat(api.get("/api/v1/driving-licences/" + lid, env.token).s("current_status")).isEqualTo("ACTIVE");
        List<Map<String, Object>> hist = fx.jdbc.queryForList("SELECT new_status, reason FROM licence_status_history WHERE driving_licence_id = ? ORDER BY history_id", lid);
        assertThat(hist).extracting(x -> (String) x.get("new_status")).containsExactly("ACTIVE", "SUSPENDED", "ACTIVE");
        assertThat((String) hist.get(2).get("reason")).contains("Appeal upheld");
    }

    @Test
    void appealVisibilityAndListing() {
        Resp ch = challanCase();
        String mine = fx.citizenToken(env.citizen);
        Resp a = appeal(mine, "CHALLAN", ch.l("challan_id"));
        String other = fx.citizenToken(fx.makeCitizen());
        assertThat(api.get("/api/v1/appeals/" + a.l("appeal_id"), other).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/appeals/" + a.l("appeal_id"), mine).status()).isEqualTo(200);
        assertThat(api.get("/api/v1/appeals", other).l("total")).isEqualTo(0);
        Resp staff = api.get("/api/v1/appeals", env.token, m("status", "FILED", "against_type", "CHALLAN", "citizen_id", env.citizen.getCitizenId()));
        assertThat(staff.json().get("items")).hasSize(1);
        assertThat(staff.at("items/0/appeal_id").asLong()).isEqualTo(a.l("appeal_id"));
    }

    // ------------------------------- notifications -------------------------------

    @Test
    void notificationFailureNeverBreaksTheBusinessOperation() {
        provider.use((NotificationProvider) (channel, recipient, subject, message) -> {
            throw new RuntimeException("SMS gateway down");
        });
        JsonNode a = flows.newApp(env);   // the primary transaction must succeed even though delivery fails
        assertThat(a.get("current_status").asText()).isEqualTo("SUBMITTED");
        long personId = env.citizen.getPersonId();
        List<Map<String, Object>> rows = fx.jdbc.queryForList("SELECT status, sent_at FROM notifications WHERE person_id = ?", personId);
        assertThat(rows).isNotEmpty();
        rows.forEach(r -> {
            assertThat(r.get("status")).isEqualTo("FAILED");
            assertThat(r.get("sent_at")).isNull();
        });
    }

    @Test
    void eventsGenerateNotificationsWithProviderDelivery() {
        fx.jdbc.update("UPDATE persons SET email = ? WHERE person_id = ?", ("c" + Fx.u(6) + "@example.com").toLowerCase(), env.citizen.getPersonId());
        JsonNode a = flows.newApp(env);
        flows.setStatus(env, a.get("application_id").asLong(), "UNDER_VERIFICATION", null);
        List<Map<String, Object>> rows = fx.jdbc.queryForList("SELECT subject, channel, status, sent_at FROM notifications WHERE person_id = ? ORDER BY notification_id", env.citizen.getPersonId());
        List<String> subjects = rows.stream().map(r -> (String) r.get("subject")).toList();
        assertThat(subjects).contains("Application submitted");
        assertThat(subjects.stream().anyMatch(s -> s.contains("Under Verification"))).isTrue();
        assertThat(rows.stream().map(r -> (String) r.get("channel")).collect(java.util.stream.Collectors.toSet())).containsExactlyInAnyOrder("SMS", "EMAIL");
        rows.forEach(r -> {
            assertThat(r.get("status")).isEqualTo("SENT");
            assertThat(r.get("sent_at")).isNotNull();
        });
        assertThat(provider.mock.sent).hasSize(rows.size());
        Resp mine = api.get("/api/v1/notifications", fx.citizenToken(env.citizen));
        assertThat(mine.l("total")).isEqualTo(rows.size());
        mine.json().get("items").forEach(i -> assertThat(i.get("person_id").asLong()).isEqualTo(env.citizen.getPersonId()));
        assertThat(api.get("/api/v1/notifications", env.token, m("person_id", env.citizen.getPersonId(), "status", "SENT")).l("total")).isEqualTo(rows.size());
        assertThat(api.get("/api/v1/notifications", fx.withPermissions()).status()).isEqualTo(403);
    }

    @Test
    void expiryReminders() {
        LicenceClass cls = fx.makeLicenceClass();
        JsonNode app = flows.approvedApp(env);
        Resp soon = api.post("/api/v1/driving-licences", env.token, m("citizen_id", env.citizen.getCitizenId(), "application_id", app.get("application_id").asLong(),
                "office_id", env.office.getOfficeId(), "licence_class_ids", List.of(cls.getLicenceClassId()),
                "issue_date", Clock.today().minusDays(300).toString(), "expiry_date", Clock.today().plusDays(10).toString()));
        provider.reset();
        Resp r = api.post("/api/v1/notifications/expiry-reminders", env.token, null, m("days", 30));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.i("reminders_sent")).isGreaterThanOrEqualTo(1);
        assertThat(provider.mock.sent.stream().anyMatch(n -> n.get("message").contains("Driving licence " + soon.s("licence_number")))).isTrue();
    }

    // ------------------------------- audit -------------------------------

    @Test
    void auditLogIsReadOnlyAndFilterable() {
        JsonNode a = flows.newApp(env);
        Resp r = api.get("/api/v1/audit-logs", env.token, m("table_name", "applications", "record_id", a.get("application_id").asLong()));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.l("total")).isEqualTo(1);
        JsonNode entry = r.at("items/0");
        assertThat(entry.get("action").asText()).isEqualTo("INSERT");
        assertThat(entry.get("changed_by_user_id").asLong()).isEqualTo(env.user.getUserId());
        assertThat(entry.get("old_values").isNull()).isTrue();
        assertThat(api.get("/api/v1/audit-logs", env.token, m("action", "UPDATE", "user_id", env.user.getUserId())).status()).isEqualTo(200);
        long auditId = entry.get("audit_id").asLong();
        for (org.springframework.http.HttpMethod method : List.of(org.springframework.http.HttpMethod.POST, org.springframework.http.HttpMethod.PUT,
                org.springframework.http.HttpMethod.PATCH, org.springframework.http.HttpMethod.DELETE)) {
            assertThat(api.request(method, "/api/v1/audit-logs/" + auditId, env.token).status()).isIn(404, 405);
            assertThat(api.request(method, "/api/v1/audit-logs", env.token).status()).isEqualTo(405);
        }
        assertThat(api.get("/api/v1/audit-logs", fx.withPermissions("citizen.view")).status()).isEqualTo(403);
    }

    @Test
    void auditNeverContainsSecretsOrNationalIds() {
        String nid = "SECRETNID" + Fx.u(8);
        Resp r = api.post("/api/v1/citizens", env.token, m("first_name", "A", "last_name", "B", "date_of_birth", "1990-01-01", "gender", "MALE",
                "national_id_number", nid, "phone_primary", "9111111111"));
        assertThat(r.status()).isEqualTo(201);
        var person = fx.makePerson();
        Resp u2 = api.post("/api/v1/users", env.token, m("person_id", person.getPersonId(), "username", "aud_" + Fx.u(8), "password", "SuperSecret123"));
        assertThat(u2.status()).isEqualTo(201);
        api.patch("/api/v1/users/" + u2.l("user_id"), env.token, m("password", "AnotherSecret456"));
        String everything = String.join(" ", fx.jdbc.queryForList("SELECT CONCAT(IFNULL(CAST(old_values AS CHAR), ''), IFNULL(CAST(new_values AS CHAR), '')) FROM audit_logs", String.class));
        for (String forbidden : new String[]{nid, "SuperSecret123", "AnotherSecret456", "password_hash", "$2b$", "$2a$"}) {
            assertThat(everything).as(forbidden).doesNotContain(forbidden);
        }
    }

    // ------------------------------- dashboard -------------------------------

    private long n(String sql, Object... args) {
        return fx.count(sql, args);
    }

    @Test
    void dashboardMatchesTheDatabase() {
        JsonNode a = flows.approvedApp(env);
        LicenceClass cls = fx.makeLicenceClass();
        api.post("/api/v1/driving-licences", env.token, m("citizen_id", env.citizen.getCitizenId(), "application_id", a.get("application_id").asLong(),
                "office_id", env.office.getOfficeId(), "licence_class_ids", List.of(cls.getLicenceClassId()),
                "issue_date", Clock.today().minusDays(300).toString(), "expiry_date", Clock.today().plusDays(5).toString()));
        flows.newApp(env);
        Resp s = api.get("/api/v1/dashboard/summary", env.token);
        assertThat(s.status()).isEqualTo(200);
        assertThat(s.l("total_citizens")).isEqualTo(n("SELECT COUNT(*) FROM citizens"));
        assertThat(s.l("total_vehicles")).isEqualTo(n("SELECT COUNT(*) FROM vehicles"));
        assertThat(s.l("active_licences")).isEqualTo(n("SELECT COUNT(*) FROM driving_licences WHERE current_status = 'ACTIVE'"));
        long total = 0;
        for (Map<String, Object> row : fx.jdbc.queryForList("SELECT current_status AS st, COUNT(*) AS c FROM applications GROUP BY current_status")) {
            assertThat(s.at("applications_by_status/" + row.get("st")).asLong()).isEqualTo(((Number) row.get("c")).longValue());
            total += ((Number) row.get("c")).longValue();
        }
        long sum = 0;
        for (JsonNode v : s.json().get("applications_by_status")) sum += v.asLong();
        assertThat(sum).isEqualTo(total).isEqualTo(n("SELECT COUNT(*) FROM applications"));
        assertThat(s.l("pending_applications")).isEqualTo(n("SELECT COUNT(*) FROM applications WHERE current_status IN ('SUBMITTED','DOCS_PENDING','UNDER_VERIFICATION','APPOINTMENT_SCHEDULED','AWAITING_PAYMENT')"));
        assertThat(s.l("applications_under_verification")).isEqualTo(n("SELECT COUNT(*) FROM applications WHERE current_status = 'UNDER_VERIFICATION'"));
        assertThat(s.l("licences_expiring_soon")).isGreaterThanOrEqualTo(1);
        BigDecimal success = fx.jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE status = 'SUCCESS'", BigDecimal.class);
        assertThat(new BigDecimal(s.at("payments/by_status/SUCCESS/amount").asText())).isEqualByComparingTo(success);
        assertThat(new BigDecimal(s.at("payments/net_collected").asText())).isEqualByComparingTo(
                new BigDecimal(s.at("payments/gross_collected").asText()).subtract(new BigDecimal(s.at("payments/refunded").asText())));
        Resp narrow = api.get("/api/v1/dashboard/summary", env.token, m("days", 1));
        assertThat(narrow.l("licences_expiring_soon")).isLessThanOrEqualTo(s.l("licences_expiring_soon"));
        assertThat(narrow.i("expiry_window_days")).isEqualTo(1);
    }

    @Test
    void dashboardReflectsNewDataAndRequiresPermission() {
        Resp before = api.get("/api/v1/dashboard/summary", env.token);
        flows.newApp(env);
        Resp after = api.get("/api/v1/dashboard/summary", env.token);
        assertThat(after.at("applications_by_status/SUBMITTED").asLong()).isEqualTo(before.at("applications_by_status/SUBMITTED").asLong() + 1);
        assertThat(after.l("pending_applications")).isEqualTo(before.l("pending_applications") + 1);
        assertThat(api.get("/api/v1/dashboard/summary", fx.withPermissions("citizen.view")).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/dashboard/summary", null).status()).isEqualTo(401);
        assertThat(new ArrayList<>(List.of(1))).isNotEmpty();
    }
}
