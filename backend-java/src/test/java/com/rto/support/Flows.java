package com.rto.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.rto.core.Clock;
import com.rto.domain.*;
import com.rto.support.Api.Resp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.rto.support.Api.m;
import static org.assertj.core.api.Assertions.assertThat;

/** Reusable end-to-end builders: an office with staff, a citizen, and applications driven through the real API. */
@Component
public class Flows {
    public static final byte[] PNG = concat(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'}, "0".repeat(64).getBytes());
    public static final byte[] PDF = concat("%PDF-1.4\n".getBytes(), "0".repeat(64).getBytes());

    private final Fx fx;
    private final Api api;

    @Autowired
    public Flows(Fx fx, Api api) {
        this.fx = fx;
        this.api = api;
    }

    static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    /** Mutable on purpose: tests swap `service`, `citizen`, `docType` to model different scenarios. */
    public static class Env {
        public RtoOffice office;
        public String token;
        public Employee emp;
        public User user;
        public ServiceType service;
        public Citizen citizen;
        public DocumentType docType;

        public Env with(Citizen c) {
            Env e = copy();
            e.citizen = c;
            return e;
        }

        public Env with(ServiceType s) {
            Env e = copy();
            e.service = s;
            return e;
        }

        public Env with(DocumentType d) {
            Env e = copy();
            e.docType = d;
            return e;
        }

        Env copy() {
            Env e = new Env();
            e.office = office; e.token = token; e.emp = emp; e.user = user; e.service = service; e.citizen = citizen; e.docType = docType;
            return e;
        }
    }

    public Env makeEnv() {
        return makeEnv("0.00");
    }

    public Env makeEnv(String fee) {
        Env e = new Env();
        e.office = fx.makeOffice();
        Fx.Staff s = fx.makeStaff(e.office);
        e.token = s.token();
        e.emp = s.emp();
        e.user = s.user();
        e.service = fx.makeServiceType(fee, 7);
        e.citizen = fx.makeCitizen();
        e.docType = fx.makeDocumentType(null);
        return e;
    }

    public Resp newAppResp(Env env, Map<String, Object> over) {
        Map<String, Object> body = m("citizen_id", env.citizen.getCitizenId(), "service_type_id", env.service.getServiceTypeId(), "office_id", env.office.getOfficeId());
        body.putAll(over);
        return api.post("/api/v1/applications", env.token, body);
    }

    public JsonNode newApp(Env env) {
        return newApp(env, Map.of());
    }

    public JsonNode newApp(Env env, Map<String, Object> over) {
        Resp r = newAppResp(env, over);
        assertThat(r.status()).as(r.toString()).isEqualTo(201);
        return r.json();
    }

    public Resp setStatus(Env env, long appId, String to, String reason) {
        return setStatus(env.token, appId, to, reason);
    }

    public Resp setStatus(String token, long appId, String to, String reason) {
        return api.post("/api/v1/applications/" + appId + "/status", token, m("status", to, "reason", reason));
    }

    public Resp upload(Env env, long appId, byte[] content, String name, String token) {
        return api.upload("/api/v1/applications/" + appId + "/documents", token != null ? token : env.token,
                Map.of("document_type_id", String.valueOf(env.docType.getDocumentTypeId())), name, content);
    }

    public Resp upload(Env env, long appId) {
        return upload(env, appId, PNG, "id.png", null);
    }

    public JsonNode underVerification(Env env) {
        return underVerification(env, Map.of());
    }

    public JsonNode underVerification(Env env, Map<String, Object> over) {
        JsonNode a = newApp(env, over);
        assertThat(setStatus(env, a.get("application_id").asLong(), "UNDER_VERIFICATION", null).status()).isEqualTo(200);
        return a;
    }

    public JsonNode verifiedApp(Env env) {
        return verifiedApp(env, Map.of());
    }

    public JsonNode verifiedApp(Env env, Map<String, Object> over) {
        JsonNode a = underVerification(env, over);
        Resp d = upload(env, a.get("application_id").asLong());
        assertThat(d.status()).as(d.toString()).isEqualTo(201);
        assertThat(api.patch("/api/v1/documents/" + d.l("document_id") + "/verify", env.token).status()).isEqualTo(200);
        return a;
    }

    /** A zero-fee application taken all the way to APPROVED. */
    public JsonNode approvedApp(Env env) {
        return approvedApp(env, Map.of());
    }

    public JsonNode approvedApp(Env env, Map<String, Object> over) {
        JsonNode a = verifiedApp(env, over);
        Resp r = null;
        for (String next : new String[]{"AWAITING_PAYMENT", "APPROVED"}) {
            r = setStatus(env, a.get("application_id").asLong(), next, null);
            assertThat(r.status()).as(r.toString()).isEqualTo(200);
        }
        return r.json();
    }

    public String ref() {
        return "GW-" + Fx.u(12);
    }

    public String futureIso(int days) {
        return LocalDateTime.now(java.time.ZoneOffset.UTC).plusDays(days).withNano(0).toString();
    }

    public Map<String, Object> put(Map<String, Object> base, Object... kv) {
        Map<String, Object> r = new LinkedHashMap<>(base);
        for (int i = 0; i < kv.length; i += 2) r.put((String) kv[i], kv[i + 1]);
        return r;
    }

    public static String today() {
        return Clock.today().toString();
    }
}
