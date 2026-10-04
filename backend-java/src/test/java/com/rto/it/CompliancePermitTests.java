package com.rto.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.core.Clock;
import com.rto.domain.FitnessCertificate;
import com.rto.domain.PermitType;
import com.rto.domain.PollutionCertificate;
import com.rto.domain.RoadTaxRecord;
import com.rto.support.Api.Resp;
import com.rto.support.BaseIT;
import com.rto.support.Flows.Env;
import com.rto.support.Fx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.rto.support.Api.m;
import static org.assertj.core.api.Assertions.assertThat;

class CompliancePermitTests extends BaseIT {
    Env env;
    long vehicleId;        // commercial vehicle owned by env.citizen
    long privateVehicleId;

    @BeforeEach
    void setup() {
        env = flows.makeEnv();
        vehicleId = createVehicle(fx.makeVehicleRefs(true), env.citizen.getCitizenId());
        privateVehicleId = createVehicle(fx.makeVehicleRefs(false), null);
    }

    private long createVehicle(Fx.Refs refs, Long owner) {
        Map<String, Object> b = m("registration_number", "MH12" + Fx.u(6), "chassis_number", "CH" + Fx.u(12), "engine_number", "EN" + Fx.u(12),
                "manufacture_year", 2020, "registering_office_id", env.office.getOfficeId());
        b.putAll(refs.body());
        if (owner != null) b.put("owner_citizen_id", owner);
        Resp r = api.post("/api/v1/vehicles", env.token, b);
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
        return r.l("vehicle_id");
    }

    private static String d(int days) {
        return Clock.today().plusDays(days).toString();
    }

    private Resp post(String path, Map<String, Object> body) {
        return api.post("/api/v1/" + path, env.token, body);
    }

    // ------------------------------- inspections / fitness -------------------------------

