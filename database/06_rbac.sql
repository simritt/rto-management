-- ============================================================
-- RTO Management System
-- 06_rbac.sql
-- Role-based access control (5 tables).
-- user_roles and role_permissions are pure junction tables — the pair
-- itself is the whole entity, so the composite key IS the primary key.
-- Depends on: 03_identity (persons).
-- ============================================================

USE rto_management;

CREATE TABLE users (
    user_id       BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    person_id     BIGINT UNSIGNED NOT NULL,
    username      VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,             -- never plaintext; app hashes (bcrypt/argon2)
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at DATETIME NULL,
    UNIQUE KEY uq_username (username),
    CONSTRAINT fk_user_person FOREIGN KEY (person_id)
        REFERENCES persons(person_id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE TABLE roles (
    role_id    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    role_name  VARCHAR(50) NOT NULL,
    UNIQUE KEY uq_role_name (role_name)
) ENGINE=InnoDB;

CREATE TABLE permissions (
    permission_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    permission_key VARCHAR(80) NOT NULL,             -- e.g. 'application.approve'
    UNIQUE KEY uq_permission_key (permission_key)
) ENGINE=InnoDB;

CREATE TABLE user_roles (
    user_id BIGINT UNSIGNED NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_userrole_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_userrole_role FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE role_permissions (
    role_id BIGINT UNSIGNED NOT NULL,
    permission_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_roleperm_role FOREIGN KEY (role_id) REFERENCES roles(role_id) ON DELETE CASCADE,
    CONSTRAINT fk_roleperm_permission FOREIGN KEY (permission_id) REFERENCES permissions(permission_id) ON DELETE CASCADE
) ENGINE=InnoDB;
