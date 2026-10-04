package com.rto.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.core.Clock;
import com.rto.domain.Citizen;
import com.rto.domain.Employee;
import com.rto.domain.LearnerLicence;
import com.rto.domain.LicenceClass;
import com.rto.support.Api.Resp;
import com.rto.support.BaseIT;
import com.rto.support.Flows.Env;
import com.rto.support.Fx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.rto.support.Api.m;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LicenceTests extends BaseIT {
    Env env;

    @BeforeEach
    void setup() {
        env = flows.makeEnv();
    }

    private long id(JsonNode n) {
        return n.get("application_id").asLong();
    }

    private Resp learner(JsonNode app, Object... over) {
        return api.post("/api/v1/learner-licences", env.token, flows.put(m("citizen_id", env.citizen.getCitizenId(), "application_id", id(app), "office_id", env.office.getOfficeId()), over));
    }

    private Resp licence(JsonNode app, List<Long> classIds, Object... over) {
        return api.post("/api/v1/driving-licences", env.token, flows.put(m("citizen_id", env.citizen.getCitizenId(), "application_id", id(app),
                "office_id", env.office.getOfficeId(), "licence_class_ids", classIds), over));
    }

    // ------------------------------- learner -------------------------------

    @Test
    void learnerLicenceIssueCompletesApplicationAndDefaultsDates() {
        JsonNode a = flows.approvedApp(env);
        Resp r = learner(a);
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
        assertThat(r.s("status")).isEqualTo("ACTIVE");
        assertThat(r.s("licence_number")).startsWith("LL-");
        assertThat(r.s("expiry_date").compareTo(r.s("issue_date"))).isGreaterThan(0);
        assertThat(api.get("/api/v1/applications/" + id(a), env.token).s("current_status")).isEqualTo("COMPLETED");
        assertThat(fx.count("SELECT COUNT(*) FROM audit_logs WHERE table_name = 'learner_licences' AND record_id = ?", r.l("learner_licence_id"))).isGreaterThan(0);
    }

    @Test
    void learnerRequiresApprovedApplicationOfTheSameCitizen() {
        Resp r = learner(flows.verifiedApp(env));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("APPLICATION_NOT_APPROVED");
        JsonNode approved = flows.approvedApp(env);
        Citizen stranger = fx.makeCitizen();
        Resp mismatch = api.post("/api/v1/learner-licences", env.token, m("citizen_id", stranger.getCitizenId(), "application_id", id(approved), "office_id", env.office.getOfficeId()));
        assertThat(mismatch.status()).isEqualTo(409);
        assertThat(mismatch.code()).isEqualTo("APPLICATION_CITIZEN_MISMATCH");
        Resp inactive = learner(approved, "office_id", fx.makeOffice(false).getOfficeId());
        assertThat(inactive.status()).isEqualTo(409);
        assertThat(inactive.code()).isEqualTo("OFFICE_INACTIVE");
    }

    @Test
    void expiryMustFollowIssue() {
        JsonNode a = flows.approvedApp(env);
        assertThat(learner(a, "issue_date", "2025-01-10", "expiry_date", "2025-01-10").status()).isEqualTo(422);
        assertThat(learner(a, "issue_date", "2025-01-10", "expiry_date", "2025-01-01").status()).isEqualTo(422);
    }

    @Test
    void onlyOneActiveLearnerPerCitizen() {
        JsonNode a1 = flows.approvedApp(env);
        assertThat(learner(a1).status()).isEqualTo(201);
        JsonNode a2 = flows.approvedApp(env);
        Resp r = learner(a2);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("ACTIVE_LEARNER_EXISTS");
        // the database guarantees it independently of the service check (generated column + UNIQUE)
        LearnerLicence dup = new LearnerLicence();
        dup.setLicenceNumber("X" + Fx.u(10));
        dup.setCitizenId(env.citizen.getCitizenId());
        dup.setApplicationId(id(a2));
        dup.setOfficeId(env.office.getOfficeId());
        dup.setIssueDate(Clock.today());
        dup.setExpiryDate(Clock.today().plusDays(30));
        dup.setStatus("ACTIVE");
        assertThatThrownBy(() -> fx.save(dup)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void cancelledLearnerAllowsANewOneAndALapsedOneIsAutoClosed() {
        JsonNode a1 = flows.approvedApp(env);
        Resp first = learner(a1);
        Resp cancel = api.post("/api/v1/learner-licences/" + first.l("learner_licence_id") + "/cancel", env.token, m("reason", "lost"));
        assertThat(cancel.status()).isEqualTo(200);
        assertThat(cancel.s("status")).isEqualTo("CANCELLED");
        Resp second = learner(flows.approvedApp(env), "issue_date", Clock.today().minusDays(400).toString(), "expiry_date", Clock.today().minusDays(100).toString());
        assertThat(second.status()).isEqualTo(201);
        Resp third = learner(flows.approvedApp(env));   // the lapsed (never swept) ACTIVE row is closed automatically
        assertThat(third.status()).isEqualTo(201);
        assertThat(fx.jdbc.queryForObject("SELECT status FROM learner_licences WHERE learner_licence_id = ?", String.class, second.l("learner_licence_id"))).isEqualTo("EXPIRED");
    }

    // ------------------------------- driving licence -------------------------------

    @Test
    void drivingLicenceWithMultipleClassesHistoryAndLearnerConversion() {
        Resp ll = learner(flows.approvedApp(env));
        LicenceClass c1 = fx.makeLicenceClass(), c2 = fx.makeLicenceClass();
        JsonNode a = flows.approvedApp(env);
        Resp r = licence(a, List.of(c1.getLicenceClassId(), c2.getLicenceClassId()), "learner_licence_id", ll.l("learner_licence_id"));
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
        List<Long> classes = new ArrayList<>();
        r.json().get("classes").forEach(c -> classes.add(c.get("licence_class_id").asLong()));
        assertThat(classes).containsExactlyInAnyOrder(c1.getLicenceClassId(), c2.getLicenceClassId());
        assertThat(r.s("current_status")).isEqualTo("ACTIVE");
        assertThat(api.get("/api/v1/learner-licences/" + ll.l("learner_licence_id"), env.token).s("status")).isEqualTo("CONVERTED");
        JsonNode hist = api.get("/api/v1/driving-licences/" + r.l("driving_licence_id") + "/history", env.token).json();
        assertThat(hist).hasSize(1);
        assertThat(hist.get(0).get("previous_status").isNull()).isTrue();
        assertThat(hist.get(0).get("new_status").asText()).isEqualTo("ACTIVE");
        assertThat(api.get("/api/v1/applications/" + id(a), env.token).s("current_status")).isEqualTo("COMPLETED");
        LicenceClass c3 = fx.makeLicenceClass();
        Resp added = api.post("/api/v1/driving-licences/" + r.l("driving_licence_id") + "/classes", env.token, m("licence_class_id", c3.getLicenceClassId()));
        assertThat(added.status()).isEqualTo(201);
        assertThat(added.size("classes")).isEqualTo(3);
        assertThat(api.post("/api/v1/driving-licences/" + r.l("driving_licence_id") + "/classes", env.token, m("licence_class_id", c3.getLicenceClassId())).status()).isEqualTo(409);
    }

    @Test
    void drivingLicenceValidation() {
        LicenceClass c = fx.makeLicenceClass();
        JsonNode a = flows.approvedApp(env);
        assertThat(licence(a, List.of()).status()).isEqualTo(422);
        assertThat(licence(a, List.of(99999999L)).status()).isEqualTo(400);
        assertThat(licence(a, List.of(c.getLicenceClassId()), "issue_date", "2025-01-10", "expiry_date", "2024-01-10").status()).isEqualTo(422);
        Citizen stranger = fx.makeCitizen();
        JsonNode sa = flows.approvedApp(env.with(stranger));
        Resp ll = api.post("/api/v1/learner-licences", env.token, m("citizen_id", stranger.getCitizenId(), "application_id", id(sa), "office_id", env.office.getOfficeId()));
        Resp mismatch = licence(a, List.of(c.getLicenceClassId()), "learner_licence_id", ll.l("learner_licence_id"));
        assertThat(mismatch.status()).isEqualTo(409);
        assertThat(mismatch.code()).isEqualTo("LEARNER_CITIZEN_MISMATCH");
        Resp ok = licence(a, List.of(c.getLicenceClassId()), "licence_number", "DL" + Fx.u(8));
        assertThat(ok.status()).isEqualTo(201);
        JsonNode a2 = flows.approvedApp(env);
        Resp dup = licence(a2, List.of(c.getLicenceClassId()), "licence_number", ok.s("licence_number"));
        assertThat(dup.status()).isEqualTo(409);
        assertThat(dup.code()).isEqualTo("DUPLICATE");
    }

    @Test
    void suspendAndRevokeRecordHistoryUserReasonAndAudit() {
        LicenceClass c = fx.makeLicenceClass();
        Resp dl = licence(flows.approvedApp(env), List.of(c.getLicenceClassId()));
        long lid = dl.l("driving_licence_id");
        String base = "/api/v1/driving-licences/" + lid;
        assertThat(api.post(base + "/suspend", env.token, m("reason", "x")).status()).isEqualTo(422);
        Resp r = api.post(base + "/suspend", env.token, m("reason", "Drunk driving"));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.s("current_status")).isEqualTo("SUSPENDED");
        assertThat(api.post(base + "/suspend", env.token, m("reason", "again")).status()).isEqualTo(409);
        Resp rev = api.post(base + "/revoke", env.token, m("reason", "Repeat offence"));
        assertThat(rev.status()).isEqualTo(200);
        assertThat(rev.s("current_status")).isEqualTo("REVOKED");
        for (String action : new String[]{"reinstate", "suspend", "revoke"}) {
            assertThat(api.post(base + "/" + action, env.token, m("reason", "nope nope")).status()).as(action).isEqualTo(409);
        }
        List<Map<String, Object>> hist = fx.jdbc.queryForList("SELECT previous_status, new_status, reason, changed_by_user_id FROM licence_status_history WHERE driving_licence_id = ? ORDER BY history_id", lid);
        assertThat(hist).extracting(h -> h.get("previous_status") + ">" + h.get("new_status") + ":" + h.get("reason")).containsExactly(
                "null>ACTIVE:Licence issued", "ACTIVE>SUSPENDED:Drunk driving", "SUSPENDED>REVOKED:Repeat offence");
        hist.forEach(h -> assertThat(((Number) h.get("changed_by_user_id")).longValue()).isEqualTo(env.user.getUserId()));
        assertThat(fx.count("SELECT COUNT(*) FROM audit_logs WHERE table_name = 'driving_licences' AND record_id = ? AND action = 'UPDATE'", lid)).isEqualTo(2);
    }

    @Test
    void reinstateRenewAndExpirySweep() {
        LicenceClass c = fx.makeLicenceClass();
        Resp dl = licence(flows.approvedApp(env), List.of(c.getLicenceClassId()));
        long lid = dl.l("driving_licence_id");
        String base = "/api/v1/driving-licences/" + lid;
        api.post(base + "/suspend", env.token, m("reason", "pending review"));
        assertThat(api.post(base + "/reinstate", env.token, m("reason", "review done")).s("current_status")).isEqualTo("ACTIVE");
        LocalDate far = Clock.today().plusDays(9000);
        Resp ext = api.post(base + "/renew", env.token, m("new_expiry_date", far.toString()));
        assertThat(ext.status()).isEqualTo(200);
        assertThat(ext.s("expiry_date")).isEqualTo(far.toString());
        assertThat(api.post(base + "/renew", env.token, m("new_expiry_date", Clock.today().plusDays(100).toString())).status()).isEqualTo(400);
        Resp old = licence(flows.approvedApp(env), List.of(c.getLicenceClassId()), "issue_date", Clock.today().minusDays(400).toString(), "expiry_date", Clock.today().minusDays(5).toString());
        Resp sweep = api.post("/api/v1/driving-licences/expire-overdue", env.token);
        assertThat(sweep.status()).isEqualTo(200);
        assertThat(sweep.i("driving_licences_expired")).isGreaterThanOrEqualTo(1);
        assertThat(api.get("/api/v1/driving-licences/" + old.l("driving_licence_id"), env.token).s("current_status")).isEqualTo("EXPIRED");
        Resp back = api.post("/api/v1/driving-licences/" + old.l("driving_licence_id") + "/renew", env.token, m("new_expiry_date", Clock.today().plusDays(365).toString()));
        assertThat(back.status()).isEqualTo(200);
        assertThat(back.s("current_status")).isEqualTo("ACTIVE");
        List<String> statuses = new ArrayList<>();
        api.get("/api/v1/driving-licences/" + old.l("driving_licence_id") + "/history", env.token).json().forEach(h -> statuses.add(h.get("new_status").asText()));
        assertThat(statuses).containsExactly("ACTIVE", "EXPIRED", "ACTIVE");
    }

    @Test
    void licenceListingFiltersAndPermissions() {
        LicenceClass c = fx.makeLicenceClass();
        Resp dl = licence(flows.approvedApp(env), List.of(c.getLicenceClassId()));
        Resp r = api.get("/api/v1/driving-licences", env.token, m("citizen_id", env.citizen.getCitizenId(), "class_id", c.getLicenceClassId(), "status", "ACTIVE"));
        assertThat(r.json().get("items")).hasSize(1);
        assertThat(r.at("items/0/driving_licence_id").asLong()).isEqualTo(dl.l("driving_licence_id"));
        assertThat(api.get("/api/v1/driving-licences", env.token, m("search", dl.s("licence_number"))).l("total")).isEqualTo(1);
        assertThat(api.get("/api/v1/driving-licences", env.token, m("expiring_within_days", 30, "citizen_id", env.citizen.getCitizenId())).l("total")).isEqualTo(0);
        String nobody = fx.withPermissions("licence.view");
        assertThat(api.post("/api/v1/driving-licences/" + dl.l("driving_licence_id") + "/suspend", nobody, m("reason", "no rights")).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/driving-licences/" + dl.l("driving_licence_id"), fx.citizenToken(env.citizen)).status()).isEqualTo(200);
        assertThat(api.get("/api/v1/driving-licences/" + dl.l("driving_licence_id"), fx.citizenToken(fx.makeCitizen())).status()).isEqualTo(403);
    }

    // ------------------------------- driving tests & schools -------------------------------

    private Resp centre() {
        return api.post("/api/v1/test-centres", env.token, m("office_id", env.office.getOfficeId(), "centre_name", "Centre " + Fx.u(4)));
    }

    private Map<String, Object> testBody(JsonNode app, Resp centre, int days) {
        return m("application_id", id(app), "citizen_id", env.citizen.getCitizenId(), "test_centre_id", centre.l("test_centre_id"),
                "examiner_employee_id", env.emp.getEmployeeId(), "scheduled_at", flows.futureIso(days));
    }

    @Test
    void drivingTestLifecycleWithRetake() {
        Resp centre = centre();
        JsonNode a = flows.verifiedApp(env);
        Map<String, Object> body = testBody(a, centre, 3);
        Resp t1 = api.post("/api/v1/driving-tests", env.token, body);
        assertThat(t1.status()).isEqualTo(201);
        assertThat(t1.s("result")).isEqualTo("PENDING");
        assertThat(api.post("/api/v1/driving-tests", env.token, body).status()).isEqualTo(409);   // one pending at a time
        Resp res = api.patch("/api/v1/driving-tests/" + t1.l("test_id") + "/result", env.token, m("result", "FAIL", "remarks", "missed signal"));
        assertThat(res.status()).isEqualTo(200);
        assertThat(res.s("result")).isEqualTo("FAIL");
        assertThat(api.patch("/api/v1/driving-tests/" + t1.l("test_id") + "/result", env.token, m("result", "PASS")).status()).isEqualTo(409);
        Resp t2 = api.post("/api/v1/driving-tests", env.token, flows.put(body, "scheduled_at", flows.futureIso(10)));
        assertThat(t2.status()).isEqualTo(201);
        assertThat(t2.l("test_id")).isNotEqualTo(t1.l("test_id"));   // retake = a new row
        api.patch("/api/v1/driving-tests/" + t2.l("test_id") + "/result", env.token, m("result", "PASS"));
        assertThat(api.post("/api/v1/driving-tests", env.token, flows.put(body, "scheduled_at", flows.futureIso(20))).status()).isEqualTo(409);   // already passed
        assertThat(api.get("/api/v1/driving-tests", env.token, m("application_id", id(a))).l("total")).isEqualTo(2);
    }

    @Test
    void licenceRequiresAPassedTestWhenTestsExist() {
        Resp centre = centre();
        LicenceClass c = fx.makeLicenceClass();
        JsonNode a = flows.approvedApp(env);
        Resp t = api.post("/api/v1/driving-tests", env.token, testBody(a, centre, 1));
        assertThat(t.status()).isEqualTo(201);   // closed applications cannot get new tests, APPROVED ones can
        api.patch("/api/v1/driving-tests/" + t.l("test_id") + "/result", env.token, m("result", "ABSENT"));
        Resp r = licence(a, List.of(c.getLicenceClassId()));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("TEST_NOT_PASSED");
        Resp t2 = api.post("/api/v1/driving-tests", env.token, testBody(a, centre, 5));
        api.patch("/api/v1/driving-tests/" + t2.l("test_id") + "/result", env.token, m("result", "PASS"));
        assertThat(licence(a, List.of(c.getLicenceClassId())).status()).isEqualTo(201);
    }

    @Test
    void drivingTestValidations() {
        Resp centre = centre();
        JsonNode a = flows.newApp(env);
        Map<String, Object> base = testBody(a, centre, 1);
        assertThat(api.post("/api/v1/driving-tests", env.token, flows.put(base, "scheduled_at", "2001-01-01T10:00:00")).status()).isEqualTo(409);
        assertThat(api.post("/api/v1/driving-tests", env.token, flows.put(base, "test_centre_id", 99999999)).status()).isEqualTo(404);
        assertThat(api.post("/api/v1/driving-tests", env.token, flows.put(base, "examiner_employee_id", 99999999)).status()).isEqualTo(404);
        assertThat(api.post("/api/v1/driving-tests", env.token, flows.put(base, "citizen_id", fx.makeCitizen().getCitizenId())).status()).isEqualTo(409);
        Employee inactive = fx.makeEmployee(env.office);
        fx.jdbc.update("UPDATE employees SET is_active = 0 WHERE employee_id = ?", inactive.getEmployeeId());
        assertThat(api.post("/api/v1/driving-tests", env.token, flows.put(base, "examiner_employee_id", inactive.getEmployeeId())).status()).isEqualTo(409);
        assertThat(api.post("/api/v1/driving-tests", env.token, base).status()).isEqualTo(201);
    }

    @Test
    void schoolsAndInstructors() {
        LicenceClass c = fx.makeLicenceClass();
        Resp s = api.post("/api/v1/driving-schools", env.token, m("school_name", "Safe Drive", "licence_number", "DS" + Fx.u(8), "office_id", env.office.getOfficeId()));
        assertThat(s.status()).isEqualTo(201);
        assertThat(api.post("/api/v1/driving-schools", env.token, m("school_name", "x", "licence_number", s.s("licence_number"), "office_id", env.office.getOfficeId())).status()).isEqualTo(409);
        Map<String, Object> person = m("first_name", "Ila", "last_name", "Rao", "date_of_birth", "1980-01-01", "gender", "FEMALE", "national_id_number", "INS" + Fx.u(10), "phone_primary", "9000000001");
        String url = "/api/v1/driving-schools/" + s.l("school_id") + "/instructors";
        Resp ins = api.post(url, env.token, m("person", person, "licence_class_id", c.getLicenceClassId()));
        assertThat(ins.status()).isEqualTo(201);
        assertThat(ins.s("first_name")).isEqualTo("Ila");
        assertThat(api.post(url, env.token, m("person_id", ins.l("person_id"), "licence_class_id", c.getLicenceClassId())).status()).isEqualTo(409);
        assertThat(api.get(url, env.token).json()).hasSize(1);
        assertThat(api.get("/api/v1/driving-schools", env.token, m("search", "Safe")).l("total")).isGreaterThanOrEqualTo(1);
    }
}
