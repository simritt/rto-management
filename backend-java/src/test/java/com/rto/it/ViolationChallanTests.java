package com.rto.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.core.Clock;
import com.rto.domain.Citizen;
import com.rto.domain.ViolationType;
import com.rto.support.Api.Resp;
import com.rto.support.BaseIT;
import com.rto.support.Flows.Env;
import com.rto.support.Fx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.rto.support.Api.m;
import static org.assertj.core.api.Assertions.assertThat;

class ViolationChallanTests extends BaseIT {
    Env env;
    long vehicleId;

    @BeforeEach
    void setup() {
        env = flows.makeEnv();
        vehicleId = newVehicle();
    }

    long newVehicle() {
        Fx.Refs refs = fx.makeVehicleRefs(false);
        Map<String, Object> b = m("registration_number", "MH12" + Fx.u(6), "chassis_number", "CH" + Fx.u(12), "engine_number", "EN" + Fx.u(12),
                "manufacture_year", 2020, "registering_office_id", env.office.getOfficeId(), "owner_citizen_id", env.citizen.getCitizenId());
        b.putAll(refs.body());
        return api.post("/api/v1/vehicles", env.token, b).l("vehicle_id");
    }

    ViolationType vtype(String fine) {
        return fx.makeViolationType(fine);
    }

    String hoursAgo() {
        return LocalDateTime.now(java.time.ZoneOffset.UTC).minusHours(2).withNano(0).toString();
    }

    Resp violation(long vehicle, ViolationType vt, Object... over) {
        Resp r = api.post("/api/v1/violations", env.token, flows.put(m("vehicle_id", vehicle, "violation_type_id", vt.getViolationTypeId(),
                "location", "NH48 km 12", "occurred_at", hoursAgo()), over));
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
        return r;
    }

    Resp challan(Resp... violations) {
        List<Long> ids = new ArrayList<>();
        for (Resp v : violations) ids.add(v.l("violation_id"));
        Resp r = api.post("/api/v1/challans", env.token, m("violation_ids", ids));
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
        return r;
    }

    // ------------------------------- violations -------------------------------

    @Test
    void violationWithUnknownDriverIsValid() {
        ViolationType vt = vtype("1500.50");
        Resp v = violation(vehicleId, vt);
        assertThat(v.isNull("driver_citizen_id")).isTrue();
        assertThat(v.s("fine_amount")).isEqualTo("1500.50");
        assertThat(v.isNull("challan_id")).isTrue();
        assertThat(v.l("officer_employee_id")).isEqualTo(env.emp.getEmployeeId());
        assertThat(v.s("violation_description")).isEqualTo(vt.getDescription());
    }

    @Test
    void violationWithKnownDriverAndValidation() {
        ViolationType vt = vtype("500.00");
        Citizen driver = fx.makeCitizen();
        assertThat(violation(vehicleId, vt, "driver_citizen_id", driver.getCitizenId()).l("driver_citizen_id")).isEqualTo(driver.getCitizenId());
        Function<Object[], Resp> post = over -> api.post("/api/v1/violations", env.token, flows.put(m("vehicle_id", vehicleId,
                "violation_type_id", vt.getViolationTypeId(), "location", "x", "occurred_at", "2024-01-01T10:00:00"), over));
        assertThat(post.apply(new Object[]{"vehicle_id", 99999999}).status()).isEqualTo(404);
        assertThat(post.apply(new Object[]{"violation_type_id", 99999999}).status()).isEqualTo(404);
        assertThat(post.apply(new Object[]{"driver_citizen_id", 99999999}).status()).isEqualTo(404);
        assertThat(post.apply(new Object[]{"officer_employee_id", 99999999}).status()).isEqualTo(404);
        assertThat(post.apply(new Object[]{"occurred_at", "2999-01-01T00:00:00"}).status()).isEqualTo(400);
        assertThat(post.apply(new Object[]{"location", ""}).status()).isEqualTo(422);
        var inactive = fx.makeEmployee(env.office);
        fx.jdbc.update("UPDATE employees SET is_active = 0 WHERE employee_id = ?", inactive.getEmployeeId());
        assertThat(post.apply(new Object[]{"officer_employee_id", inactive.getEmployeeId()}).status()).isEqualTo(409);
        String outsider = fx.withPermissions("violation.create", "violation.view");
        assertThat(api.post("/api/v1/violations", outsider, m("vehicle_id", vehicleId, "violation_type_id", vt.getViolationTypeId(), "location", "x",
                "occurred_at", "2024-01-01T10:00:00")).status()).isEqualTo(400);
    }

