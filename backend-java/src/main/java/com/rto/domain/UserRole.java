package com.rto.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Generated from the schema in /database (table `user_roles`). */
@Entity
@Table(name = "user_roles")
@Getter
@Setter
@NoArgsConstructor
@IdClass(UserRole.Pk.class)
public class UserRole {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "role_id")
    private Long roleId;

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Pk implements java.io.Serializable {
        private Long userId;
        private Long roleId;
    }
}
