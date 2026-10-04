package com.rto.it;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.domain.Citizen;
import com.rto.support.Api.Resp;
import com.rto.support.BaseIT;
import com.rto.support.Fx;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static com.rto.support.Api.m;
import static org.assertj.core.api.Assertions.assertThat;

class CitizenTests extends BaseIT {

    static Map<String, Object> citizenBody(Object... over) {
        Map<String, Object> b = m("first_name", "Asha", "last_name", "Rao" + Fx.u(4), "date_of_birth", "1992-03-04", "gender", "FEMALE",
                "national_id_number", "NID" + Fx.u(10), "phone_primary", "9" + String.format("%09d", ThreadLocalRandom.current().nextInt(1_000_000_000)));
        for (int i = 0; i < over.length; i += 2) b.put((String) over[i], over[i + 1]);
        return b;
    }

    @Test
    void createCitizenWithAddressAndGeneratedCode() {
        String h = fx.admin();
        Map<String, Object> body = citizenBody("address", m("line1", "1 MG Road", "city", "Pune", "state", "MH", "pincode", "411001", "valid_from", "2020-01-01"));
        Resp r = api.post("/api/v1/citizens", h, body);
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
        assertThat(r.s("citizen_code")).startsWith("CIT-");
        assertThat(r.at("current_address/city").asText()).isEqualTo("Pune");
        assertThat(r.s("national_id_number")).isEqualTo(body.get("national_id_number"));   // admin holds citizen.view_sensitive
    }

    @Test
    void duplicateNationalIdAndCodeConflict() {
        String h = fx.admin();
        Map<String, Object> body = citizenBody("citizen_code", "DUP" + Fx.u(6));
        assertThat(api.post("/api/v1/citizens", h, body).status()).isEqualTo(201);
        Resp again = api.post("/api/v1/citizens", h, citizenBody("national_id_number", body.get("national_id_number")));
        assertThat(again.status()).isEqualTo(409);
        assertThat(again.code()).isEqualTo("DUPLICATE");
        assertThat(api.post("/api/v1/citizens", h, citizenBody("citizen_code", body.get("citizen_code"))).status()).isEqualTo(409);
    }

    @Test
    void validationErrorsAre422() {
        Resp r = api.post("/api/v1/citizens", fx.admin(), citizenBody("phone_primary", "abc", "date_of_birth", "2999-01-01"));
        assertThat(r.status()).isEqualTo(422);
        Set<String> fields = new HashSet<>();
        r.json().get("errors").forEach(e -> fields.add(e.get("field").asText()));
        assertThat(fields).contains("phone_primary", "date_of_birth");
    }

    @Test
    void nationalIdHiddenFromListsAndUnprivilegedUsers() {
        String h = fx.admin();
        Map<String, Object> body = citizenBody();
        long cid = api.post("/api/v1/citizens", h, body).l("citizen_id");
        String clerk = fx.withPermissions("citizen.view");
        Resp lst = api.get("/api/v1/citizens", clerk, m("search", body.get("last_name")));
        assertThat(lst.status()).isEqualTo(200);
        assertThat(lst.l("total")).isEqualTo(1);
        assertThat(new String(lst.raw())).doesNotContain((String) body.get("national_id_number")).doesNotContain("national_id");
        assertThat(api.get("/api/v1/citizens/" + cid, clerk).isNull("national_id_number")).isTrue();
        assertThat(api.get("/api/v1/citizens", clerk, m("national_id", body.get("national_id_number"))).status()).isEqualTo(403);
        Resp found = api.get("/api/v1/citizens", h, m("national_id", body.get("national_id_number")));
        assertThat(found.json().get("items")).hasSize(1);
        assertThat(found.at("items/0/citizen_id").asLong()).isEqualTo(cid);
    }

    @Test
    void searchByCodeNamePhoneAndPaginationShape() {
        String h = fx.admin();
        Map<String, Object> body = citizenBody("first_name", "Zebulon");
        Resp created = api.post("/api/v1/citizens", h, body);
        for (Object term : List.of(created.s("citizen_code"), "Zebulon", body.get("phone_primary"))) {
            Resp r = api.get("/api/v1/citizens", h, m("search", term));
            List<Long> ids = new java.util.ArrayList<>();
            r.json().get("items").forEach(i -> ids.add(i.get("citizen_id").asLong()));
            assertThat(ids).as("search " + term).contains(created.l("citizen_id"));
        }
        Resp page = api.get("/api/v1/citizens", h, m("page_size", 1));
        Set<String> keys = new HashSet<>();
        page.json().fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactlyInAnyOrder("items", "page", "page_size", "total", "pages");
        assertThat(page.json().get("items")).hasSize(1);
        assertThat(api.get("/api/v1/citizens", h, m("page_size", 5000)).status()).isEqualTo(422);
        assertThat(api.get("/api/v1/citizens", h, m("sort", "password; DROP")).status()).isEqualTo(400);
    }