    @Test
    void violationListingFilters() {
        ViolationType vt = vtype("500.00");
        Resp v = violation(vehicleId, vt, "location", "Kothrud Chowk");
        Function<Map<String, Object>, List<Long>> q = p -> {
            List<Long> ids = new ArrayList<>();
            api.get("/api/v1/violations", env.token, p).json().get("items").forEach(x -> ids.add(x.get("violation_id").asLong()));
            return ids;
        };
        assertThat(q.apply(m("vehicle_id", vehicleId, "violation_type_id", vt.getViolationTypeId()))).containsExactly(v.l("violation_id"));
        assertThat(q.apply(m("search", "Kothrud"))).contains(v.l("violation_id"));
        assertThat(q.apply(m("officer_id", env.emp.getEmployeeId(), "vehicle_id", vehicleId))).containsExactly(v.l("violation_id"));
        assertThat(q.apply(m("vehicle_id", vehicleId, "unchallaned", true))).containsExactly(v.l("violation_id"));
        challan(v);
        assertThat(q.apply(m("vehicle_id", vehicleId, "unchallaned", true))).isEmpty();
        assertThat(q.apply(m("vehicle_id", vehicleId, "unchallaned", false))).containsExactly(v.l("violation_id"));
        assertThat(api.get("/api/v1/violations/" + v.l("violation_id"), env.token).isNull("challan_id")).isFalse();
    }

    // ------------------------------- challans -------------------------------

    @Test
    void challanTotalIsTheExactSumInDecimal() {
        Resp v1 = violation(vehicleId, vtype("1500.50")), v2 = violation(vehicleId, vtype("500.25")), v3 = violation(vehicleId, vtype("0.10"));
        Resp c = challan(v1, v2, v3);
        assertThat(c.s("total_amount")).isEqualTo("2000.85");
        assertThat(c.s("status")).isEqualTo("ISSUED");
        assertThat(c.size("violations")).isEqualTo(3);
        assertThat(c.s("challan_number")).startsWith("CHN-");
        assertThat(c.s("registration_number")).isNotBlank();
        c.json().get("violations").forEach(v -> assertThat(v.get("challan_id").asLong()).isEqualTo(c.l("challan_id")));
        List<Map<String, Object>> hist = fx.jdbc.queryForList("SELECT previous_status, new_status FROM challan_status_history WHERE challan_id = ?", c.l("challan_id"));
        assertThat(hist).hasSize(1);
        assertThat(hist.get(0).get("previous_status")).isNull();
        assertThat(hist.get(0).get("new_status")).isEqualTo("ISSUED");
        assertThat(fx.count("SELECT COUNT(*) FROM audit_logs WHERE table_name = 'challans' AND record_id = ?", c.l("challan_id"))).isGreaterThan(0);
    }

    @Test
    void challanCreationValidation() {
        ViolationType vt = vtype("500.00");
        Resp v = violation(vehicleId, vt);
        Resp w = violation(newVehicle(), vt);
        Resp mixed = api.post("/api/v1/challans", env.token, m("violation_ids", List.of(v.l("violation_id"), w.l("violation_id"))));
        assertThat(mixed.status()).isEqualTo(409);
        assertThat(mixed.code()).isEqualTo("MIXED_VEHICLES");
        assertThat(api.post("/api/v1/challans", env.token, m("violation_ids", List.of())).status()).isEqualTo(422);
        assertThat(api.post("/api/v1/challans", env.token, m("violation_ids", List.of(99999999))).status()).isEqualTo(400);
        Resp dups = api.post("/api/v1/challans", env.token, m("violation_ids", List.of(v.l("violation_id"), v.l("violation_id"))));
        assertThat(dups.status()).isEqualTo(400);
        assertThat(dups.code()).isEqualTo("DUPLICATE_VIOLATION");
        challan(v);
        Resp again = api.post("/api/v1/challans", env.token, m("violation_ids", List.of(v.l("violation_id"))));
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isEqualTo("VIOLATION_ALREADY_CHALLANED");
        String num = "CHN-" + Fx.u(8);
        assertThat(api.post("/api/v1/challans", env.token, m("violation_ids", List.of(w.l("violation_id")), "challan_number", num)).status()).isEqualTo(201);
        Resp x = violation(vehicleId, vt);
        assertThat(api.post("/api/v1/challans", env.token, m("violation_ids", List.of(x.l("violation_id")), "challan_number", num)).status()).isEqualTo(409);
    }

