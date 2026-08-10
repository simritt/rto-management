-- ============================================================
-- RTO Management System
-- 09_vehicles.sql
-- Vehicle master, effective-dated ownership, transfer workflow (3 tables).
-- Ownership is a dated table, NOT a mutable owner_id column on vehicles,
-- so "who owned this vehicle in 2022" stays answerable forever.
-- Depends on: 02 (manufacturers, models, vehicle_types, fuel_types),
--             03 (citizens), 04 (rto_offices), 07 (applications).
-- ============================================================

USE rto_management;

CREATE TABLE vehicles (
    vehicle_id       BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    registration_number VARCHAR(20) NOT NULL,
    chassis_number   VARCHAR(40) NOT NULL,
    engine_number    VARCHAR(40) NOT NULL,
    manufacturer_id  BIGINT UNSIGNED NOT NULL,
    model_id         BIGINT UNSIGNED NOT NULL,
    vehicle_type_id  BIGINT UNSIGNED NOT NULL,
    fuel_type_id     BIGINT UNSIGNED NOT NULL,
    manufacture_year YEAR NOT NULL,
    color            VARCHAR(30) NULL,
    registering_office_id BIGINT UNSIGNED NOT NULL,
    registration_date DATE NOT NULL,
    status           ENUM('ACTIVE','BLACKLISTED','SCRAPPED','DEREGISTERED') NOT NULL DEFAULT 'ACTIVE',
    UNIQUE KEY uq_vehicle_registration (registration_number),
    UNIQUE KEY uq_vehicle_chassis (chassis_number),
    UNIQUE KEY uq_vehicle_engine (engine_number),
    KEY idx_vehicle_type_status (vehicle_type_id, status),
    CONSTRAINT fk_vehicle_manufacturer FOREIGN KEY (manufacturer_id)
        REFERENCES vehicle_manufacturers(manufacturer_id) ON DELETE RESTRICT,
    CONSTRAINT fk_vehicle_model FOREIGN KEY (model_id)
        REFERENCES vehicle_models(model_id) ON DELETE RESTRICT,
    CONSTRAINT fk_vehicle_type FOREIGN KEY (vehicle_type_id)
        REFERENCES vehicle_types(vehicle_type_id) ON DELETE RESTRICT,
    CONSTRAINT fk_vehicle_fuel FOREIGN KEY (fuel_type_id)
        REFERENCES fuel_types(fuel_type_id) ON DELETE RESTRICT,
    CONSTRAINT fk_vehicle_office FOREIGN KEY (registering_office_id)
        REFERENCES rto_offices(office_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE vehicle_ownerships (
    ownership_id  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    vehicle_id    BIGINT UNSIGNED NOT NULL,
    citizen_id    BIGINT UNSIGNED NOT NULL,
    effective_from DATE NOT NULL,
    effective_to   DATE NULL,                        -- NULL = current owner
    KEY idx_ownership_vehicle (vehicle_id, effective_to),
    KEY idx_ownership_citizen (citizen_id),
    CONSTRAINT fk_ownership_vehicle FOREIGN KEY (vehicle_id)
        REFERENCES vehicles(vehicle_id) ON DELETE RESTRICT,
    CONSTRAINT fk_ownership_citizen FOREIGN KEY (citizen_id)
        REFERENCES citizens(citizen_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- Enforce exactly one CURRENT owner row per vehicle. The generated column is
-- 1 only while effective_to IS NULL; UNIQUE ignores NULLs, so a second open
-- ownership row for the same vehicle fails with ERROR 1062.
ALTER TABLE vehicle_ownerships
    ADD COLUMN current_flag TINYINT AS (IF(effective_to IS NULL, 1, NULL)) STORED,
    ADD UNIQUE KEY uq_vehicle_current_owner (vehicle_id, current_flag);

CREATE TABLE ownership_transfers (
    transfer_id    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    vehicle_id     BIGINT UNSIGNED NOT NULL,
    from_citizen_id BIGINT UNSIGNED NOT NULL,
    to_citizen_id   BIGINT UNSIGNED NOT NULL,
    application_id  BIGINT UNSIGNED NOT NULL,
    requested_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at     DATETIME NULL,
    status          ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
    CONSTRAINT fk_transfer_vehicle FOREIGN KEY (vehicle_id)
        REFERENCES vehicles(vehicle_id) ON DELETE RESTRICT,
    CONSTRAINT fk_transfer_from FOREIGN KEY (from_citizen_id)
        REFERENCES citizens(citizen_id) ON DELETE RESTRICT,
    CONSTRAINT fk_transfer_to FOREIGN KEY (to_citizen_id)
        REFERENCES citizens(citizen_id) ON DELETE RESTRICT,
    CONSTRAINT fk_transfer_application FOREIGN KEY (application_id)
        REFERENCES applications(application_id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='Workflow row; approval closes old vehicle_ownerships row and opens new one (see sp_transfer_vehicle_ownership)';
