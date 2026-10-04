package com.rto.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.support.Api.Resp;
import com.rto.support.BaseIT;
import com.rto.support.Fx;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.rto.support.Api.m;
import static org.assertj.core.api.Assertions.assertThat;

class ReferenceTests extends BaseIT {

    @Test
    void regionsHierarchyAnyDepthAndCyclePrevention() {
        String h = fx.admin();
        Map<String, Resp> made = new java.util.LinkedHashMap<>();
        java.util.function.BiFunction<String, Long, Resp> mk = (name, parent) ->
                api.post("/api/v1/regions", h, m("region_name", name, "region_code", "R" + Fx.u(7), "parent_region_id", parent));
        Resp state = mk.apply("State", null);
        Resp zone = mk.apply("Zone", state.l("region_id"));
        Resp district = mk.apply("District", zone.l("region_id"));
        Resp village = mk.apply("Village", district.l("region_id"));
        JsonNode tree = api.get("/api/v1/regions/tree", h, m("root_id", state.l("region_id"))).json();
        assertThat(tree.at("/0/children/0/children/0/children/0/region_id").asLong()).isEqualTo(village.l("region_id"));
        Resp cyc = api.patch("/api/v1/regions/" + state.l("region_id"), h, m("parent_region_id", village.l("region_id")));
        assertThat(cyc.status()).isEqualTo(409);
        assertThat(cyc.code()).isEqualTo("REGION_CYCLE");
        assertThat(api.patch("/api/v1/regions/" + state.l("region_id"), h, m("parent_region_id", state.l("region_id"))).status()).isEqualTo(409);
        assertThat(api.post("/api/v1/regions", h, m("region_name", "x", "region_code", "R" + Fx.u(7), "parent_region_id", 99999999)).status()).isEqualTo(404);
        assertThat(api.delete("/api/v1/regions/" + state.l("region_id"), h).status()).isEqualTo(409);   // has children (FK RESTRICT)
        assertThat(api.delete("/api/v1/regions/" + village.l("region_id"), h).status()).isEqualTo(204);
    }

    @Test
    void regionCodeIsUnique() {
        String h = fx.admin();
        String code = "U" + Fx.u(7);
        assertThat(api.post("/api/v1/regions", h, m("region_name", "a", "region_code", code)).status()).isEqualTo(201);
        Resp r = api.post("/api/v1/regions", h, m("region_name", "b", "region_code", code));
        assertThat(r.status()).isEqualTo(409);
        assertThat(r.code()).isEqualTo("DUPLICATE");
    }

    @Test
    void vehicleModelNamesAreUniquePerManufacturer() {
        String h = fx.admin();
        Resp m1 = api.post("/api/v1/vehicle-manufacturers", h, m("name", "Maker" + Fx.u(6)));
        Resp m2 = api.post("/api/v1/vehicle-manufacturers", h, m("name", "Maker" + Fx.u(6)));
        Map<String, Object> body = m("manufacturer_id", m1.l("manufacturer_id"), "model_name", "Zeta");
        assertThat(api.post("/api/v1/vehicle-models", h, body).status()).isEqualTo(201);
        assertThat(api.post("/api/v1/vehicle-models", h, body).status()).isEqualTo(409);
        assertThat(api.post("/api/v1/vehicle-models", h, m("manufacturer_id", m2.l("manufacturer_id"), "model_name", "Zeta")).status()).isEqualTo(201);
        assertThat(api.post("/api/v1/vehicle-models", h, m("manufacturer_id", 99999999, "model_name", "Zeta")).status()).isEqualTo(400);
        assertThat(api.get("/api/v1/vehicle-models", h, m("manufacturer_id", m1.l("manufacturer_id"))).l("total")).isEqualTo(1);
        assertThat(api.delete("/api/v1/vehicle-manufacturers/" + m1.l("manufacturer_id"), h).status()).isEqualTo(409);
    }

