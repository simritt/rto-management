package com.rto.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.core.ApiException;
import com.rto.core.Clock;
import com.rto.core.CurrentUser;
import com.rto.domain.Citizen;
import com.rto.domain.VehicleOwnership;
import com.rto.service.OwnershipService;
import com.rto.service.UserLoader;
import com.rto.support.Api.Resp;
import com.rto.support.BaseIT;
import com.rto.support.Flows.Env;
import com.rto.support.Fx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static com.rto.support.Api.m;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VehicleTests extends BaseIT {
    @Autowired OwnershipService ownership;
    @Autowired UserLoader loader;

    Env env;
    Fx.Refs refs;

    @BeforeEach
    void setup() {
        env = flows.makeEnv();
        refs = fx.makeVehicleRefs(false);
    }

    Map<String, Object> vehicleBody(Fx.Refs r, Object... over) {
        Map<String, Object> b = m("registration_number", "MH12" + Fx.u(6), "chassis_number", "CH" + Fx.u(12), "engine_number", "EN" + Fx.u(12));
        b.putAll(r.body());
        b.putAll(m("manufacture_year", 2020, "color", "Red", "registering_office_id", env.office.getOfficeId()));
        for (int i = 0; i < over.length; i += 2) b.put((String) over[i], over[i + 1]);
        return b;
    }

    Resp createVehicle(Fx.Refs r, Object... over) {
        Resp resp = api.post("/api/v1/vehicles", env.token, vehicleBody(r, over));
        assertThat(resp.status()).as(resp.toString()).isEqualTo(201);
        return resp;
    }

    // ------------------------------- vehicles -------------------------------

    @Test
    void createVehicleNormalisesNumbersAndAudits() {
        Resp r = api.post("/api/v1/vehicles", env.token, vehicleBody(refs, "registration_number", "mh 12  ab 1234"));
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
        assertThat(r.s("registration_number")).isEqualTo("MH 12 AB 1234");
        assertThat(r.s("status")).isEqualTo("ACTIVE");
        assertThat(r.isNull("current_owner")).isTrue();
        assertThat(r.s("manufacturer_name")).isNotBlank();
        assertThat(r.s("model_name")).isNotBlank();
        assertThat(r.s("fuel_type_name")).isNotBlank();
        assertThat(r.s("vehicle_type_name")).isNotBlank();
        assertThat(fx.count("SELECT COUNT(*) FROM audit_logs WHERE table_name = 'vehicles' AND record_id = ?", r.l("vehicle_id"))).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"registration_number", "chassis_number", "engine_number"})
    void duplicateIdentifiersAre409(String field) {
        Resp first = createVehicle(refs);
        Resp r = api.post("/api/v1/vehicles", env.token, vehicleBody(refs, field, first.s(field).toLowerCase()));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("DUPLICATE");
        assertThat(new String(r.raw())).doesNotContain("uq_");
    }

    @Test
    void vehicleReferenceValidation() {
        Fx.Refs other = fx.makeVehicleRefs(false);
        assertThat(api.post("/api/v1/vehicles", env.token, vehicleBody(refs, "manufacturer_id", 99999999)).status()).isEqualTo(404);
        assertThat(api.post("/api/v1/vehicles", env.token, vehicleBody(refs, "model_id", 99999999)).status()).isEqualTo(404);
        assertThat(api.post("/api/v1/vehicles", env.token, vehicleBody(refs, "vehicle_type_id", 99999999)).status()).isEqualTo(404);
        assertThat(api.post("/api/v1/vehicles", env.token, vehicleBody(refs, "fuel_type_id", 99999999)).status()).isEqualTo(404);
        Resp mismatch = api.post("/api/v1/vehicles", env.token, vehicleBody(refs, "model_id", other.modelId()));
        assertThat(mismatch.status()).isEqualTo(409);
        assertThat(mismatch.code()).isEqualTo("MODEL_MANUFACTURER_MISMATCH");
        assertThat(api.post("/api/v1/vehicles", env.token, vehicleBody(refs, "registering_office_id", fx.makeOffice(false).getOfficeId())).status()).isEqualTo(409);
        assertThat(api.post("/api/v1/vehicles", env.token, vehicleBody(refs, "manufacture_year", 2999)).status()).isEqualTo(422);
        assertThat(api.post("/api/v1/vehicles", env.token, vehicleBody(refs, "registration_date", Clock.today().plusDays(3).toString())).status()).isEqualTo(400);
        assertThat(api.post("/api/v1/vehicles", env.token, vehicleBody(refs, "registration_number", "!!")).status()).isEqualTo(422);
    }

    @Test
    void vehicleSearchAndFilters() {
        Resp v = createVehicle(refs);
        long vid = v.l("vehicle_id");
        java.util.function.Function<Map<String, Object>, List<Long>> q = p -> {
            List<Long> ids = new ArrayList<>();
            api.get("/api/v1/vehicles", env.token, p).json().get("items").forEach(x -> ids.add(x.get("vehicle_id").asLong()));
            return ids;
        };
        String reg = v.s("registration_number");
        assertThat(q.apply(m("search", reg.substring(reg.length() - 6)))).containsExactly(vid);
        assertThat(q.apply(m("registration_number", reg))).containsExactly(vid);
        assertThat(q.apply(m("chassis_number", v.s("chassis_number")))).containsExactly(vid);
        assertThat(q.apply(m("engine_number", v.s("engine_number")))).containsExactly(vid);
        assertThat(q.apply(m("manufacturer_id", refs.manufacturerId(), "model_id", refs.modelId(), "vehicle_type_id", refs.vehicleTypeId(),
                "fuel_type_id", refs.fuelTypeId(), "status", "ACTIVE", "office_id", env.office.getOfficeId()))).containsExactly(vid);
        assertThat(q.apply(m("status", "SCRAPPED", "manufacturer_id", refs.manufacturerId()))).isEmpty();
    }

    @Test
    void vehicleStatusTransitionsNeedAReasonAndAreValidated() {
        Resp v = createVehicle(refs);
        String url = "/api/v1/vehicles/" + v.l("vehicle_id");
        assertThat(api.patch(url, env.token, m("status", "BLACKLISTED")).status()).isEqualTo(400);
        Resp r = api.patch(url, env.token, m("status", "BLACKLISTED", "reason", "stolen vehicle report"));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.s("status")).isEqualTo("BLACKLISTED");
        assertThat(api.patch(url, env.token, m("status", "ACTIVE", "reason", "recovered")).s("status")).isEqualTo("ACTIVE");
        assertThat(api.patch(url, env.token, m("status", "SCRAPPED", "reason", "end of life")).status()).isEqualTo(200);
        Resp back = api.patch(url, env.token, m("status", "ACTIVE", "reason", "oops mistake"));
        assertThat(back.status()).isEqualTo(409);
        assertThat(back.code()).isEqualTo("INVALID_TRANSITION");
        assertThat(fx.count("SELECT COUNT(*) FROM audit_logs WHERE table_name = 'vehicles' AND record_id = ? AND action = 'UPDATE'", v.l("vehicle_id"))).isEqualTo(3);
        assertThat(fx.jdbc.queryForObject("SELECT new_values FROM audit_logs WHERE table_name = 'vehicles' AND record_id = ? AND action = 'UPDATE' ORDER BY audit_id LIMIT 1",
                String.class, v.l("vehicle_id"))).contains("stolen vehicle report");
        assertThat(api.patch(url, env.token, m("color", "Blue")).s("color")).isEqualTo("Blue");
    }

    @Test
    void vehiclePermissions() {
        String viewer = fx.withPermissions("vehicle.view");
        assertThat(api.post("/api/v1/vehicles", viewer, vehicleBody(refs)).status()).isEqualTo(403);
        Resp v = createVehicle(refs);
        assertThat(api.get("/api/v1/vehicles/" + v.l("vehicle_id"), viewer).status()).isEqualTo(200);
        assertThat(api.patch("/api/v1/vehicles/" + v.l("vehicle_id"), viewer, m("color", "x")).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/vehicles", null).status()).isEqualTo(401);
    }

    // ------------------------------- ownership -------------------------------

    @Test
    void ownerAtRegistrationCreatesTheFirstOwnershipRow() {
        Resp v = createVehicle(refs, "owner_citizen_id", env.citizen.getCitizenId(), "registration_date", "2021-03-01");
        assertThat(v.at("current_owner/citizen_id").asLong()).isEqualTo(env.citizen.getCitizenId());
        assertThat(v.at("current_owner/effective_from").asText()).isEqualTo("2021-03-01");
        Resp cur = api.get("/api/v1/vehicles/" + v.l("vehicle_id") + "/current-owner", env.token);
        assertThat(cur.b("is_current")).isTrue();
        assertThat(cur.isNull("effective_to")).isTrue();
        JsonNode mine = api.get("/api/v1/citizens/" + env.citizen.getCitizenId() + "/vehicles", env.token).json();
        assertThat(mine).hasSize(1);
        assertThat(mine.get(0).get("vehicle_id").asLong()).isEqualTo(v.l("vehicle_id"));
        assertThat(api.get("/api/v1/vehicles", env.token, m("owner_citizen_id", env.citizen.getCitizenId())).l("total")).isEqualTo(1);
    }

    @Test
    void ownerlessVehicleHasNoCurrentOwnerAndInitialOwnerRules() {
        Resp v = createVehicle(refs);
        Resp r = api.get("/api/v1/vehicles/" + v.l("vehicle_id") + "/current-owner", env.token);
        assertThat(r.status()).isEqualTo(404);
        assertThat(r.code()).isEqualTo("NO_CURRENT_OWNER");
        assertThat(api.get("/api/v1/vehicles/" + v.l("vehicle_id") + "/owners", env.token).json()).isEmpty();
        String url = "/api/v1/vehicles/" + v.l("vehicle_id") + "/owners";
        assertThat(api.post(url, env.token, m("citizen_id", env.citizen.getCitizenId(), "effective_from", Clock.today().plusDays(1).toString())).status()).isEqualTo(400);
        assertThat(api.post(url, env.token, m("citizen_id", env.citizen.getCitizenId())).status()).isEqualTo(201);
        Resp again = api.post(url, env.token, m("citizen_id", fx.makeCitizen().getCitizenId()));
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isEqualTo("OWNER_EXISTS");
    }

    @Test
    void citizenCanListOwnVehiclesOnly() {
        createVehicle(refs, "owner_citizen_id", env.citizen.getCitizenId());
        String own = fx.citizenToken(env.citizen);
        assertThat(api.get("/api/v1/citizens/" + env.citizen.getCitizenId() + "/vehicles", own).status()).isEqualTo(200);
        assertThat(api.get("/api/v1/citizens/" + fx.makeCitizen().getCitizenId() + "/vehicles", own).status()).isEqualTo(403);
    }

    @Test
    void databaseRejectsTwoCurrentOwners() {
        Resp v = createVehicle(refs, "owner_citizen_id", env.citizen.getCitizenId());
        VehicleOwnership second = new VehicleOwnership();
        second.setVehicleId(v.l("vehicle_id"));
        second.setCitizenId(fx.makeCitizen().getCitizenId());
        second.setEffectiveFrom(Clock.today());
        assertThatThrownBy(() -> fx.save(second)).isInstanceOf(RuntimeException.class);   // uq_vehicle_current_owner (generated column)
    }

    // ------------------------------- transfers -------------------------------

    record Setup(Resp vehicle, Citizen seller, Citizen buyer, Resp transfer) {}

    Setup setupTransfer() {
        Citizen seller = env.citizen, buyer = fx.makeCitizen();
        Resp v = createVehicle(refs, "owner_citizen_id", seller.getCitizenId(), "registration_date", "2020-01-15");
        JsonNode app = flows.newApp(env.with(buyer));
        Resp t = api.post("/api/v1/ownership-transfers", env.token, m("vehicle_id", v.l("vehicle_id"), "to_citizen_id", buyer.getCitizenId(), "application_id", app.get("application_id").asLong()));
        assertThat(t.status()).as(t.toString()).isEqualTo(201);
        return new Setup(v, seller, buyer, t);
    }

    @Test
    void transferApprovalClosesOldOpensNewAndKeepsHistory() {
        Setup s = setupTransfer();
        Resp t = s.transfer();
        assertThat(t.s("status")).isEqualTo("PENDING");
        assertThat(t.l("from_citizen_id")).isEqualTo(s.seller().getCitizenId());
        assertThat(t.isNull("approved_at")).isTrue();
        Resp r = api.post("/api/v1/ownership-transfers/" + t.l("transfer_id") + "/approve", env.token, m("transfer_date", "2023-06-01"));
        assertThat(r.status()).as(r.toString()).isEqualTo(200);
        assertThat(r.s("status")).isEqualTo("APPROVED");
        assertThat(r.isNull("approved_at")).isFalse();
        JsonNode owners = api.get("/api/v1/vehicles/" + s.vehicle().l("vehicle_id") + "/owners", env.token).json();
        assertThat(owners).hasSize(2);
        assertThat(owners.get(0).get("citizen_id").asLong()).isEqualTo(s.buyer().getCitizenId());
        assertThat(owners.get(0).get("effective_from").asText()).isEqualTo("2023-06-01");
        assertThat(owners.get(0).get("effective_to").isNull()).isTrue();
        assertThat(owners.get(0).get("is_current").asBoolean()).isTrue();
        assertThat(owners.get(1).get("citizen_id").asLong()).isEqualTo(s.seller().getCitizenId());
        assertThat(owners.get(1).get("effective_from").asText()).isEqualTo("2020-01-15");
        assertThat(owners.get(1).get("effective_to").asText()).isEqualTo("2023-06-01");
        assertThat(api.get("/api/v1/vehicles/" + s.vehicle().l("vehicle_id") + "/current-owner", env.token).l("citizen_id")).isEqualTo(s.buyer().getCitizenId());
        assertThat(api.get("/api/v1/citizens/" + s.seller().getCitizenId() + "/vehicles", env.token).json()).isEmpty();
        JsonNode hist = api.get("/api/v1/citizens/" + s.seller().getCitizenId() + "/vehicles", env.token, m("include_history", true)).json();
        assertThat(hist.get(0).get("ownership_to").asText()).isEqualTo("2023-06-01");
        assertThat(fx.jdbc.queryForObject("SELECT new_values FROM audit_logs WHERE table_name = 'ownership_transfers' AND record_id = ? AND action = 'UPDATE'", String.class, t.l("transfer_id"))).contains("APPROVED");
        assertThat(api.post("/api/v1/ownership-transfers/" + t.l("transfer_id") + "/approve", env.token).status()).isEqualTo(409);
    }

    @Test
    void invalidTransfersAreRefused() {
        Resp v = createVehicle(refs, "owner_citizen_id", env.citizen.getCitizenId());
        JsonNode app = flows.newApp(env);
        Citizen buyer = fx.makeCitizen();
        java.util.function.Function<Object[], Resp> post = over -> api.post("/api/v1/ownership-transfers", env.token,
                flows.put(m("vehicle_id", v.l("vehicle_id"), "to_citizen_id", buyer.getCitizenId(), "application_id", app.get("application_id").asLong()), over));
        assertThat(post.apply(new Object[]{"to_citizen_id", env.citizen.getCitizenId()}).code()).isEqualTo("SAME_OWNER");
        Resp notOwner = post.apply(new Object[]{"from_citizen_id", buyer.getCitizenId()});
        assertThat(notOwner.status()).isEqualTo(409);
        assertThat(notOwner.code()).isEqualTo("NOT_CURRENT_OWNER");
        assertThat(post.apply(new Object[]{"vehicle_id", 99999999}).status()).isEqualTo(404);
        assertThat(post.apply(new Object[]{"to_citizen_id", 99999999}).status()).isEqualTo(404);
        assertThat(post.apply(new Object[]{"application_id", 99999999}).status()).isEqualTo(404);
        fx.jdbc.update("UPDATE citizens SET blacklisted = 1 WHERE citizen_id = ?", buyer.getCitizenId());
        assertThat(post.apply(new Object[]{}).code()).isEqualTo("CITIZEN_BLACKLISTED");
        fx.jdbc.update("UPDATE citizens SET blacklisted = 0 WHERE citizen_id = ?", buyer.getCitizenId());
        assertThat(post.apply(new Object[]{}).status()).isEqualTo(201);
        Resp pending = post.apply(new Object[]{});
        assertThat(pending.status()).isEqualTo(409);
        assertThat(pending.code()).isEqualTo("TRANSFER_PENDING");
        Resp ownerless = createVehicle(refs);
        Resp r = api.post("/api/v1/ownership-transfers", env.token, m("vehicle_id", ownerless.l("vehicle_id"), "to_citizen_id", buyer.getCitizenId(), "application_id", app.get("application_id").asLong()));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("NO_CURRENT_OWNER");
        api.patch("/api/v1/vehicles/" + v.l("vehicle_id"), env.token, m("status", "BLACKLISTED", "reason", "court order"));
        Resp inactive = post.apply(new Object[]{});
        assertThat(inactive.status()).isEqualTo(409);
        assertThat(inactive.code()).isEqualTo("VEHICLE_NOT_ACTIVE");
    }

    @Test
    void transferDateRules() {
        Setup s = setupTransfer();
        String url = "/api/v1/ownership-transfers/" + s.transfer().l("transfer_id") + "/approve";
        assertThat(api.post(url, env.token, m("transfer_date", Clock.today().plusDays(2).toString())).status()).isEqualTo(400);
        Resp r = api.post(url, env.token, m("transfer_date", "2019-01-01"));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("OWNERSHIP_CHRONOLOGY");
        assertThat(api.get("/api/v1/vehicles/" + s.vehicle().l("vehicle_id") + "/current-owner", env.token).l("citizen_id")).isEqualTo(s.seller().getCitizenId());
    }

    @Test
    void rejectLeavesOwnershipUntouched() {
        Setup s = setupTransfer();
        String base = "/api/v1/ownership-transfers/" + s.transfer().l("transfer_id");
        assertThat(api.post(base + "/reject", env.token, m("reason", "x")).status()).isEqualTo(422);
        Resp r = api.post(base + "/reject", env.token, m("reason", "Documents incomplete"));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.s("status")).isEqualTo("REJECTED");
        assertThat(r.isNull("approved_at")).isTrue();
        JsonNode owners = api.get("/api/v1/vehicles/" + s.vehicle().l("vehicle_id") + "/owners", env.token).json();
        assertThat(owners).hasSize(1);
        assertThat(owners.get(0).get("citizen_id").asLong()).isEqualTo(s.seller().getCitizenId());
        assertThat(owners.get(0).get("is_current").asBoolean()).isTrue();
        assertThat(api.post(base + "/approve", env.token).status()).isEqualTo(409);
        assertThat(api.post(base + "/reject", env.token, m("reason", "again again")).status()).isEqualTo(409);
        Resp again = api.post("/api/v1/ownership-transfers", env.token, m("vehicle_id", s.vehicle().l("vehicle_id"), "to_citizen_id", s.buyer().getCitizenId(), "application_id", s.transfer().l("application_id")));
        assertThat(again.status()).isEqualTo(201);   // a rejected transfer does not block a new request
    }

    @Test
    void transferRequiresDedicatedPermissions() {
        Setup s = setupTransfer();
        String clerk = fx.withPermissions("vehicle.view", "vehicle.transfer");
        String base = "/api/v1/ownership-transfers/" + s.transfer().l("transfer_id");
        assertThat(api.post(base + "/approve", clerk).status()).isEqualTo(403);
        assertThat(api.post(base + "/reject", clerk, m("reason", "no rights")).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/ownership-transfers", env.token, m("vehicle_id", s.vehicle().l("vehicle_id"), "status", "PENDING")).l("total")).isEqualTo(1);
    }

    @Test
    void concurrentApprovalsLeaveExactlyOneCurrentOwner() throws Exception {
        Setup s = setupTransfer();
        long tid = s.transfer().l("transfer_id");
        List<String> results = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Thread t = new Thread(() -> {
                try {
                    CurrentUser user = loader.load(env.user.getUserId());
                    start.await();
                    ownership.approve(tid, null, user);
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
        assertThat(results.stream().filter("ok"::equals).count()).isEqualTo(1);
        assertThat(results.stream().filter("INVALID_TRANSITION"::equals).count()).isEqualTo(5);
        List<Map<String, Object>> rows = fx.jdbc.queryForList("SELECT citizen_id, effective_to FROM vehicle_ownerships WHERE vehicle_id = ?", s.vehicle().l("vehicle_id"));
        assertThat(rows).hasSize(2);
        List<Map<String, Object>> current = rows.stream().filter(r -> r.get("effective_to") == null).toList();
        assertThat(current).hasSize(1);
        assertThat(((Number) current.get(0).get("citizen_id")).longValue()).isEqualTo(s.buyer().getCitizenId());
    }
}
