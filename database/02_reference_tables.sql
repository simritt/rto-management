-- ============================================================
-- RTO Management System
-- 02_reference_tables.sql
-- Reference / lookup tables (11 tables).
-- Stable, low-cardinality domains. Kept as tables (not ENUM) so the
-- business can add values without a schema migration.
-- Dependencies: none, except regions -> regions (self-referencing).
-- ============================================================

USE rto_management;

-- Geographic/administrative hierarchy. Self-referencing parent_region_id
-- supports state -> zone -> district nesting of arbitrary depth.
CREATE TABLE regions (
    region_id       BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    region_name     VARCHAR(100) NOT NULL,
    region_code     VARCHAR(10)  NOT NULL,
    parent_region_id BIGINT UNSIGNED NULL,
    UNIQUE KEY uq_region_code (region_code),
    CONSTRAINT fk_region_parent FOREIGN KEY (parent_region_id)
        REFERENCES regions(region_id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB COMMENT='Geographic/administrative hierarchy (state -> zone -> district)';

CREATE TABLE vehicle_manufacturers (
    manufacturer_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    UNIQUE KEY uq_manufacturer_name (name)
) ENGINE=InnoDB;

-- Separate from vehicles to resolve the transitive dependency
-- vehicle_id -> model_id -> manufacturer_id -> manufacturer_name (3NF).
CREATE TABLE vehicle_models (
    model_id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    manufacturer_id  BIGINT UNSIGNED NOT NULL,
    model_name       VARCHAR(100) NOT NULL,
    UNIQUE KEY uq_manufacturer_model (manufacturer_id, model_name),
    CONSTRAINT fk_model_manufacturer FOREIGN KEY (manufacturer_id)
        REFERENCES vehicle_manufacturers(manufacturer_id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE TABLE vehicle_types (
    vehicle_type_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    type_name       VARCHAR(50) NOT NULL,          -- e.g. Two-Wheeler, LMV, HMV, Transport
    is_commercial   BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE KEY uq_vehicle_type_name (type_name)
) ENGINE=InnoDB;

CREATE TABLE fuel_types (
    fuel_type_id  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    fuel_name     VARCHAR(30) NOT NULL,
    UNIQUE KEY uq_fuel_name (fuel_name)
) ENGINE=InnoDB;

CREATE TABLE licence_classes (
    licence_class_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    class_code       VARCHAR(10) NOT NULL,          -- MC50CC, LMV, HMV, TRANS
    description      VARCHAR(150) NOT NULL,
    UNIQUE KEY uq_class_code (class_code)
) ENGINE=InnoDB;

CREATE TABLE document_types (
    document_type_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    type_name        VARCHAR(100) NOT NULL,
    is_mandatory_default BOOLEAN NOT NULL DEFAULT TRUE,
    validity_period_days INT UNSIGNED NULL,          -- NULL = does not expire
    UNIQUE KEY uq_doc_type_name (type_name)
) ENGINE=InnoDB;

CREATE TABLE service_types (
    service_type_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    service_name    VARCHAR(100) NOT NULL,          -- New Licence, Renewal, Ownership Transfer, etc.
    service_code    VARCHAR(30)  NOT NULL,
    base_fee        DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    sla_days        INT UNSIGNED NOT NULL DEFAULT 7,
    UNIQUE KEY uq_service_code (service_code)
) ENGINE=InnoDB;

CREATE TABLE violation_types (
    violation_type_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    description        VARCHAR(150) NOT NULL,
    base_fine_amount   DECIMAL(10,2) NOT NULL,
    is_cognizable      BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE KEY uq_violation_desc (description)
) ENGINE=InnoDB;

CREATE TABLE permit_types (
    permit_type_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    type_name      VARCHAR(50) NOT NULL,           -- State, National, Route, Temporary
    validity_months INT UNSIGNED NOT NULL,
    UNIQUE KEY uq_permit_type_name (type_name)
) ENGINE=InnoDB;

-- Drives the polymorphic payments.payable_type_id discriminator.
CREATE TABLE payable_types (
    payable_type_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    type_name       VARCHAR(30) NOT NULL,           -- APPLICATION, CHALLAN, PERMIT, ROAD_TAX
    UNIQUE KEY uq_payable_type_name (type_name)
) ENGINE=InnoDB;
