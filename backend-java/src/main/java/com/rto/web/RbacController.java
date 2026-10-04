package com.rto.web;

import com.rto.core.CurrentUser;
import com.rto.core.PageParams;
import com.rto.core.PageResponse;
import com.rto.core.Requires;
import com.rto.domain.Permission;
import com.rto.dto.RbacDto.*;
import com.rto.service.RbacService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "rbac & users")
public class RbacController {
    private final RbacService svc;

    public RbacController(RbacService svc) {
        this.svc = svc;
    }

    @GetMapping("/permissions")
    @Requires("rbac.manage")
    @Operation(summary = "List all permission keys")
    public List<Permission> permissions() {
        return svc.permissions();
    }

    @GetMapping("/roles")
    @Requires("rbac.manage")
    @Operation(summary = "List roles with their permissions")
    public List<RoleDetail> roles() {
        return svc.roles();
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("rbac.manage")
    @Operation(summary = "Create a role")
    public RoleDetail createRole(@Valid @RequestBody RoleCreate body, CurrentUser actor) {
        return svc.createRole(body, actor);
    }

    @GetMapping("/roles/{roleId}")
    @Requires("rbac.manage")
    public RoleDetail role(@PathVariable long roleId) {
        return svc.role(roleId);
    }

    @PutMapping("/roles/{roleId}/permissions")
    @Requires("rbac.manage")
    @Operation(summary = "Replace the permission set of a role (audited)")
    public RoleDetail setPermissions(@PathVariable long roleId, @Valid @RequestBody RolePermissionsSet body, CurrentUser actor) {
        return svc.setRolePermissions(roleId, body.permissionKeys(), actor);
    }

    @GetMapping("/users")
    @Requires("user.view")
    @Operation(summary = "List users")
    public PageResponse<UserOut> users(@RequestParam(name = "is_active", required = false) Boolean isActive, PageParams p) {
        return svc.users(p, isActive);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires("user.manage")
    @Operation(summary = "Create a login for an existing person")
    public UserOut createUser(@Valid @RequestBody UserCreate body, CurrentUser actor) {
        return svc.createUser(body, actor);
    }

    @GetMapping("/users/{userId}")
    @Requires("user.view")
    public UserOut user(@PathVariable long userId) {
        return svc.user(userId);
    }

    @PatchMapping("/users/{userId}")
    @Requires("user.manage")
    @Operation(summary = "Activate/deactivate or reset password")
    public UserOut updateUser(@PathVariable long userId, @Valid @RequestBody UserUpdate body, CurrentUser actor) {
        return svc.updateUser(userId, body, actor);
    }

    @PutMapping("/users/{userId}/roles")
    @Requires("rbac.manage")
    @Operation(summary = "Replace a user's roles (audited)")
    public UserOut setRoles(@PathVariable long userId, @Valid @RequestBody UserRolesSet body, CurrentUser actor) {
        return svc.setUserRoles(userId, body.roleIds(), actor);
    }
}
