package com.rto.service;

import com.rto.core.ApiException;
import com.rto.core.CurrentUser;
import com.rto.core.Db;
import com.rto.domain.Employee;
import com.rto.domain.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Builds the CurrentUser for every request: permissions come ONLY from user_roles + role_permissions. */
@Service
public class UserLoader {
    private final Db db;

    public UserLoader(Db db) {
        this.db = db;
    }

    @Transactional(readOnly = true)
    public CurrentUser load(long userId) {
        User user = db.find(User.class, userId);
        if (user == null || !Boolean.TRUE.equals(user.getIsActive())) {
            throw ApiException.unauthorized("USER_INACTIVE", "User is inactive or no longer exists");
        }
        List<String> roles = db.list(String.class, "select r.roleName from UserRole ur join Role r on r.roleId = ur.roleId "
                + "where ur.userId = :u order by r.roleName", "u", userId);
        Set<String> perms = new HashSet<>(db.list(String.class,
                "select distinct p.permissionKey from UserRole ur join RolePermission rp on rp.roleId = ur.roleId "
                        + "join Permission p on p.permissionId = rp.permissionId where ur.userId = :u", "u", userId));
        Employee emp = db.first(Employee.class, "select e from Employee e where e.personId = :p and e.isActive = true",
                "p", user.getPersonId());
        Long citizenId = db.first(Long.class, "select c.citizenId from Citizen c where c.personId = :p", "p", user.getPersonId());
        List<Long> offices = emp == null ? List.of() : db.list(Long.class,
                "select ep.officeId from EmployeePosting ep where ep.employeeId = :e and ep.postedTo is null",
                "e", emp.getEmployeeId());
        return new CurrentUser(user.getUserId(), user.getUsername(), user.getPersonId(), Set.copyOf(perms), roles,
                emp == null ? null : emp.getEmployeeId(), citizenId, offices);
    }
}
