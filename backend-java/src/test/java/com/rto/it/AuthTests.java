package com.rto.it;

import com.rto.domain.User;
import com.rto.support.Api.Resp;
import com.rto.support.BaseIT;
import com.rto.support.Fx;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static com.rto.support.Api.m;
import static org.assertj.core.api.Assertions.assertThat;

class AuthTests extends BaseIT {

    private Resp login(User user, String password) {
        return api.post("/api/v1/auth/login", null, m("username", user.getUsername(), "password", password));
    }

    @Test
    void loginReturnsTokensAndRecordsLastLogin() {
        User user = fx.userWithPermissions("citizen.view");
        Resp r = login(user, Fx.PASSWORD);
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.s("token_type")).isEqualTo("bearer");
        assertThat(r.s("access_token")).isNotBlank();
        assertThat(r.s("refresh_token")).isNotBlank();
        assertThat(fx.count("SELECT COUNT(*) FROM users WHERE user_id = ? AND last_login_at IS NOT NULL", user.getUserId())).isEqualTo(1);
    }

    @Test
    void invalidPasswordAndUnknownUserAreIndistinguishable() {
        User user = fx.userWithPermissions();
        Resp bad = login(user, "wrong-password");
        Resp ghost = api.post("/api/v1/auth/login", null, m("username", "nobody_here", "password", "x"));
        assertThat(bad.status()).isEqualTo(401).isEqualTo(ghost.status());
        assertThat(bad.code()).isEqualTo("INVALID_CREDENTIALS").isEqualTo(ghost.code());
        assertThat(bad.s("detail")).isEqualTo(ghost.s("detail"));
    }

    @Test
    void inactiveUserCannotLogin() {
        User user = fx.makeUserRaw(java.util.List.of(), java.util.List.of(), null, false, null);
        Resp r = login(user, Fx.PASSWORD);
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.code()).isEqualTo("USER_INACTIVE");
    }

    @Test
    void deactivatedUserLosesAccessWithAnExistingToken() {
        User user = fx.userWithPermissions("citizen.view");
        String token = fx.login(user);
        assertThat(api.get("/api/v1/auth/me", token).status()).isEqualTo(200);
        fx.jdbc.update("UPDATE users SET is_active = 0 WHERE user_id = ?", user.getUserId());
        assertThat(api.get("/api/v1/auth/me", token).status()).isEqualTo(401);
    }

    @Test
    void meRequiresAToken() {
        Resp r = api.get("/api/v1/auth/me", null);
        assertThat(r.status()).isEqualTo(401);
        assertThat(r.code()).isEqualTo("NOT_AUTHENTICATED");
    }

    @Test
    void meReturnsResolvedPermissionsAndNeverPasswordHashOrNationalId() {
        User user = fx.userWithPermissions("citizen.view", "vehicle.view");
        Resp r = api.get("/api/v1/auth/me", fx.login(user));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.s("username")).isEqualTo(user.getUsername());
        assertThat(r.strs("permissions")).containsExactly("citizen.view", "vehicle.view");
        assertThat(new String(r.raw(), StandardCharsets.UTF_8).toLowerCase()).doesNotContain("password").doesNotContain("national_id");
    }

    @Test
    void tamperedAndExpiredTokensAreRejected() {
        User user = fx.userWithPermissions();
        String token = fx.login(user);
        assertThat(api.getWithAuthorization("/api/v1/auth/me", "Bearer " + token + "x").status()).isEqualTo(401);
        String expired = Jwts.builder().subject(String.valueOf(user.getUserId())).claim("typ", "access").id("j1")
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        Resp r = api.getWithAuthorization("/api/v1/auth/me", "Bearer " + expired);
        assertThat(r.status()).isEqualTo(401);
        assertThat(r.code()).isEqualTo("TOKEN_EXPIRED");
    }

    @Test
    void refreshTokenCannotBeUsedAsAnAccessToken() {
        String refresh = login(fx.userWithPermissions(), Fx.PASSWORD).s("refresh_token");
        assertThat(api.getWithAuthorization("/api/v1/auth/me", "Bearer " + refresh).status()).isEqualTo(401);
    }

    @Test
    void refreshRotatesAndTheOldRefreshTokenIsDead() {
        Resp first = login(fx.userWithPermissions(), Fx.PASSWORD);
        Resp next = api.post("/api/v1/auth/refresh", null, m("refresh_token", first.s("refresh_token")));
        assertThat(next.status()).isEqualTo(200);
        assertThat(api.post("/api/v1/auth/refresh", null, m("refresh_token", first.s("refresh_token"))).status()).isEqualTo(401);
        assertThat(api.post("/api/v1/auth/refresh", null, m("refresh_token", next.s("refresh_token"))).status()).isEqualTo(200);
    }

    @Test
    void logoutRevokesTheAccessAndRefreshTokens() {
        Resp tokens = login(fx.userWithPermissions(), Fx.PASSWORD);
        String access = tokens.s("access_token");
        assertThat(api.post("/api/v1/auth/logout", access, m("refresh_token", tokens.s("refresh_token"))).status()).isEqualTo(204);
        assertThat(api.get("/api/v1/auth/me", access).status()).isEqualTo(401);
        assertThat(api.post("/api/v1/auth/refresh", null, m("refresh_token", tokens.s("refresh_token"))).status()).isEqualTo(401);
    }

    @Test
    void passwordIsStoredHashed() {
        User user = fx.userWithPermissions();
        String hash = fx.jdbc.queryForObject("SELECT password_hash FROM users WHERE user_id = ?", String.class, user.getUserId());
        assertThat(hash).isNotEqualTo(Fx.PASSWORD).startsWith("$2");
    }

    @Test
    void healthReportsTheDatabaseAndSwaggerIsServed() {
        Resp h = api.get("/api/v1/health", null);
        assertThat(h.status()).isEqualTo(200);
        assertThat(h.s("status")).isEqualTo("ok");
        assertThat(h.i("tables")).isEqualTo(63);
        assertThat(api.get("/openapi.json", null).status()).isEqualTo(200);
    }

    @Test
    void corsAllowsTheConfiguredFrontendOriginAndErrorsHaveTheUniformShape() {
        Resp pre = api.options("/api/v1/health", java.util.Map.of("Origin", "http://localhost:5173", "Access-Control-Request-Method", "GET"));
        assertThat(pre.status()).isEqualTo(200);
        Resp err = api.getWithAuthorization("/api/v1/applications/abc", "Bearer x");
        assertThat(err.status()).isEqualTo(401);
        assertThat(err.json().has("detail")).isTrue();
        assertThat(err.json().has("code")).isTrue();
        assertThat(api.get("/api/v1/does-not-exist", null).status()).isEqualTo(404);
    }
}
