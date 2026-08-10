-- ============================================================
-- RTO Management System
-- 10_compliance.sql
-- Vehicle compliance: inspections, fitness, pollution, insurance, tax
-- (5 tables).
-- Every (vehicle_id, expiry_date) composite index serves both
-- "current cert for this vehicle" and "expiring in the next N days".
-- Depends on: 05 (employees), 09 (vehicles).
-- ============================================================

USE rto_management;

CREATE TABLE vehicle_inspections (
    inspection_id  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    vehicle_id     BIGINT UNSIGNED NOT NULL,
    inspector_employee_id BIGINT UNSIGNED NOT NULL,
    inspected_at   DATETIME NOT NULL,
    result         ENUM('PASS','FAIL') NOT NULL,
    remarks        VARCHAR(255) NULL,
    KEY idx_inspection_vehicle (vehicle_id),
    CONSTRAINT fk_inspection_vehicle FOREIGN KEY (vehicle_id)
        REFERENCES vehicles(vehicle_id) ON DELETE CASCADE,
    CONSTRAINT fk_inspection_inspector FOREIGN KEY (inspector_employee_id)
        REFERENCES employees(employee_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE fitness_certificates (
    certificate_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    vehicle_id     BIGINT UNSIGNED NOT NULL,
    inspection_id  BIGINT UNSIGNED NULL,
    issue_date     DATE NOT NULL,
    expiry_date    DATE NOT NULL,
    status         ENUM('ACTIVE','EXPIRED','REVOKED') NOT NULL DEFAULT 'ACTIVE',
    KEY idx_fitness_vehicle (vehicle_id, expiry_date),
    CONSTRAINT fk_fitness_vehicle FOREIGN KEY (vehicle_id)
        REFERENCES vehicles(vehicle_id) ON DELETE CASCADE,
    CONSTRAINT fk_fitness_inspection FOREIGN KEY (inspection_id)
        REFERENCES vehicle_inspections(inspection_id) ON DELETE SET NULL,
    CONSTRAINT chk_fitness_dates CHECK (expiry_date > issue_date)
) ENGINE=InnoDB;

CREATE TABLE pollution_certificates (
    puc_id        BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    vehicle_id    BIGINT UNSIGNED NOT NULL,
    issue_date    DATE NOT NULL,
    expiry_date   DATE NOT NULL,
    status        ENUM('ACTIVE','EXPIRED') NOT NULL DEFAULT 'ACTIVE',
    KEY idx_puc_vehicle (vehicle_id, expiry_date),
    CONSTRAINT fk_puc_vehicle FOREIGN KEY (vehicle_id)
        REFERENCES vehicles(vehicle_id) ON DELETE CASCADE,
    CONSTRAINT chk_puc_dates CHECK (expiry_date > issue_date)
) ENGINE=InnoDB;

CREATE TABLE insurance_policies (
    policy_id      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    vehicle_id     BIGINT UNSIGNED NOT NULL,
    provider_name  VARCHAR(100) NOT NULL,
    policy_number  VARCHAR(40) NOT NULL,
    start_date     DATE NOT NULL,
    end_date       DATE NOT NULL,
    UNIQUE KEY uq_policy_number (policy_number),
    KEY idx_insurance_vehicle (vehicle_id, end_date),
    CONSTRAINT fk_insurance_vehicle FOREIGN KEY (vehicle_id)
        REFERENCES vehicles(vehicle_id) ON DELETE CASCADE,
    CONSTRAINT chk_insurance_dates CHECK (end_date > start_date)
) ENGINE=InnoDB;

CREATE TABLE road_tax_records (
    tax_record_id  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    vehicle_id     BIGINT UNSIGNED NOT NULL,
    assessment_year YEAR NOT NULL,
    amount_due     DECIMAL(10,2) NOT NULL,
    due_date       DATE NOT NULL,
    status         ENUM('DUE','PAID','OVERDUE') NOT NULL DEFAULT 'DUE',
    KEY idx_tax_vehicle_year (vehicle_id, assessment_year),
    CONSTRAINT fk_tax_vehicle FOREIGN KEY (vehicle_id)
        REFERENCES vehicles(vehicle_id) ON DELETE CASCADE
) ENGINE=InnoDB;
