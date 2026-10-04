package com.rto.service;

import com.rto.core.ApiException;
import com.rto.core.Audit;
import com.rto.core.CurrentUser;
import com.rto.core.Db;
import com.rto.core.PageParams;
import com.rto.core.PageResponse;
import com.rto.core.QB;
import com.rto.domain.Permission;
import com.rto.domain.Person;
import com.rto.domain.Role;
import com.rto.domain.RolePermission;
import com.rto.domain.User;
import com.rto.domain.UserRole;
import com.rto.dto.RbacDto.*;
import com.rto.security.Passwords;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static com.rto.core.Audit.m;

@Service
@Transactional
public class RbacService {
    private final Db db;
    private final Audit audit;
    private final Passwords passwords;

    public RbacService(Db db, Audit audit, Passwords passwords) {
        this.db = db;
        this.audit = audit;
        this.passwords = passwords;
    }

    // ---- roles -------------------------------------------------------------------------------------

    private List<String> roleKeys(long roleId) {
        return db.list(String.class, "select p.permissionKey from RolePermission rp join Permission p "
                + "on p.permissionId = rp.permissionId where rp.roleId = :r order by p.permissionKey", "r", roleId);
    }

    public RoleDetail roleDetail(Role r) {
        return new RoleDetail(r.getRoleId(), r.getRoleName(), roleKeys(r.getRoleId()));
    }

    @Transactional(readOnly = true)
    public List<RoleDetail> roles() {
        return db.list(Role.class, "select r from Role r order by r.roleName").stream().map(this::roleDetail).toList();
    }

    @Transactional(readOnly = true)
    public RoleDetail role(long id) {
        return roleDetail(db.get(Role.class, id, "Role"));
    }

    @Transactional(readOnly = true)
    public List<Permission> permissions() {
        return db.list(Permission.class, "select p from Permission p order by p.permissionKey");
    }

    private List<Permission> resolvePermissions(List<String> keys) {
        List<String> unique = new TreeSet<>(keys).stream().toList();
        List<Permission> perms = unique.isEmpty() ? List.of()
                : db.list(Permission.class, "select p from Permission p where p.permissionKey in :k", "k", unique);
        List<String> missing = unique.stream().filter(k -> perms.stream().noneMatch(p -> p.getPermissionKey().equals(k))).toList();
        if (!missing.isEmpty()) {
            throw ApiException.badRequest("UNKNOWN_PERMISSION", "Unknown permission(s): " + String.join(", ", missing));
        }
        return perms;
    }

    private void grant(long roleId, List<Permission> perms) {
        for (Permission p : perms) {
            RolePermission rp = new RolePermission();
            rp.setRoleId(roleId);
            rp.setPermissionId(p.getPermissionId());
            db.save(rp);
        }
    }

    public RoleDetail createRole(RoleCreate d, CurrentUser actor) {
        List<Permission> perms = resolvePermissions(d.permissionKeys());
        Role role = new Role();
        role.setRoleName(d.roleName());
        db.save(role);
        grant(role.getRoleId(), perms);
        audit.record("roles", role.getRoleId(), "INSERT", null,
                m("role_name", role.getRoleName(), "permissions", new TreeSet<>(d.permissionKeys()).stream().toList()), actor.userId());
        db.flush();
        return roleDetail(role);
    }

    public RoleDetail setRolePermissions(long roleId, List<String> keys, CurrentUser actor) {
        Role role = db.lock(Role.class, roleId, "Role");
        List<Permission> perms = resolvePermissions(keys);
        List<String> old = roleKeys(roleId);
        db.update("delete from RolePermission rp where rp.roleId = :r", "r", roleId);
        db.em().clear();   // bulk delete bypasses the persistence context
        role = db.get(Role.class, roleId, "Role");
        grant(roleId, perms);
        audit.record("role_permissions", roleId, "UPDATE", m("permissions", old),
                m("permissions", new TreeSet<>(keys).stream().toList()), actor.userId());
        db.flush();
        return roleDetail(role);
    }

    // ---- users --------------------------------------------------------------------------------------