    @Test
    void inspectionDefaultsInspectorToTheCallingEmployee() {
        Resp r = post("inspections", m("vehicle_id", vehicleId, "result", "PASS", "remarks", "ok"));
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.l("inspector_employee_id")).isEqualTo(env.emp.getEmployeeId());
        assertThat(post("inspections", m("vehicle_id", 99999999, "result", "PASS")).status()).isEqualTo(404);
        assertThat(post("inspections", m("vehicle_id", vehicleId, "result", "MAYBE")).status()).isEqualTo(422);
        assertThat(post("inspections", m("vehicle_id", vehicleId, "result", "PASS", "inspector_employee_id", 99999999)).status()).isEqualTo(404);
        assertThat(post("inspections", m("vehicle_id", vehicleId, "result", "PASS", "inspected_at", "2999-01-01T00:00:00")).status()).isEqualTo(400);
        assertThat(api.get("/api/v1/inspections", env.token, m("vehicle_id", vehicleId)).l("total")).isEqualTo(1);
        String outsider = fx.admin();   // an admin who is not an employee must name the inspector
        assertThat(api.post("/api/v1/inspections", outsider, m("vehicle_id", vehicleId, "result", "PASS")).status()).isEqualTo(400);
    }

    @Test
    void fitnessCertificateRules() {
        Resp fail = post("inspections", m("vehicle_id", vehicleId, "result", "FAIL"));
        Resp good = post("inspections", m("vehicle_id", vehicleId, "result", "PASS"));
        Map<String, Object> body = m("vehicle_id", vehicleId, "issue_date", d(-1), "expiry_date", d(364));
        assertThat(post("fitness-certificates", flows.put(body, "expiry_date", d(-1))).status()).isEqualTo(422);
        assertThat(post("fitness-certificates", flows.put(body, "inspection_id", fail.l("inspection_id"))).code()).isEqualTo("INSPECTION_NOT_PASSED");
        long other = createVehicle(fx.makeVehicleRefs(true), null);
        Resp wrong = post("fitness-certificates", flows.put(body, "vehicle_id", other, "inspection_id", good.l("inspection_id")));
        assertThat(wrong.status()).isEqualTo(409);
        assertThat(wrong.code()).isEqualTo("INSPECTION_VEHICLE_MISMATCH");
        Resp r = post("fitness-certificates", flows.put(body, "inspection_id", good.l("inspection_id")));
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.s("status")).isEqualTo("ACTIVE");
        long cid = r.l("certificate_id");
        Resp rev = api.post("/api/v1/fitness-certificates/" + cid + "/revoke", env.token, m("reason", "failed re-test"));
        assertThat(rev.status()).isEqualTo(200);
        assertThat(rev.s("status")).isEqualTo("REVOKED");
        assertThat(api.post("/api/v1/fitness-certificates/" + cid + "/revoke", env.token, m("reason", "again again")).status()).isEqualTo(409);
        assertThat(post("fitness-certificates", m("vehicle_id", vehicleId, "issue_date", d(-400), "expiry_date", d(-35))).s("status")).isEqualTo("EXPIRED");
    }

    @Test
    void recordsCanOnlyBeAddedToActiveVehicles() {
        api.patch("/api/v1/vehicles/" + vehicleId, env.token, m("status", "SCRAPPED", "reason", "end of life"));
        Resp r = post("pollution-certificates", m("vehicle_id", vehicleId, "issue_date", d(-1), "expiry_date", d(100)));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("VEHICLE_NOT_ACTIVE");
    }

    // ------------------------------- pollution / insurance / tax -------------------------------

    @Test
    void pollutionAndInsuranceRules() {
        assertThat(post("pollution-certificates", m("vehicle_id", vehicleId, "issue_date", d(5), "expiry_date", d(5))).status()).isEqualTo(422);
        assertThat(post("pollution-certificates", m("vehicle_id", vehicleId, "issue_date", d(-1), "expiry_date", d(180))).status()).isEqualTo(201);
        String pol = "POL/" + Fx.u(8);
        Map<String, Object> base = m("vehicle_id", vehicleId, "provider_name", "Acme Insurance", "policy_number", pol, "start_date", d(-1), "end_date", d(364));
        assertThat(post("insurance-policies", flows.put(base, "end_date", d(-1))).status()).isEqualTo(422);
        assertThat(post("insurance-policies", base).status()).isEqualTo(201);
        Resp dup = post("insurance-policies", base);
        assertThat(dup.status()).isEqualTo(409);
        assertThat(dup.code()).isEqualTo("DUPLICATE");
    }

    @Test
    void roadTaxRules() {
        Map<String, Object> body = m("vehicle_id", vehicleId, "assessment_year", 2030, "amount_due", "1250.50", "due_date", d(30));
        Resp r = post("road-tax", body);
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.s("status")).isEqualTo("DUE");
        assertThat(r.s("amount_due")).isEqualTo("1250.50");
        assertThat(post("road-tax", body).status()).isEqualTo(409);
        assertThat(post("road-tax", flows.put(body, "assessment_year", 2031, "amount_due", "0")).status()).isEqualTo(422);
        assertThat(post("road-tax", flows.put(body, "assessment_year", 2032, "amount_due", "10.999")).status()).isEqualTo(422);
        assertThat(post("road-tax", flows.put(body, "assessment_year", 2024, "due_date", d(-10))).s("status")).isEqualTo("OVERDUE");
    }

    @Test
    void refreshStatusesPersistsTimeDrivenChanges() {
        FitnessCertificate f = new FitnessCertificate();
        f.setVehicleId(vehicleId);
        f.setIssueDate(Clock.today().minusDays(400));
        f.setExpiryDate(Clock.today().minusDays(2));
        f.setStatus("ACTIVE");
        fx.save(f);
        PollutionCertificate p = new PollutionCertificate();
        p.setVehicleId(vehicleId);
        p.setIssueDate(Clock.today().minusDays(400));
        p.setExpiryDate(Clock.today().minusDays(2));
        p.setStatus("ACTIVE");
        fx.save(p);
        RoadTaxRecord t = new RoadTaxRecord();
        t.setVehicleId(vehicleId);
        t.setAssessmentYear(2001);
        t.setAmountDue(new BigDecimal("100.00"));
        t.setDueDate(Clock.today().minusDays(3));
        t.setStatus("DUE");
        fx.save(t);
        Resp out = api.post("/api/v1/compliance/refresh-statuses", env.token);
        assertThat(out.i("fitness_expired")).isGreaterThanOrEqualTo(1);
        assertThat(out.i("pollution_expired")).isGreaterThanOrEqualTo(1);
        assertThat(out.i("road_tax_overdue")).isGreaterThanOrEqualTo(1);
        assertThat(fx.jdbc.queryForObject("SELECT status FROM road_tax_records WHERE vehicle_id = ? AND assessment_year = 2001", String.class, vehicleId)).isEqualTo("OVERDUE");
    }

    // ------------------------------- consolidated compliance & expiring -------------------------------

    @Test
    void consolidatedComplianceForACommercialVehicle() {
        String url = "/api/v1/vehicles/" + vehicleId + "/compliance";
        Resp c = api.get(url, env.token);
        assertThat(c.s("overall")).isEqualTo("NON_COMPLIANT");
        assertThat(c.at("fitness/valid").asBoolean()).isFalse();
        assertThat(c.at("fitness/required").asBoolean()).isTrue();
        post("fitness-certificates", m("vehicle_id", vehicleId, "issue_date", d(-1), "expiry_date", d(200)));
        post("pollution-certificates", m("vehicle_id", vehicleId, "issue_date", d(-1), "expiry_date", d(90)));
        post("insurance-policies", m("vehicle_id", vehicleId, "provider_name", "P", "policy_number", "I" + Fx.u(9), "start_date", d(-1), "end_date", d(300)));
        c = api.get(url, env.token);
        assertThat(c.s("overall")).isEqualTo("COMPLIANT");
        assertThat(c.at("pollution/days_to_expiry").asInt()).isEqualTo(90);
        assertThat(c.at("fitness/days_to_expiry").asInt()).isEqualTo(200);
        post("road-tax", m("vehicle_id", vehicleId, "assessment_year", 2020, "amount_due", "500.00", "due_date", d(-5)));
        c = api.get(url, env.token);
        assertThat(c.s("overall")).isEqualTo("NON_COMPLIANT");
        assertThat(c.at("road_tax/valid").asBoolean()).isFalse();
        assertThat(c.at("road_tax/detail").asText()).contains("overdue");
    }

    @Test
    void privateVehicleDoesNotNeedFitness() {
        post("pollution-certificates", m("vehicle_id", privateVehicleId, "issue_date", d(-1), "expiry_date", d(90)));
        post("insurance-policies", m("vehicle_id", privateVehicleId, "provider_name", "P", "policy_number", "I" + Fx.u(9), "start_date", d(-1), "end_date", d(300)));
        Resp c = api.get("/api/v1/vehicles/" + privateVehicleId + "/compliance", env.token);
        assertThat(c.at("fitness/required").asBoolean()).isFalse();
        assertThat(c.at("fitness/valid").asBoolean()).isTrue();
        assertThat(c.s("overall")).isEqualTo("COMPLIANT");
    }

    @Test
    void expiringQuery() {
        post("fitness-certificates", m("vehicle_id", vehicleId, "issue_date", d(-300), "expiry_date", d(10)));
        post("pollution-certificates", m("vehicle_id", vehicleId, "issue_date", d(-100), "expiry_date", d(20)));
        post("insurance-policies", m("vehicle_id", vehicleId, "provider_name", "P", "policy_number", "I" + Fx.u(9), "start_date", d(-300), "end_date", d(200)));
        Resp r = api.get("/api/v1/compliance/expiring", env.token, m("days", 30, "page_size", 100));
        List<String> mine = new ArrayList<>();
        r.json().get("items").forEach(i -> {
            if (i.get("vehicle_id").asLong() == vehicleId) mine.add(i.get("kind").asText() + ":" + i.get("days_left").asInt());
            assertThat(i.get("registration_number").asText()).isNotBlank();
        });
        assertThat(mine).containsExactlyInAnyOrder("FITNESS:10", "POLLUTION:20");
        Resp wide = api.get("/api/v1/compliance/expiring", env.token, m("days", 365, "kind", "INSURANCE", "page_size", 100));
        boolean found = false;
        for (JsonNode i : wide.json().get("items")) found |= i.get("vehicle_id").asLong() == vehicleId;
        assertThat(found).isTrue();
        assertThat(api.get("/api/v1/compliance/expiring", env.token, m("kind", "BOGUS")).status()).isEqualTo(422);
    }

    // ------------------------------- routes -------------------------------

    @Test
    void routesAndOrderedSegments() {
        Resp route = post("routes", m("route_name", "Pune-Mumbai " + Fx.u(4), "origin", "Pune", "destination", "Mumbai"));
        long rid = route.l("route_id");
        java.util.function.Function<Map<String, Object>, Resp> seg = b -> api.post("/api/v1/routes/" + rid + "/segments", env.token, b);
        assertThat(seg.apply(m("segment_name", "Lonavala")).i("sequence_no")).isEqualTo(1);
        assertThat(seg.apply(m("segment_name", "Khopoli")).i("sequence_no")).isEqualTo(2);
        assertThat(seg.apply(m("segment_name", "Panvel", "sequence_no", 10)).status()).isEqualTo(201);
        Resp dup = seg.apply(m("segment_name", "Dup", "sequence_no", 2));
        assertThat(dup.status()).isEqualTo(409);
        assertThat(dup.code()).isEqualTo("DUPLICATE");
        assertThat(seg.apply(m("segment_name", "Next")).i("sequence_no")).isEqualTo(11);
        JsonNode segs = api.get("/api/v1/routes/" + rid + "/segments", env.token).json();
        List<Integer> seqs = new ArrayList<>();
        segs.forEach(s -> seqs.add(s.get("sequence_no").asInt()));
        assertThat(seqs).containsExactly(1, 2, 10, 11);
        assertThat(segs.get(0).get("segment_name").asText()).isEqualTo("Lonavala");
        List<String> names = new ArrayList<>();
        api.get("/api/v1/routes/" + rid, env.token).json().get("segments").forEach(s -> names.add(s.get("segment_name").asText()));
        assertThat(names).containsExactly("Lonavala", "Khopoli", "Panvel", "Next");
        assertThat(api.get("/api/v1/routes", env.token, m("search", "Mumbai")).l("total")).isGreaterThanOrEqualTo(1);
        assertThat(api.post("/api/v1/routes/99999999/segments", env.token, m("segment_name", "x")).status()).isEqualTo(404);
    }

    // ------------------------------- permits -------------------------------

    private PermitType ptype() {
        return fx.makePermitType(12);
    }

    private Map<String, Object> permitBody(PermitType t, JsonNode app, Object... over) {
        return flows.put(m("vehicle_id", vehicleId, "citizen_id", env.citizen.getCitizenId(), "permit_type_id", t.getPermitTypeId(),
                "application_id", app.get("application_id").asLong()), over);
    }

    @Test
    void permitIssueDefaultsExpiryFromTypeAndCompletesTheApplication() {
        PermitType t = ptype();
        JsonNode app = flows.approvedApp(env);
        Resp route = post("routes", m("route_name", "R" + Fx.u(5), "origin", "A", "destination", "B"));
        Resp r = post("permits", permitBody(t, app, "route_id", route.l("route_id"), "issue_date", "2025-01-31"));
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
        assertThat(r.s("status")).isEqualTo("ACTIVE");
        assertThat(r.s("expiry_date")).isEqualTo("2026-01-31");
        assertThat(r.l("route_id")).isEqualTo(route.l("route_id"));
        assertThat(api.get("/api/v1/applications/" + app.get("application_id").asLong(), env.token).s("current_status")).isEqualTo("COMPLETED");
        JsonNode hist = api.get("/api/v1/permits/" + r.l("permit_id") + "/history", env.token).json();
        assertThat(hist).hasSize(1);
        assertThat(hist.get(0).get("previous_status").isNull()).isTrue();
        assertThat(hist.get(0).get("new_status").asText()).isEqualTo("ACTIVE");
    }

    @Test
    void permitValidation() {
        PermitType t = ptype();
        JsonNode app = flows.approvedApp(env);
        assertThat(post("permits", permitBody(t, app, "issue_date", "2025-05-01", "expiry_date", "2025-04-01")).status()).isEqualTo(422);
        assertThat(post("permits", permitBody(t, app, "issue_date", "2025-05-01", "expiry_date", "2025-05-01")).status()).isEqualTo(422);
        assertThat(post("permits", permitBody(t, app, "vehicle_id", 99999999)).status()).isEqualTo(404);
        assertThat(post("permits", permitBody(t, app, "citizen_id", 99999999)).status()).isEqualTo(404);
        assertThat(post("permits", permitBody(t, app, "permit_type_id", 99999999)).status()).isEqualTo(404);
        assertThat(post("permits", permitBody(t, app, "route_id", 99999999)).status()).isEqualTo(404);
        assertThat(post("permits", permitBody(t, app, "application_id", 99999999)).status()).isEqualTo(404);
        Resp notCommercial = post("permits", permitBody(t, app, "vehicle_id", privateVehicleId));
        assertThat(notCommercial.status()).isEqualTo(409);
        assertThat(notCommercial.code()).isEqualTo("VEHICLE_NOT_COMMERCIAL");
        Resp unapproved = post("permits", permitBody(t, flows.verifiedApp(env)));
        assertThat(unapproved.status()).isEqualTo(409);
        assertThat(unapproved.code()).isEqualTo("APPLICATION_NOT_APPROVED");
        JsonNode strangerApp = flows.approvedApp(env.with(fx.makeCitizen()));
        Resp mismatch = post("permits", permitBody(t, strangerApp));
        assertThat(mismatch.status()).isEqualTo(409);
        assertThat(mismatch.code()).isEqualTo("APPLICATION_CITIZEN_MISMATCH");
        Resp ok = post("permits", permitBody(t, app, "permit_number", "PN" + Fx.u(8)));
        assertThat(ok.status()).isEqualTo(201);
        JsonNode app2 = flows.approvedApp(env);
        assertThat(post("permits", permitBody(t, app2, "permit_number", ok.s("permit_number"))).status()).isEqualTo(409);
        Resp clash = post("permits", permitBody(t, app2));
        assertThat(clash.status()).isEqualTo(409);
        assertThat(clash.code()).isEqualTo("PERMIT_EXISTS");
    }

    @Test
    void permitStatusWorkflowIsValidatedAndRecorded() {
        Resp p = post("permits", permitBody(ptype(), flows.approvedApp(env)));
        long pid = p.l("permit_id");
        java.util.function.BiFunction<String, String, Resp> act = (a, reason) -> api.post("/api/v1/permits/" + pid + "/" + a, env.token, m("reason", reason));
        assertThat(act.apply("reinstate", "valid reason").status()).isEqualTo(409);   // ACTIVE -> ACTIVE is not a move
        assertThat(act.apply("suspend", "x").status()).isEqualTo(422);
        assertThat(act.apply("suspend", "Unpaid dues").s("status")).isEqualTo("SUSPENDED");
        assertThat(act.apply("suspend", "valid reason").status()).isEqualTo(409);
        assertThat(act.apply("reinstate", "Dues cleared").s("status")).isEqualTo("ACTIVE");
        assertThat(act.apply("cancel", "Operator request").s("status")).isEqualTo("CANCELLED");
        for (String a : new String[]{"suspend", "reinstate", "cancel"}) assertThat(act.apply(a, "valid reason").status()).as(a).isEqualTo(409);
        List<Map<String, Object>> hist = fx.jdbc.queryForList("SELECT previous_status, new_status, reason FROM permit_status_history WHERE permit_id = ? ORDER BY history_id", pid);
        assertThat(hist).extracting(h -> h.get("previous_status") + ">" + h.get("new_status") + ":" + h.get("reason")).containsExactly(
                "null>ACTIVE:Permit issued", "ACTIVE>SUSPENDED:Unpaid dues", "SUSPENDED>ACTIVE:Dues cleared", "ACTIVE>CANCELLED:Operator request");
        List<Map<String, Object>> audits = fx.jdbc.queryForList("SELECT changed_by_user_id FROM audit_logs WHERE table_name = 'permits' AND record_id = ? AND action = 'UPDATE'", pid);
        assertThat(audits).hasSize(3);
        audits.forEach(a -> assertThat(((Number) a.get("changed_by_user_id")).longValue()).isEqualTo(env.user.getUserId()));
    }

    @Test
    void expiredPermitCannotBeReinstatedAndTheSweepExpiresIt() {
        Resp p = post("permits", permitBody(ptype(), flows.approvedApp(env), "issue_date", d(-100), "expiry_date", d(-1)));
        long pid = p.l("permit_id");
        assertThat(api.post("/api/v1/permits/" + pid + "/suspend", env.token, m("reason", "audit hold")).status()).isEqualTo(200);
        Resp r = api.post("/api/v1/permits/" + pid + "/reinstate", env.token, m("reason", "hold lifted"));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("PERMIT_EXPIRED");
        assertThat(api.post("/api/v1/permits/expire-overdue", env.token).i("permits_expired")).isGreaterThanOrEqualTo(1);
        assertThat(api.get("/api/v1/permits/" + pid, env.token).s("status")).isEqualTo("EXPIRED");
        assertThat(api.post("/api/v1/permits/" + pid + "/cancel", env.token, m("reason", "too late")).status()).isEqualTo(409);
    }

    @Test
    void permitListingFilters() {
        PermitType t = ptype();
        Resp p = post("permits", permitBody(t, flows.approvedApp(env), "issue_date", d(-1), "expiry_date", d(20)));
        java.util.function.Function<Map<String, Object>, List<Long>> q = params -> {
            List<Long> ids = new ArrayList<>();
            api.get("/api/v1/permits", env.token, params).json().get("items").forEach(i -> ids.add(i.get("permit_id").asLong()));
            return ids;
        };
        assertThat(q.apply(m("vehicle_id", vehicleId, "status", "ACTIVE"))).containsExactly(p.l("permit_id"));
        assertThat(q.apply(m("citizen_id", env.citizen.getCitizenId(), "permit_type_id", t.getPermitTypeId()))).containsExactly(p.l("permit_id"));
        assertThat(q.apply(m("search", p.s("permit_number")))).containsExactly(p.l("permit_id"));
        assertThat(q.apply(m("vehicle_id", vehicleId, "expiring_within_days", 30))).containsExactly(p.l("permit_id"));
        assertThat(q.apply(m("vehicle_id", vehicleId, "expiring_within_days", 5))).isEmpty();
    }
}
