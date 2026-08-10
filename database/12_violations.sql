-- ============================================================
-- RTO Management System
-- 12_violations.sql
-- Traffic violations and challans (4 tables).
-- A violation attaches to the VEHICLE; the driver may be unknown at
-- citation time, which is why driver_citizen_id is nullable.
-- Depends on: 02 (violation_types), 03 (citizens), 05 (employees),
--             09 (vehicles).
-- ============================================================

USE rto_management;

CREATE TABLE violations (
    violation_id    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    vehicle_id      BIGINT UNSIGNED NOT NULL,
    driver_citizen_id BIGINT UNSIGNED NULL,             -- NULL = driver unknown at time of citation
    violation_type_id BIGINT UNSIGNED NOT NULL,
    officer_employee_id BIGINT UNSIGNED NOT NULL,
    location        VARCHAR(200) NOT NULL,
    occurred_at     DATETIME NOT NULL,
    KEY idx_violation_vehicle (vehicle_id),
    KEY idx_violation_driver (driver_citizen_id),
    CONSTRAINT fk_violation_vehicle FOREIGN KEY (vehicle_id)
        REFERENCES vehicles(vehicle_id) ON DELETE RESTRICT,
    CONSTRAINT fk_violation_driver FOREIGN KEY (driver_citizen_id)
        REFERENCES citizens(citizen_id) ON DELETE SET NULL,
    CONSTRAINT fk_violation_type FOREIGN KEY (violation_type_id)
        REFERENCES violation_types(violation_type_id) ON DELETE RESTRICT,
    CONSTRAINT fk_violation_officer FOREIGN KEY (officer_employee_id)
        REFERENCES employees(employee_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE challans (
    challan_id     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    challan_number VARCHAR(30) NOT NULL,
    total_amount   DECIMAL(10,2) NOT NULL,
    status         ENUM('ISSUED','DISPUTED','PAID','CANCELLED') NOT NULL DEFAULT 'ISSUED',
    issued_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_challan_number (challan_number),
    KEY idx_challan_status (status)
) ENGINE=InnoDB;

CREATE TABLE challan_violations (
    challan_id   BIGINT UNSIGNED NOT NULL,
    violation_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (challan_id, violation_id),
    CONSTRAINT fk_cv_challan FOREIGN KEY (challan_id) REFERENCES challans(challan_id) ON DELETE CASCADE,
    CONSTRAINT fk_cv_violation FOREIGN KEY (violation_id) REFERENCES violations(violation_id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='One incident can produce several violations bundled into one challan';

CREATE TABLE challan_status_history (
    history_id  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    challan_id  BIGINT UNSIGNED NOT NULL,
    previous_status VARCHAR(20) NULL,
    new_status  VARCHAR(20) NOT NULL,
    changed_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_csh_challan FOREIGN KEY (challan_id) REFERENCES challans(challan_id) ON DELETE CASCADE
) ENGINE=InnoDB;
