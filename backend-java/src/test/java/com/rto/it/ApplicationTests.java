package com.rto.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.core.ApiException;
import com.rto.core.CurrentUser;
import com.rto.domain.Citizen;
import com.rto.domain.Employee;
import com.rto.domain.RtoOffice;
import com.rto.service.ApplicationService;
import com.rto.service.UserLoader;
import com.rto.support.Api.Resp;
import com.rto.support.BaseIT;
import com.rto.support.Flows.Env;
import com.rto.support.Fx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static com.rto.support.Api.m;
import static org.assertj.core.api.Assertions.assertThat;

class ApplicationTests extends BaseIT {
    @Autowired ApplicationService applications;
    @Autowired UserLoader loader;

    Env env;

    @BeforeEach
    void setup() {
        env = flows.makeEnv();
    }

    private long id(JsonNode app) {
        return app.get("application_id").asLong();
    }

    // ---------------- creation ----------------

    @Test
    void createApplicationWritesHistoryAuditAndGeneratesNumber() {
        JsonNode a = flows.newApp(env);
        assertThat(a.get("current_status").asText()).isEqualTo("SUBMITTED");
        assertThat(a.get("application_number").asText()).startsWith("APP-");
        List<String> allowed = new ArrayList<>();
        a.get("allowed_transitions").forEach(t -> allowed.add(t.asText()));
        assertThat(allowed).containsExactly("CANCELLED", "DOCS_PENDING", "REJECTED", "UNDER_VERIFICATION");
        List<Map<String, Object>> hist = fx.jdbc.queryForList("SELECT previous_status, new_status, changed_by_user_id FROM application_status_history WHERE application_id = ?", id(a));
        assertThat(hist).hasSize(1);
        assertThat(hist.get(0).get("previous_status")).isNull();
        assertThat(hist.get(0).get("new_status")).isEqualTo("SUBMITTED");
        assertThat(((Number) hist.get(0).get("changed_by_user_id")).longValue()).isEqualTo(env.user.getUserId());
        assertThat(fx.count("SELECT COUNT(*) FROM audit_logs WHERE table_name = 'applications' AND record_id = ? AND action = 'INSERT'", id(a))).isEqualTo(1);
        JsonNode b = flows.newApp(env);   // same citizen again re-uses the applicant row
        assertThat(b.get("citizen_id").asLong()).isEqualTo(a.get("citizen_id").asLong());
        assertThat(b.get("application_number").asText()).isNotEqualTo(a.get("application_number").asText());
        assertThat(fx.count("SELECT COUNT(*) FROM applicants WHERE citizen_id = ?", env.citizen.getCitizenId())).isEqualTo(1);
    }

    @Test
    void createValidations() {
        Map<String, Object> base = m("citizen_id", env.citizen.getCitizenId(), "service_type_id", env.service.getServiceTypeId(), "office_id", env.office.getOfficeId());
        java.util.function.Function<Map<String, Object>, Resp> post = over -> api.post("/api/v1/applications", env.token, flows.put(base, over.entrySet().stream().flatMap(e -> java.util.stream.Stream.of(e.getKey(), e.getValue())).toArray()));
        assertThat(post.apply(m("service_type_id", 99999999)).status()).isEqualTo(404);
        assertThat(post.apply(m("citizen_id", 99999999)).status()).isEqualTo(404);
        Resp inactive = post.apply(m("office_id", fx.makeOffice(false).getOfficeId()));
        assertThat(inactive.status()).isEqualTo(409);
        assertThat(inactive.code()).isEqualTo("OFFICE_INACTIVE");
        assertThat(api.post("/api/v1/applications", env.token, m("service_type_id", env.service.getServiceTypeId(), "office_id", env.office.getOfficeId())).status()).isEqualTo(400);
        String num = "CUSTOM-" + Fx.u(6);
        assertThat(post.apply(m("application_number", num)).status()).isEqualTo(201);
        assertThat(post.apply(m("application_number", num)).status()).isEqualTo(409);
        fx.jdbc.update("UPDATE citizens SET blacklisted = 1 WHERE citizen_id = ?", env.citizen.getCitizenId());
        Resp bl = post.apply(m());
        assertThat(bl.status()).isEqualTo(409);
        assertThat(bl.code()).isEqualTo("CITIZEN_BLACKLISTED");
    }

