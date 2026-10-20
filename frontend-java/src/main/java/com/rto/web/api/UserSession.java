package com.rto.web.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.util.HashSet;
import java.util.Set;

/** Per-browser-session login state: JWT pair and the /auth/me profile. */
@Component
@SessionScope
public class UserSession {
    private String accessToken;
    private String refreshToken;
    private JsonNode me;
    private Set<String> permissions = Set.of();

    public boolean isLoggedIn() {
        return accessToken != null;
    }

    public void setTokens(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public void setMe(JsonNode me) {
        this.me = me;
        Set<String> p = new HashSet<>();
        if (me != null && me.has("permissions")) {
            me.get("permissions").forEach(n -> p.add(n.asText()));
        }
        this.permissions = p;
    }

    public void clear() {
        accessToken = null;
        refreshToken = null;
        me = null;
        permissions = Set.of();
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public JsonNode getMe() {
        return me;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public String getUsername() {
        return me == null ? "" : me.path("username").asText("");
    }

    public String getDisplayName() {
        if (me == null) {
            return "";
        }
        String first = me.path("person").path("first_name").asText("");
        return first.isBlank() ? getUsername() : first;
    }

    public java.util.List<String> getRoles() {
        java.util.List<String> roles = new java.util.ArrayList<>();
        if (me != null) {
            me.path("roles").forEach(n -> roles.add(n.asText()));
        }
        return roles;
    }

    public boolean can(String permission) {
        return permissions.contains(permission);
    }
}
