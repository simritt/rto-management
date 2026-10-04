package com.rto.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `role_permissions`). */
@Entity
@Table(name = "role_permissions")
@Getter
@Setter
@NoArgsConstructor
@IdClass(RolePermission.Pk.class)
public class RolePermission {
    @Id
    @Column(name = "role_id")
    private Long roleId;

    @Id
    @Column(name = "permission_id")
    private Long permissionId;

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Pk implements java.io.Serializable {
        private Long roleId;
        private Long permissionId;
    }
}
