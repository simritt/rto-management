package com.rto.dto;

import com.rto.core.ValidPassword;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public final class RbacDto {
    private RbacDto() {}

    public record RoleDetail(Long roleId, String roleName, List<String> permissions) {}

    public record RoleCreate(@NotNull @Size(min = 2, max = 50) String roleName, List<String> permissionKeys) {
        public RoleCreate {
            permissionKeys = permissionKeys == null ? new ArrayList<>() : permissionKeys;
        }
    }

    public record RolePermissionsSet(@NotNull List<String> permissionKeys) {}

    public record UserCreate(@NotNull Long personId,
                             @NotNull @Size(min = 3, max = 50) @Pattern(regexp = "^[A-Za-z0-9_.@-]+$") String username,
                             @NotNull @ValidPassword String password, List<Long> roleIds) {
        public UserCreate {
            roleIds = roleIds == null ? new ArrayList<>() : roleIds;
        }
    }

    public record UserUpdate(Boolean isActive, @ValidPassword String password) {}

    public record UserRolesSet(@NotNull List<Long> roleIds) {}

    /** password_hash is intentionally absent from every user representation. */
    public record UserOut(Long userId, Long personId, String username, Boolean isActive, LocalDateTime lastLoginAt,
                          List<String> roles) {}
}
