package com.rto.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.LinkedHashMap;
import java.util.Map;

/** Thin HTTP client over MockMvc: real filter chain + real controllers + real MySQL, JSON in / JSON out. */
@Component
public class Api {
    private final MockMvc mvc;
    private final ObjectMapper mapper;

    @Autowired
    public Api(MockMvc mvc, ObjectMapper mapper) {
        this.mvc = mvc;
        this.mapper = mapper;
    }

    /** Null-tolerant map literal for request bodies / query params: m("a", 1, "b", null). */
    public static Map<String, Object> m(Object... kv) {
        Map<String, Object> r = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) r.put((String) kv[i], kv[i + 1]);
        return r;
    }

    public record Resp(int status, JsonNode json, byte[] raw) {
        public String s(String field) { JsonNode n = json.get(field); return n == null || n.isNull() ? null : n.asText(); }
        public long l(String field) { return json.get(field).asLong(); }
        public int i(String field) { return json.get(field).asInt(); }
        public boolean b(String field) { return json.get(field).asBoolean(); }
        public boolean isNull(String field) { return !json.has(field) || json.get(field).isNull(); }
        public String code() { return s("code"); }
        public java.util.List<String> strs(String field) {
            java.util.List<String> out = new java.util.ArrayList<>();
            json.get(field).forEach(n -> out.add(n.asText()));
            return out;
        }
        public int size(String field) { return json.get(field).size(); }
        public JsonNode at(String ptr) { return json.at(ptr.startsWith("/") ? ptr : "/" + ptr); }
        @Override public String toString() { return status + " " + json; }
    }

    private Resp run(MockHttpServletRequestBuilder b, String token) {
        try {
            if (token != null) b.header("Authorization", "Bearer " + token);
            MvcResult r = mvc.perform(b).andReturn();
            byte[] raw = r.getResponse().getContentAsByteArray();
            JsonNode json = null;
            String ct = r.getResponse().getContentType();
            if (raw.length > 0 && ct != null && ct.contains("json")) json = mapper.readTree(raw);
            return new Resp(r.getResponse().getStatus(), json == null ? mapper.nullNode() : json, raw);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private MockHttpServletRequestBuilder withQuery(MockHttpServletRequestBuilder b, Map<String, Object> params) {
        if (params != null) params.forEach((k, v) -> { if (v != null) b.param(k, String.valueOf(v)); });
        return b;
    }

    private MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder b, Object body) {
        try {
            if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
            return b;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Resp get(String path, String token) { return run(MockMvcRequestBuilders.get(path), token); }
    public Resp get(String path, String token, Map<String, Object> params) { return run(withQuery(MockMvcRequestBuilders.get(path), params), token); }
    public Resp post(String path, String token, Object body) { return run(withBody(MockMvcRequestBuilders.post(path), body), token); }
    public Resp post(String path, String token) { return run(MockMvcRequestBuilders.post(path), token); }
    public Resp post(String path, String token, Object body, Map<String, Object> params) { return run(withQuery(withBody(MockMvcRequestBuilders.post(path), body), params), token); }
    public Resp patch(String path, String token, Object body) { return run(withBody(MockMvcRequestBuilders.patch(path), body), token); }
    public Resp patch(String path, String token) { return run(MockMvcRequestBuilders.patch(path), token); }
    public Resp put(String path, String token, Object body) { return run(withBody(MockMvcRequestBuilders.put(path), body), token); }
    public Resp delete(String path, String token) { return run(MockMvcRequestBuilders.delete(path), token); }
    public Resp request(HttpMethod method, String path, String token) { return run(MockMvcRequestBuilders.request(method, path), token); }

    public Resp options(String path, Map<String, String> headers) {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.options(path);
        headers.forEach(b::header);
        return run(b, null);
    }

    /** Raw Authorization header value (for tampered/garbage tokens). */
    public Resp getWithAuthorization(String path, String authorization) {
        return run(MockMvcRequestBuilders.get(path).header("Authorization", authorization), null);
    }

    public Resp upload(String path, String token, Map<String, String> fields, String filename, byte[] content) {
        var b = MockMvcRequestBuilders.multipart(path).file(new MockMultipartFile("file", filename, "application/octet-stream", content));
        fields.forEach(b::param);
        return run(b, token);
    }
}