    @Test
    void addViolationsRecalculatesAndBlocksDuplicates() {
        Resp v1 = violation(vehicleId, vtype("100.10")), v2 = violation(vehicleId, vtype("200.20")), v3 = violation(vehicleId, vtype("300.30"));
        Resp c = challan(v1);
        String url = "/api/v1/challans/" + c.l("challan_id") + "/violations";
        Resp r = api.post(url, env.token, m("violation_ids", List.of(v2.l("violation_id"), v3.l("violation_id"))));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.s("total_amount")).isEqualTo("600.60");
        assertThat(r.size("violations")).isEqualTo(3);
        Resp dup = api.post(url, env.token, m("violation_ids", List.of(v2.l("violation_id"))));
        assertThat(dup.status()).isEqualTo(409);
        assertThat(dup.code()).isEqualTo("DUPLICATE_VIOLATION");
        Resp other = challan(violation(vehicleId, vtype("500.00")));
        Resp taken = api.post("/api/v1/challans/" + other.l("challan_id") + "/violations", env.token, m("violation_ids", List.of(v1.l("violation_id"))));
        assertThat(taken.status()).isEqualTo(409);
        assertThat(taken.code()).isEqualTo("VIOLATION_ALREADY_CHALLANED");
        Resp foreign = violation(newVehicle(), vtype("500.00"));
        Resp mixed = api.post(url, env.token, m("violation_ids", List.of(foreign.l("violation_id"))));
        assertThat(mixed.status()).isEqualTo(409);
        assertThat(mixed.code()).isEqualTo("MIXED_VEHICLES");
        api.post("/api/v1/challans/" + c.l("challan_id") + "/cancel", env.token, m("reason", "issued in error"));
        Resp late = api.post(url, env.token, m("violation_ids", List.of(violation(vehicleId, vtype("500.00")).l("violation_id"))));
        assertThat(late.status()).isEqualTo(409);
        assertThat(late.code()).isEqualTo("INVALID_STATE");
    }

    @Test
    void challanStatusWorkflowHistoryAndAudit() {
        Resp c = challan(violation(vehicleId, vtype("500.00")));
        long cid = c.l("challan_id");
        BiFunction<String, String, Resp> act = (a, reason) -> api.post("/api/v1/challans/" + cid + "/" + a, env.token, m("reason", reason));
        assertThat(act.apply("reissue", "valid reason").status()).isEqualTo(409);   // ISSUED -> ISSUED
        assertThat(act.apply("dispute", "x").status()).isEqualTo(422);
        assertThat(act.apply("dispute", "Not my vehicle").s("status")).isEqualTo("DISPUTED");
        assertThat(act.apply("dispute", "valid reason").status()).isEqualTo(409);
        assertThat(act.apply("reissue", "Dispute dismissed").s("status")).isEqualTo("ISSUED");
        assertThat(act.apply("cancel", "Issued in error").s("status")).isEqualTo("CANCELLED");
        for (String a : new String[]{"dispute", "reissue", "cancel"}) assertThat(act.apply(a, "valid reason").status()).as(a).isEqualTo(409);
        List<Map<String, Object>> hist = fx.jdbc.queryForList("SELECT previous_status, new_status FROM challan_status_history WHERE challan_id = ? ORDER BY history_id", cid);
        assertThat(hist).extracting(h -> h.get("previous_status") + ">" + h.get("new_status")).containsExactly("null>ISSUED", "ISSUED>DISPUTED", "DISPUTED>ISSUED", "ISSUED>CANCELLED");
        List<String> audits = fx.jdbc.queryForList("SELECT CAST(new_values AS CHAR) FROM audit_logs WHERE table_name = 'challans' AND record_id = ? AND action = 'UPDATE' ORDER BY audit_id", String.class, cid);
        assertThat(audits).hasSize(3);
        assertThat(audits.get(0)).contains("Not my vehicle");
        JsonNode h = api.get("/api/v1/challans/" + cid + "/history", env.token).json();
        assertThat(h.get(h.size() - 1).get("new_status").asText()).isEqualTo("CANCELLED");
    }

    @Test
    void cancelledChallanReleasesItsViolations() {
        Resp v = violation(vehicleId, vtype("750.00"));
        Resp c = challan(v);
        api.post("/api/v1/challans/" + c.l("challan_id") + "/cancel", env.token, m("reason", "wrong vehicle"));
        assertThat(api.get("/api/v1/violations/" + v.l("violation_id"), env.token).isNull("challan_id")).isTrue();
        Resp again = challan(v);
        assertThat(again.l("challan_id")).isNotEqualTo(c.l("challan_id"));
        assertThat(again.s("total_amount")).isEqualTo("750.00");
    }

    @Test
    void challanListingAndPermissions() {
        Citizen driver = fx.makeCitizen();
        Resp v = violation(vehicleId, vtype("500.00"), "driver_citizen_id", driver.getCitizenId());
        Resp c = challan(v);
        Function<Map<String, Object>, List<Long>> q = p -> {
            List<Long> ids = new ArrayList<>();
            api.get("/api/v1/challans", env.token, p).json().get("items").forEach(x -> ids.add(x.get("challan_id").asLong()));
            return ids;
        };
        assertThat(q.apply(m("vehicle_id", vehicleId, "status", "ISSUED"))).containsExactly(c.l("challan_id"));
        assertThat(q.apply(m("driver_citizen_id", driver.getCitizenId()))).containsExactly(c.l("challan_id"));
        assertThat(q.apply(m("search", c.s("challan_number")))).containsExactly(c.l("challan_id"));
        assertThat(q.apply(m("vehicle_id", vehicleId, "status", "PAID"))).isEmpty();
        String viewer = fx.withPermissions("challan.view");
        assertThat(api.get("/api/v1/challans/" + c.l("challan_id"), viewer).status()).isEqualTo(200);
        assertThat(api.post("/api/v1/challans/" + c.l("challan_id") + "/cancel", viewer, m("reason", "no rights")).status()).isEqualTo(403);
        assertThat(api.post("/api/v1/challans", viewer, m("violation_ids", List.of(v.l("violation_id")))).status()).isEqualTo(403);
        assertThat(Clock.today()).isNotNull();
    }
}