    @Test
    void citizenWithoutPermissionIsForbiddenButCanReadSelf() {
        Citizen cit = fx.makeCitizen();
        String h = fx.citizenToken(cit);
        assertThat(api.get("/api/v1/citizens", h).status()).isEqualTo(403);
        Resp me = api.get("/api/v1/citizens/me", h);
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.l("citizen_id")).isEqualTo(cit.getCitizenId());
        Citizen other = fx.makeCitizen();
        assertThat(api.get("/api/v1/citizens/" + other.getCitizenId(), h).status()).isEqualTo(403);
    }

    @Test
    void addressHistoryIsPreserved() {
        String h = fx.admin();
        long cid = api.post("/api/v1/citizens", h, citizenBody()).l("citizen_id");
        String url = "/api/v1/citizens/" + cid + "/addresses";
        Map<String, Object> base = m("line1", "L", "city", "Pune", "state", "MH", "pincode", "411001");
        Resp r1 = api.post(url, h, merge(base, "valid_from", "2018-01-01"));
        Resp r2 = api.post(url, h, merge(base, "city", "Mumbai", "valid_from", "2021-06-01"));
        assertThat(r1.status()).isEqualTo(201);
        assertThat(r2.status()).isEqualTo(201);
        JsonNode hist = api.get(url, h).json();
        assertThat(hist.get(0).get("city").asText()).isEqualTo("Mumbai");
        assertThat(hist.get(1).get("city").asText()).isEqualTo("Pune");
        assertThat(hist.get(0).get("is_current").asBoolean()).isTrue();
        assertThat(hist.get(1).get("is_current").asBoolean()).isFalse();
        assertThat(hist.get(1).get("valid_to").asText()).isEqualTo("2021-06-01");   // old row closed, not overwritten or deleted
        assertThat(api.get(url, h, m("current_only", true)).json().get(0).get("city").asText()).isEqualTo("Mumbai");
        assertThat(api.get("/api/v1/citizens/" + cid, h).at("current_address/city").asText()).isEqualTo("Mumbai");
        Resp back = api.post(url, h, merge(base, "valid_from", "2019-01-01"));
        assertThat(back.status()).isEqualTo(409);
        assertThat(back.code()).isEqualTo("ADDRESS_CHRONOLOGY");
        Resp old = api.post(url, h, merge(base, "valid_from", "2010-01-01", "valid_to", "2011-01-01"));
        assertThat(old.status()).isEqualTo(201);
        assertThat(old.b("is_current")).isFalse();
    }

    private static Map<String, Object> merge(Map<String, Object> base, Object... kv) {
        Map<String, Object> r = new java.util.LinkedHashMap<>(base);
        for (int i = 0; i < kv.length; i += 2) r.put((String) kv[i], kv[i + 1]);
        return r;
    }

    @Test
    void updateBlacklistIsAuditedAndRequiresReason() {
        String h = fx.admin();
        long cid = api.post("/api/v1/citizens", h, citizenBody()).l("citizen_id");
        String url = "/api/v1/citizens/" + cid;
        assertThat(api.patch(url, h, m("blacklisted", true)).status()).isEqualTo(422);
        Resp r = api.patch(url, h, m("blacklisted", true, "blacklist_reason", "fraud", "email", "a@b.co"));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.b("blacklisted")).isTrue();
        assertThat(r.s("email")).isEqualTo("a@b.co");
        String newValues = fx.jdbc.queryForObject("SELECT new_values FROM audit_logs WHERE table_name = 'citizens' AND record_id = ? AND action = 'UPDATE'", String.class, cid);
        assertThat(newValues).contains("\"blacklisted\"").contains("true");
        Resp off = api.patch(url, h, m("blacklisted", false));
        assertThat(off.b("blacklisted")).isFalse();
        assertThat(off.isNull("blacklist_reason")).isTrue();
    }

    @Test
    void changingNationalIdChecksConflictsAndIsNeverAuditedInClear() {
        String h = fx.admin();
        Resp a = api.post("/api/v1/citizens", h, citizenBody());
        Map<String, Object> bBody = citizenBody();
        api.post("/api/v1/citizens", h, bBody);
        long aid = a.l("citizen_id");
        assertThat(api.patch("/api/v1/citizens/" + aid, h, m("national_id_number", bBody.get("national_id_number"))).status()).isEqualTo(409);
        String newId = "NEW" + Fx.u(10);
        assertThat(api.patch("/api/v1/citizens/" + aid, h, m("national_id_number", newId)).status()).isEqualTo(200);
        List<String> logs = fx.jdbc.queryForList("SELECT CONCAT(IFNULL(CAST(old_values AS CHAR), ''), IFNULL(CAST(new_values AS CHAR), '')) FROM audit_logs "
                + "WHERE table_name = 'citizens' AND record_id = ?", String.class, aid);
        assertThat(String.join("", logs)).doesNotContain(newId).doesNotContain(a.s("national_id_number"));
        String clerk = fx.withPermissions("citizen.update");
        assertThat(api.patch("/api/v1/citizens/" + aid, clerk, m("national_id_number", "X" + Fx.u(9))).status()).isEqualTo(403);
    }
}