    @Test
    void serviceTypeFeeIsExactDecimalAndCodeIsUnique() {
        String h = fx.admin();
        String code = "S" + Fx.u(8);
        Resp r = api.post("/api/v1/service-types", h, m("service_name", "Svc", "service_code", code, "base_fee", "250.50", "sla_days", 5));
        assertThat(r.status()).isEqualTo(201);
        assertThat(new BigDecimal(r.s("base_fee"))).isEqualByComparingTo("250.50");
        assertThat(api.post("/api/v1/service-types", h, m("service_name", "Svc2", "service_code", code)).status()).isEqualTo(409);
        assertThat(api.post("/api/v1/service-types", h, m("service_name", "x", "service_code", "N" + Fx.u(6), "base_fee", "-1")).status()).isEqualTo(422);
        assertThat(api.post("/api/v1/service-types", h, m("service_name", "x", "service_code", "M" + Fx.u(6), "base_fee", "1.234")).status()).isEqualTo(422);
        Resp upd = api.patch("/api/v1/service-types/" + r.l("service_type_id"), h, m("base_fee", "300.00"));
        assertThat(new BigDecimal(upd.s("base_fee"))).isEqualByComparingTo("300.00");
    }

    @Test
    void violationAndPermitTypes() {
        String h = fx.admin();
        Resp v = api.post("/api/v1/violation-types", h, m("description", "V" + Fx.u(8), "base_fine_amount", "1500.00", "is_cognizable", true));
        assertThat(v.status()).isEqualTo(201);
        assertThat(v.b("is_cognizable")).isTrue();
        assertThat(api.post("/api/v1/violation-types", h, m("description", "V" + Fx.u(8), "base_fine_amount", "0")).status()).isEqualTo(422);
        Resp p = api.post("/api/v1/permit-types", h, m("type_name", "P" + Fx.u(8), "validity_months", 60));
        assertThat(p.status()).isEqualTo(201);
        assertThat(p.i("validity_months")).isEqualTo(60);
        assertThat(api.post("/api/v1/permit-types", h, m("type_name", "P" + Fx.u(8), "validity_months", 0)).status()).isEqualTo(422);
    }

    @Test
    void simpleLookupTablesAndNullableValidityOnDocumentTypes() {
        String h = fx.admin();
        List<Map.Entry<String, Map<String, Object>>> cases = List.of(
                Map.entry("fuel-types", m("fuel_name", "F" + Fx.u(8))),
                Map.entry("vehicle-types", m("type_name", "T" + Fx.u(8), "is_commercial", true)),
                Map.entry("licence-classes", m("class_code", "L" + Fx.u(6), "description", "d")));
        for (var c : cases) {
            assertThat(api.post("/api/v1/" + c.getKey(), h, c.getValue()).status()).as(c.getKey()).isEqualTo(201);
            assertThat(api.post("/api/v1/" + c.getKey(), h, c.getValue()).status()).as(c.getKey() + " duplicate").isEqualTo(409);
        }
        Resp d = api.post("/api/v1/document-types", h, m("type_name", "D" + Fx.u(8), "validity_period_days", 30));
        Resp r = api.patch("/api/v1/document-types/" + d.l("document_type_id"), h, m("validity_period_days", null));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.isNull("validity_period_days")).isTrue();
    }

    @Test
    void referenceReadsAreOpenWritesRestrictedAndPayableTypesAreReadOnly() {
        String low = fx.withPermissions();
        assertThat(api.get("/api/v1/service-types", low).status()).isEqualTo(200);
        assertThat(api.post("/api/v1/fuel-types", low, m("fuel_name", "x" + Fx.u(5))).status()).isEqualTo(403);
        assertThat(api.get("/api/v1/service-types", null).status()).isEqualTo(401);
        List<String> names = new java.util.ArrayList<>();
        api.get("/api/v1/payable-types", low).json().forEach(p -> names.add(p.get("type_name").asText()));
        assertThat(names).contains("APPLICATION", "CHALLAN", "PERMIT", "ROAD_TAX");
        assertThat(api.post("/api/v1/payable-types", low, m("type_name", "X")).status()).isEqualTo(405);
    }
}