    @Test
    void citizenCanApplyOnlyForSelfAndSeesOnlyOwn() {
        Citizen mine = fx.makeCitizen(), other = fx.makeCitizen();
        String h = fx.citizenToken(mine);
        Map<String, Object> body = m("service_type_id", env.service.getServiceTypeId(), "office_id", env.office.getOfficeId());
        Resp r = api.post("/api/v1/applications", h, body);
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.l("citizen_id")).isEqualTo(mine.getCitizenId());
        assertThat(api.post("/api/v1/applications", h, flows.put(body, "citizen_id", other.getCitizenId())).status()).isEqualTo(403);
        JsonNode theirs = flows.newApp(env.with(other));
        assertThat(api.get("/api/v1/applications/" + id(theirs), h).status()).isEqualTo(403);
        Resp mineList = api.get("/api/v1/applications", h);
        assertThat(mineList.l("total")).isEqualTo(1);
        assertThat(mineList.at("items/0/citizen_id").asLong()).isEqualTo(mine.getCitizenId());
        assertThat(api.get("/api/v1/applications/" + r.l("application_id") + "/history", h).status()).isEqualTo(200);
        assertThat(api.get("/api/v1/applications/" + id(theirs) + "/history", h).status()).isEqualTo(403);
    }

    @Test
    void listFiltersSearchAndSorting() {
        JsonNode a = flows.newApp(env);
        Resp r = api.get("/api/v1/applications", env.token, m("search", a.get("application_number").asText()));
        assertThat(r.json().get("items")).hasSize(1);
        assertThat(r.at("items/0/application_id").asLong()).isEqualTo(id(a));
        Resp f = api.get("/api/v1/applications", env.token, m("status", "SUBMITTED", "office_id", env.office.getOfficeId(), "citizen_id", env.citizen.getCitizenId()));
        List<Long> ids = new ArrayList<>();
        f.json().get("items").forEach(i -> ids.add(i.get("application_id").asLong()));
        assertThat(ids).contains(id(a));
        Resp s = api.get("/api/v1/applications", env.token, m("sort", "submitted_at", "order", "desc", "page_size", 2));
        assertThat(s.json().get("items").size()).isLessThanOrEqualTo(2);
        assertThat(s.i("page_size")).isEqualTo(2);
        assertThat(api.get("/api/v1/applications", env.token, m("sort", "evil")).status()).isEqualTo(400);
    }

    @Test
    void patchRemarksAndClosedApplicationIsImmutable() {
        JsonNode a = flows.newApp(env);
        Resp r = api.patch("/api/v1/applications/" + id(a), env.token, m("remarks", "urgent"));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.s("remarks")).isEqualTo("urgent");
        flows.setStatus(env, id(a), "CANCELLED", "changed mind");
        assertThat(api.patch("/api/v1/applications/" + id(a), env.token, m("remarks", "x")).status()).isEqualTo(409);
    }

    // ---------------- assignment ----------------

    @Test
    void assignOfficerRules() {
        JsonNode a = flows.newApp(env);
        String url = "/api/v1/applications/" + id(a) + "/assign";
        Resp r = api.post(url, env.token, m("officer_id", env.emp.getEmployeeId()));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.l("assigned_officer_id")).isEqualTo(env.emp.getEmployeeId());
        Employee elsewhere = fx.makeEmployee(fx.makeOffice());
        Resp wrong = api.post(url, env.token, m("officer_id", elsewhere.getEmployeeId()));
        assertThat(wrong.status()).isEqualTo(409);
        assertThat(wrong.code()).isEqualTo("OFFICER_NOT_AT_OFFICE");
        assertThat(api.post(url, env.token, m("officer_id", 99999999)).status()).isEqualTo(404);
        Employee inactive = fx.makeEmployee(env.office);
        fx.jdbc.update("UPDATE employees SET is_active = 0 WHERE employee_id = ?", inactive.getEmployeeId());
        assertThat(api.post(url, env.token, m("officer_id", inactive.getEmployeeId())).status()).isEqualTo(409);
        String low = fx.withPermissions("application.view");
        assertThat(api.post(url, low, m("officer_id", env.emp.getEmployeeId())).status()).isEqualTo(403);
        assertThat(api.post(url, env.token, m("officer_id", null)).isNull("assigned_officer_id")).isTrue();
    }

    // ---------------- status workflow ----------------

    @Test
    void fullHappyPathRecordsEveryStep() {
        JsonNode a = flows.verifiedApp(env);
        long aid = id(a);
        Resp r = null;
        for (String next : new String[]{"AWAITING_PAYMENT", "APPROVED", "COMPLETED"}) {
            r = flows.setStatus(env, aid, next, null);
            assertThat(r.status()).as(next + " " + r).isEqualTo(200);
            assertThat(r.s("current_status")).isEqualTo(next);
        }
        assertThat(r.isNull("completed_at")).isFalse();
        JsonNode hist = api.get("/api/v1/applications/" + aid + "/history", env.token).json();
        List<String> statuses = new ArrayList<>();
        hist.forEach(h -> statuses.add(h.get("new_status").asText()));
        assertThat(statuses).containsExactly("SUBMITTED", "UNDER_VERIFICATION", "AWAITING_PAYMENT", "APPROVED", "COMPLETED");
        assertThat(hist.get(2).get("previous_status").asText()).isEqualTo("UNDER_VERIFICATION");
        hist.forEach(h -> assertThat(h.get("changed_by_user_id").isNull()).isFalse());
        assertThat(fx.count("SELECT COUNT(*) FROM audit_logs WHERE table_name = 'applications' AND record_id = ? AND action = 'UPDATE'", aid)).isEqualTo(4);
    }

    @ParameterizedTest
    @CsvSource({"CANCELLED,APPROVED", "REJECTED,UNDER_VERIFICATION", "CANCELLED,SUBMITTED"})
    void invalidTransitionsAre409AndChangeNothing(String firstMove, String bad) {
        JsonNode a = flows.newApp(env);
        assertThat(flows.setStatus(env, id(a), firstMove, "because reasons").status()).isEqualTo(200);
        Resp r = flows.setStatus(env, id(a), bad, "x");
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("INVALID_TRANSITION");
        assertThat(api.get("/api/v1/applications/" + id(a), env.token).s("current_status")).isEqualTo(firstMove);
    }

    @Test
    void cannotSkipAheadOrGoBackFromCompleted() {
        JsonNode a = flows.verifiedApp(env);
        long aid = id(a);
        assertThat(flows.setStatus(env, aid, "APPROVED", null).status()).isEqualTo(409);   // must pass AWAITING_PAYMENT
        assertThat(flows.setStatus(env, aid, "COMPLETED", null).status()).isEqualTo(409);
        for (String next : new String[]{"AWAITING_PAYMENT", "APPROVED", "COMPLETED"}) flows.setStatus(env, aid, next, null);
        assertThat(flows.setStatus(env, aid, "SUBMITTED", null).status()).isEqualTo(409);
        assertThat(flows.setStatus(env, aid, "CANCELLED", "late").status()).isEqualTo(409);
        assertThat(api.post("/api/v1/applications/" + aid + "/status", env.token, m("status", "NOPE")).status()).isEqualTo(422);
    }

    @Test
    void reasonRequiredForRejectCancelAndDocsPending() {
        JsonNode a = flows.newApp(env);
        for (String to : new String[]{"REJECTED", "CANCELLED", "DOCS_PENDING"}) {
            Resp r = flows.setStatus(env, id(a), to, null);
            assertThat(r.status()).as(to).isEqualTo(400);
            assertThat(r.code()).isEqualTo("REASON_REQUIRED");
        }
        assertThat(flows.setStatus(env, id(a), "DOCS_PENDING", "need address proof").status()).isEqualTo(200);
        JsonNode h = api.get("/api/v1/applications/" + id(a) + "/history", env.token).json();
        assertThat(h.get(h.size() - 1).get("reason").asText()).isEqualTo("need address proof");
    }

    @Test
    void targetStatusPermissionsAreEnforced() {
        JsonNode a = flows.verifiedApp(env);
        long aid = id(a);
        String verifier = fx.withPermissions("application.verify", "application.view");
        assertThat(flows.setStatus(verifier, aid, "AWAITING_PAYMENT", null).status()).isEqualTo(200);
        Resp r = flows.setStatus(verifier, aid, "APPROVED", null);
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.s("detail")).contains("application.approve");
        assertThat(flows.setStatus(verifier, aid, "REJECTED", "no").status()).isEqualTo(403);
        String nobody = fx.withPermissions("application.view");
        assertThat(flows.setStatus(nobody, aid, "CANCELLED", "x").status()).isEqualTo(403);
    }

    @Test
    void citizenMayCancelOwnApplicationOnly() {
        Citizen mine = fx.makeCitizen(), other = fx.makeCitizen();
        String h = fx.citizenToken(mine);
        JsonNode own = flows.newApp(env.with(mine));
        JsonNode theirs = flows.newApp(env.with(other));
        assertThat(flows.setStatus(h, id(theirs), "CANCELLED", "x").status()).isEqualTo(403);
        assertThat(flows.setStatus(h, id(own), "UNDER_VERIFICATION", null).status()).isEqualTo(403);
        Resp r = flows.setStatus(h, id(own), "CANCELLED", "no longer needed");
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.s("current_status")).isEqualTo("CANCELLED");
    }

    @Test
    void verificationRequiresVerifiedDocuments() {
        JsonNode a = flows.newApp(env);
        long aid = id(a);
        flows.setStatus(env, aid, "UNDER_VERIFICATION", null);
        Resp r = flows.setStatus(env, aid, "AWAITING_PAYMENT", null);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("DOCUMENTS_NOT_VERIFIED");   // no documents at all
        Resp d = flows.upload(env, aid);
        assertThat(flows.setStatus(env, aid, "AWAITING_PAYMENT", null).status()).isEqualTo(409);   // PENDING doc
        api.patch("/api/v1/documents/" + d.l("document_id") + "/reject", env.token, m("reason", "blurry scan"));
        assertThat(flows.setStatus(env, aid, "AWAITING_PAYMENT", null).status()).isEqualTo(409);   // REJECTED doc
        Resp d2 = flows.upload(env, aid);   // a re-upload supersedes the rejected one
        api.patch("/api/v1/documents/" + d2.l("document_id") + "/verify", env.token);
        assertThat(flows.setStatus(env, aid, "AWAITING_PAYMENT", null).status()).isEqualTo(200);
    }

    @Test
    void approvalRequiresPaymentWhenFeeDue() {
        var paid = fx.makeServiceType("500.00", 7);
        JsonNode a = flows.verifiedApp(env, m("service_type_id", paid.getServiceTypeId()));
        assertThat(a.get("fee_due").asText()).isEqualTo("500.00");
        flows.setStatus(env, id(a), "AWAITING_PAYMENT", null);
        Resp r = flows.setStatus(env, id(a), "APPROVED", null);
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("PAYMENT_REQUIRED");
    }

    @Test
    void concurrentStatusChangesOnlyOneWins() throws Exception {
        JsonNode a = flows.newApp(env);
        List<String> results = Collections.synchronizedList(new ArrayList<>());
        String[] targets = {"REJECTED", "CANCELLED", "REJECTED", "CANCELLED"};
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (String target : targets) {
            Thread t = new Thread(() -> {
                try {
                    CurrentUser user = loader.load(env.user.getUserId());
                    start.await();
                    applications.changeStatus(id(a), target, "race", user);
                    results.add("ok");
                } catch (ApiException e) {
                    results.add(e.code());
                } catch (Exception e) {
                    results.add("ERR " + e);
                }
            });
            threads.add(t);
            t.start();
        }
        start.countDown();
        for (Thread t : threads) t.join();
        assertThat(results).containsExactlyInAnyOrder("ok", "INVALID_TRANSITION", "INVALID_TRANSITION", "INVALID_TRANSITION");
        assertThat(fx.count("SELECT COUNT(*) FROM application_status_history WHERE application_id = ?", id(a))).isEqualTo(2);   // SUBMITTED + exactly one transition
    }
}
