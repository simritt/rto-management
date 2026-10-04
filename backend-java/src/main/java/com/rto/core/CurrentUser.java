package com.rto.core;

import java.util.List;
import java.util.Set;

/** The authenticated caller, rebuilt from the database on every request (so role changes apply instantly). */
public record CurrentUser(long userId, String username, long personId, Set<String> permissions, List<String> roles,
                          Long employeeId, Long citizenId, List<Long> officeIds) {

    public boolean has(String permission) {
        return permissions.contains(permission);
    }

    public void require(String permission) {
        if (!has(permission)) {
            throw ApiException.forbidden("PERMISSION_DENIED", "Missing required permission: " + permission);
        }
    }

    public boolean isCitizen(Long citizenId) {
        return this.citizenId != null && this.citizenId.equals(citizenId);
    }
}
