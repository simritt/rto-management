package com.rto.it;

import com.rto.domain.User;
import com.rto.support.Api.Resp;
import com.rto.support.BaseIT;
import com.rto.support.Fx;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.rto.support.Api.m;
import static org.assertj.core.api.Assertions.assertThat;

class RbacTests extends BaseIT {

    private String admin() {
        return fx.admin();
    }

    private long auditRows(String table, long recordId) {
        return fx.count("SELECT COUNT(*) FROM audit_logs WHERE table_name = ? AND record_id = ?", table, recordId);
    }

    @Test
    void unauthenticatedIs401AndUnauthorisedIs403() {
        assertThat(api.get("/api/v1/users", null).status()).isEqualTo(401);
        String low = fx.withPermissions("citizen.view");
        Resp r = api.get("/api/v1/users", low);
        assertThat(r.status()).isEqualTo(403);
        assertThat(r.code()).isEqualTo("PERMISSION_DENIED");
        assertThat(r.s("detail")).contains("user.view");
    }

    @Test
    void permissionsResolveThroughMultipleRoles() {
        User user = fx.userWithPermissions("user.view");
        fx.grant(user, fx.makeRole(List.of("rbac.manage")));
        String token = fx.login(user);
        assertThat(api.get("/api/v1/users", token).status()).isEqualTo(200);
        assertThat(api.get("/api/v1/roles", token).status()).isEqualTo(200);
        Resp me = api.get("/api/v1/auth/me", token);
        assertThat(me.strs("permissions")).containsExactlyInAnyOrder("user.view", "rbac.manage");
        assertThat(me.size("roles")).isEqualTo(2);
    }

    @Test
    void adminRoleHasEverySeededPermission() {
        String h = admin();
        Resp perms = api.get("/api/v1/permissions", h);
        Resp me = api.get("/api/v1/auth/me", h);
        List<String> keys = new java.util.ArrayList<>();
        perms.json().forEach(p -> keys.add(p.get("permission_key").asText()));
        assertThat(keys).containsExactlyInAnyOrderElementsOf(me.strs("permissions"));
    }

    @Test
    void createRoleAndSetPermissionsIsAudited() {
        String h = admin();
        Resp r = api.post("/api/v1/roles", h, m("role_name", "R" + Fx.u(8), "permission_keys", List.of("citizen.view")));
        assertThat(r.status()).isEqualTo(201);
        assertThat(r.strs("permissions")).containsExactly("citizen.view");
        long rid = r.l("role_id");
        Resp r2 = api.put("/api/v1/roles/" + rid + "/permissions", h, m("permission_keys", List.of("vehicle.view", "vehicle.create")));
        assertThat(r2.status()).isEqualTo(200);
        assertThat(r2.strs("permissions")).containsExactly("vehicle.create", "vehicle.view");
        Map<String, Object> log = fx.jdbc.queryForMap("SELECT action, old_values, new_values FROM audit_logs WHERE table_name = 'role_permissions' AND record_id = ?", rid);
        assertThat(log.get("action")).isEqualTo("UPDATE");
        assertThat(String.valueOf(log.get("old_values"))).contains("citizen.view");
        assertThat(String.valueOf(log.get("new_values"))).contains("vehicle.create").contains("vehicle.view");
    }

    @Test
    void unknownPermissionIsRejected() {
        Resp r = api.post("/api/v1/roles", admin(), m("role_name", "R" + Fx.u(8), "permission_keys", List.of("nope.nope")));
        assertThat(r.status()).isEqualTo(400);
        assertThat(r.code()).isEqualTo("UNKNOWN_PERMISSION");
    }

    @Test
    void duplicateRoleIs409WithoutSqlLeak() {
        String h = admin();
        String name = "R" + Fx.u(8);
        assertThat(api.post("/api/v1/roles", h, m("role_name", name)).status()).isEqualTo(201);
        Resp r = api.post("/api/v1/roles", h, m("role_name", name));
        assertThat(r.status()).isEqualTo(409);
        assertThat(new String(r.raw())).doesNotContain("INSERT").doesNotContain("uq_");
    }

    @Test
    void createUserHidesHashAndAuditsWithoutPassword() {
        String h = admin();
        var person = fx.makePerson();
        Resp r = api.post("/api/v1/users", h, m("person_id", person.getPersonId(), "username", "n_" + Fx.u(8), "password", "Longenough1"));
        assertThat(r.status()).isEqualTo(201);
        assertThat(new String(r.raw()).toLowerCase()).doesNotContain("password");
        String audited = fx.jdbc.queryForObject("SELECT new_values FROM audit_logs WHERE table_name = 'users' AND record_id = ?", String.class, r.l("user_id"));
        assertThat(audited.toLowerCase()).doesNotContain("password");
    }

    @Test
    void weakPasswordIsRejected() {
        var person = fx.makePerson();
        Resp r = api.post("/api/v1/users", admin(), m("person_id", person.getPersonId(), "username", "w_" + Fx.u(8), "password", "short"));
        assertThat(r.status()).isEqualTo(422);
        assertThat(r.code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void duplicateUsernameAndPersonConflict() {
        String h = admin();
        User existing = fx.userWithPermissions();
        var other = fx.makePerson();
        assertThat(api.post("/api/v1/users", h, m("person_id", other.getPersonId(), "username", existing.getUsername(), "password", "Longenough1")).status()).isEqualTo(409);
        assertThat(api.post("/api/v1/users", h, m("person_id", existing.getPersonId(), "username", "z_" + Fx.u(8), "password", "Longenough1")).status()).isEqualTo(409);
    }

    @Test
    void assigningRolesChangesEffectivePermissionsImmediately() {
        String h = admin();
        User target = fx.userWithPermissions();
        String tHeaders = fx.login(target);
        assertThat(api.get("/api/v1/users", tHeaders).status()).isEqualTo(403);
        Resp role = api.post("/api/v1/roles", h, m("role_name", "R" + Fx.u(8), "permission_keys", List.of("user.view")));
        Resp r = api.put("/api/v1/users/" + target.getUserId() + "/roles", h, m("role_ids", List.of(role.l("role_id"))));
        assertThat(r.status()).isEqualTo(200);
        assertThat(r.strs("roles")).containsExactly(role.s("role_name"));
        assertThat(api.get("/api/v1/users", tHeaders).status()).isEqualTo(200);   // permissions are read per request
        assertThat(auditRows("user_roles", target.getUserId())).isGreaterThan(0);
    }

    @Test
    void cannotDeactivateSelfAndPasswordResetWorks() {
        User me = fx.userWithRoles("ADMIN");
        String h = fx.login(me);
        assertThat(api.patch("/api/v1/users/" + me.getUserId(), h, m("is_active", false)).status()).isEqualTo(409);
        User victim = fx.userWithPermissions();
        assertThat(api.patch("/api/v1/users/" + victim.getUserId(), h, m("password", "BrandNewPass9")).status()).isEqualTo(200);
        assertThat(api.post("/api/v1/auth/login", null, m("username", victim.getUsername(), "password", "BrandNewPass9")).status()).isEqualTo(200);
        assertThat(api.post("/api/v1/auth/login", null, m("username", victim.getUsername(), "password", Fx.PASSWORD)).status()).isEqualTo(401);
    }
}