    private List<String> userRoles(long userId) {
        return db.list(String.class, "select r.roleName from UserRole ur join Role r on r.roleId = ur.roleId "
                + "where ur.userId = :u order by r.roleName", "u", userId);
    }

    public UserOut userOut(User u) {
        return new UserOut(u.getUserId(), u.getPersonId(), u.getUsername(), u.getIsActive(), u.getLastLoginAt(),
                userRoles(u.getUserId()));
    }

    @Transactional(readOnly = true)
    public UserOut user(long id) {
        return userOut(db.get(User.class, id, "User"));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserOut> users(PageParams p, Boolean isActive) {
        QB q = new QB("u", "User u").search(p.search(), "u.username").eq("u.isActive", isActive);
        return db.page(q, User.class, p, Map.of("username", "u.username", "user_id", "u.userId"), "u.userId", false)
                .map(this::userOut);
    }

    public UserOut createUser(UserCreate d, CurrentUser actor) {
        db.get(Person.class, d.personId(), "Person");
        if (db.exists("select u.userId from User u where u.personId = :p", "p", d.personId())) {
            throw ApiException.conflict("DUPLICATE", "This person already has a user account");
        }
        List<Role> roles = d.roleIds().isEmpty() ? List.of()
                : db.list(Role.class, "select r from Role r where r.roleId in :ids", "ids", d.roleIds());
        if (roles.size() != d.roleIds().stream().distinct().count()) {
            throw ApiException.badRequest("UNKNOWN_ROLE", "One or more roles do not exist");
        }
        User u = new User();
        u.setPersonId(d.personId());
        u.setUsername(d.username());
        u.setPasswordHash(passwords.hash(d.password()));
        u.setIsActive(true);
        db.save(u);
        for (Role r : roles) {
            UserRole ur = new UserRole();
            ur.setUserId(u.getUserId());
            ur.setRoleId(r.getRoleId());
            db.save(ur);
        }
        audit.record("users", u.getUserId(), "INSERT", null,
                m("username", u.getUsername(), "person_id", u.getPersonId(),
                        "roles", roles.stream().map(Role::getRoleName).sorted().toList()), actor.userId());
        db.flush();
        return userOut(u);
    }

    public UserOut updateUser(long id, UserUpdate d, CurrentUser actor) {
        User u = db.lock(User.class, id, "User");
        Map<String, Object> old = m(), neu = m();
        if (d.isActive() != null && !d.isActive().equals(u.getIsActive())) {
            if (u.getUserId() == actor.userId() && !d.isActive()) {
                throw ApiException.conflict("SELF_DEACTIVATION", "You cannot deactivate your own account");
            }
            old.put("is_active", u.getIsActive());
            neu.put("is_active", d.isActive());
            u.setIsActive(d.isActive());
        }
        if (d.password() != null) {
            u.setPasswordHash(passwords.hash(d.password()));
            neu.put("password_changed", true);   // the value itself is never audited
        }
        if (!neu.isEmpty()) audit.record("users", id, "UPDATE", old, neu, actor.userId());
        db.flush();
        return userOut(u);
    }

    public UserOut setUserRoles(long id, List<Long> roleIds, CurrentUser actor) {
        User u = db.lock(User.class, id, "User");
        List<Long> wanted = roleIds.stream().distinct().toList();
        List<Role> roles = wanted.isEmpty() ? List.of() : db.list(Role.class, "select r from Role r where r.roleId in :ids", "ids", wanted);
        if (roles.size() != wanted.size()) throw ApiException.badRequest("UNKNOWN_ROLE", "One or more roles do not exist");
        List<String> old = userRoles(id);
        db.update("delete from UserRole ur where ur.userId = :u", "u", id);
        db.em().clear();
        u = db.get(User.class, id, "User");
        for (Role r : roles) {
            UserRole ur = new UserRole();
            ur.setUserId(id);
            ur.setRoleId(r.getRoleId());
            db.save(ur);
        }
        audit.record("user_roles", id, "UPDATE", m("roles", old),
                m("roles", roles.stream().map(Role::getRoleName).sorted().toList()), actor.userId());
        db.flush();
        return userOut(u);
    }
}
