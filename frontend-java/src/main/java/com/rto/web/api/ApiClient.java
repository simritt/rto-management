package com.rto.web.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin JSON client for the backend REST API. Attaches the session's bearer token and transparently
 * refreshes it once on a 401. All payloads are plain JsonNode / Map (snake_case, as the API speaks).
 */
@Component
public class ApiClient {
    private final String baseUrl;
    private final RestClient http = RestClient.create();
    private final UserSession session;
    private final ObjectMapper mapper;

    public ApiClient(@Value("${rto.api-base-url}") String baseUrl, UserSession session, ObjectMapper mapper) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.session = session;
        this.mapper = mapper;
    }

    public JsonNode get(String path) {
        return get(path, Map.of());
    }

    public JsonNode get(String path, Map<String, ?> query) {
        return call(HttpMethod.GET, path, query, null, true);
    }

    public JsonNode post(String path, Object body) {
        return call(HttpMethod.POST, path, Map.of(), body, true);
    }

    public JsonNode patch(String path, Object body) {
        return call(HttpMethod.PATCH, path, Map.of(), body, true);
    }

    public JsonNode put(String path, Object body) {
        return call(HttpMethod.PUT, path, Map.of(), body, true);
    }

    public JsonNode delete(String path) {
        return call(HttpMethod.DELETE, path, Map.of(), null, true);
    }

    /** Username/password login; stores tokens and the /auth/me profile in the session. */
    public void login(String username, String password) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("password", password);
        JsonNode tokens = call(HttpMethod.POST, "/auth/login", Map.of(), body, false);
        session.setTokens(tokens.path("access_token").asText(), tokens.path("refresh_token").asText());
        session.setMe(call(HttpMethod.GET, "/auth/me", Map.of(), null, true));
    }

    public void logout() {
        try {
            if (session.isLoggedIn()) {
                Map<String, String> body = new LinkedHashMap<>();
                body.put("refresh_token", session.getRefreshToken());
                call(HttpMethod.POST, "/auth/logout", Map.of(), body, true);
            }
        } catch (RuntimeException ignored) {
            // best effort: the local session is cleared regardless
        } finally {
            session.clear();
        }
    }

    private JsonNode call(HttpMethod method, String path, Map<String, ?> query, Object body, boolean auth) {
        try {
            return send(method, path, query, body, auth);
        } catch (ApiException e) {
            if (e.status() == 401 && auth && session.getRefreshToken() != null && refresh()) {
                return send(method, path, query, body, true);
            }
            if (e.status() == 401 && auth) {
                session.clear();
            }
            throw e;
        }
    }

    private boolean refresh() {
        try {
            Map<String, String> body = new LinkedHashMap<>();
            body.put("refresh_token", session.getRefreshToken());
            JsonNode t = send(HttpMethod.POST, "/auth/refresh", Map.of(), body, false);
            session.setTokens(t.path("access_token").asText(), t.path("refresh_token").asText());
            return true;
        } catch (ApiException e) {
            session.clear();
            return false;
        }
    }

    private JsonNode send(HttpMethod method, String path, Map<String, ?> query, Object body, boolean auth) {
        UriComponentsBuilder ub = UriComponentsBuilder.fromUriString(baseUrl + path);
        query.forEach((k, v) -> {
            if (v != null && !v.toString().isBlank()) {
                ub.queryParam(k, v);
            }
        });
        URI uri = ub.build().encode().toUri();

        RestClient.RequestBodySpec spec = http.method(method).uri(uri).accept(MediaType.APPLICATION_JSON);
        if (auth && session.getAccessToken() != null) {
            spec.headers(h -> h.setBearerAuth(session.getAccessToken()));
        }
        if (body != null) {
            spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }
        return spec.exchange((req, res) -> {
            byte[] raw = res.getBody().readAllBytes();
            JsonNode json = raw.length == 0 ? NullNode.getInstance() : mapper.readTree(raw);
            if (res.getStatusCode().isError()) {
                throw new ApiException(res.getStatusCode().value(), json.path("code").asText(null),
                        json.path("detail").asText("Request failed (" + res.getStatusCode().value() + ")"),
                        json.get("errors"));
            }
            return json;
        });
    }
}
