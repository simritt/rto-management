-- ============================================================
-- RTO Management System
-- 04_office_structure.sql
-- Office / organisational structure (4 tables).
-- Depends on: 02_reference_tables (regions).
-- ============================================================

USE rto_management;

CREATE TABLE rto_offices (
    office_id     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    region_id     BIGINT UNSIGNED NOT NULL,
    office_name   VARCHAR(100) NOT NULL,
    office_code   VARCHAR(15) NOT NULL,
    address_line  VARCHAR(200) NOT NULL,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE KEY uq_office_code (office_code),
    CONSTRAINT fk_office_region FOREIGN KEY (region_id)
        REFERENCES regions(region_id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE TABLE departments (
    department_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    department_name VARCHAR(80) NOT NULL,
    UNIQUE KEY uq_department_name (department_name)
) ENGINE=InnoDB;

CREATE TABLE designations (
    designation_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    title          VARCHAR(80) NOT NULL,
    rank_level     TINYINT UNSIGNED NOT NULL,       -- 1 = highest authority
    UNIQUE KEY uq_designation_title (title)
) ENGINE=InnoDB;

CREATE TABLE counters (
    counter_id    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    office_id     BIGINT UNSIGNED NOT NULL,
    counter_number VARCHAR(10) NOT NULL,
    service_category VARCHAR(50) NULL,
    UNIQUE KEY uq_office_counter (office_id, counter_number),
    CONSTRAINT fk_counter_office FOREIGN KEY (office_id)
        REFERENCES rto_offices(office_id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;
