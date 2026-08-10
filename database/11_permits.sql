-- ============================================================
-- RTO Management System
-- 11_permits.sql
-- Routes and commercial vehicle permits (4 tables).
-- Depends on: 02 (permit_types), 03 (citizens), 07 (applications),
--             09 (vehicles).
-- ============================================================

USE rto_management;

CREATE TABLE routes (
    route_id    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    route_name  VARCHAR(120) NOT NULL,
    origin      VARCHAR(80) NOT NULL,
    destination VARCHAR(80) NOT NULL
) ENGINE=InnoDB;

-- Ordered stops along a route. sequence_no is unique per route so the
-- ordering cannot silently collide.
CREATE TABLE route_segments (
    segment_id  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    route_id    BIGINT UNSIGNED NOT NULL,
    sequence_no SMALLINT UNSIGNED NOT NULL,
    segment_name VARCHAR(120) NOT NULL,
    UNIQUE KEY uq_route_sequence (route_id, sequence_no),
    CONSTRAINT fk_segment_route FOREIGN KEY (route_id)
        REFERENCES routes(route_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE permits (
    permit_id      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    permit_number  VARCHAR(30) NOT NULL,
    vehicle_id     BIGINT UNSIGNED NOT NULL,
    citizen_id     BIGINT UNSIGNED NOT NULL,           -- operator
    permit_type_id BIGINT UNSIGNED NOT NULL,
    route_id       BIGINT UNSIGNED NULL,
    application_id BIGINT UNSIGNED NOT NULL,
    issue_date     DATE NOT NULL,
    expiry_date    DATE NOT NULL,
    status         ENUM('ACTIVE','EXPIRED','SUSPENDED','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    UNIQUE KEY uq_permit_number (permit_number),
    KEY idx_permit_vehicle (vehicle_id, expiry_date),
    CONSTRAINT fk_permit_vehicle FOREIGN KEY (vehicle_id)
        REFERENCES vehicles(vehicle_id) ON DELETE RESTRICT,
    CONSTRAINT fk_permit_citizen FOREIGN KEY (citizen_id)
        REFERENCES citizens(citizen_id) ON DELETE RESTRICT,
    CONSTRAINT fk_permit_type FOREIGN KEY (permit_type_id)
        REFERENCES permit_types(permit_type_id) ON DELETE RESTRICT,
    CONSTRAINT fk_permit_route FOREIGN KEY (route_id)
        REFERENCES routes(route_id) ON DELETE SET NULL,
    CONSTRAINT fk_permit_application FOREIGN KEY (application_id)
        REFERENCES applications(application_id) ON DELETE RESTRICT,
    CONSTRAINT chk_permit_dates CHECK (expiry_date > issue_date)
) ENGINE=InnoDB;

CREATE TABLE permit_status_history (
    history_id  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    permit_id   BIGINT UNSIGNED NOT NULL,
    previous_status VARCHAR(20) NULL,
    new_status  VARCHAR(20) NOT NULL,
    changed_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason      VARCHAR(255) NULL,
    CONSTRAINT fk_psh_permit FOREIGN KEY (permit_id)
        REFERENCES permits(permit_id) ON DELETE CASCADE
) ENGINE=InnoDB;
