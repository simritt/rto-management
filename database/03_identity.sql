-- ============================================================
-- RTO Management System
-- 03_identity.sql
-- Shared human identity (3 tables).
-- persons is the single identity row for ANY human in the system;
-- citizens and employees specialise it 1:1, so one person can be both.
-- Depends on: nothing.
-- ============================================================

USE rto_management;

CREATE TABLE persons (
    person_id     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    first_name    VARCHAR(60) NOT NULL,
    last_name     VARCHAR(60) NOT NULL,
    date_of_birth DATE NOT NULL,
    gender        ENUM('MALE','FEMALE','OTHER') NOT NULL,
    national_id_number VARCHAR(20) NOT NULL,        -- e.g. Aadhaar-style; sensitive
    phone_primary VARCHAR(15) NOT NULL,
    phone_secondary VARCHAR(15) NULL,
    email         VARCHAR(120) NULL,
    photo_path    VARCHAR(255) NULL,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uq_person_national_id (national_id_number),
    KEY idx_person_phone (phone_primary),
    KEY idx_person_name (last_name, first_name)
) ENGINE=InnoDB COMMENT='Single shared identity for any human in the system';

-- Address history, not a single mutable address: old rows are retained
-- with valid_to set, so "where did they live in 2022" stays answerable.
CREATE TABLE addresses (
    address_id    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    person_id     BIGINT UNSIGNED NOT NULL,
    line1         VARCHAR(150) NOT NULL,
    line2         VARCHAR(150) NULL,
    city          VARCHAR(80) NOT NULL,
    state         VARCHAR(80) NOT NULL,
    pincode       VARCHAR(10) NOT NULL,
    address_type  ENUM('PERMANENT','CURRENT','OFFICE') NOT NULL DEFAULT 'CURRENT',
    is_current    BOOLEAN NOT NULL DEFAULT TRUE,
    valid_from    DATE NOT NULL,
    valid_to      DATE NULL,
    CONSTRAINT fk_address_person FOREIGN KEY (person_id)
        REFERENCES persons(person_id) ON DELETE CASCADE ON UPDATE CASCADE,
    KEY idx_address_person (person_id),
    KEY idx_address_pincode (pincode)
) ENGINE=InnoDB COMMENT='Historical address log per person; is_current flags active row';

CREATE TABLE citizens (
    citizen_id     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    person_id      BIGINT UNSIGNED NOT NULL,
    citizen_code   VARCHAR(20) NOT NULL,            -- external-facing citizen number
    blacklisted    BOOLEAN NOT NULL DEFAULT FALSE,
    blacklist_reason VARCHAR(255) NULL,
    registered_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_citizen_person (person_id),
    UNIQUE KEY uq_citizen_code (citizen_code),
    CONSTRAINT fk_citizen_person FOREIGN KEY (person_id)
        REFERENCES persons(person_id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB;
