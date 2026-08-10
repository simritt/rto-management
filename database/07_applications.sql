-- ============================================================
-- RTO Management System
-- 07_applications.sql
-- Generic application workflow engine + documents + appointments (6 tables).
-- ONE applications table drives every service (licence, registration,
-- transfer, permit...) instead of one near-identical table per service type.
-- Depends on: 02 (service_types, document_types), 03 (citizens),
--             04 (rto_offices, counters), 05 (employees), 06 (users).
-- ============================================================

USE rto_management;

CREATE TABLE applicants (
    applicant_id  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    citizen_id    BIGINT UNSIGNED NOT NULL,
    preferred_office_id BIGINT UNSIGNED NULL,
    UNIQUE KEY uq_applicant_citizen (citizen_id),
    CONSTRAINT fk_applicant_citizen FOREIGN KEY (citizen_id)
        REFERENCES citizens(citizen_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_applicant_office FOREIGN KEY (preferred_office_id)
        REFERENCES rto_offices(office_id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE TABLE applications (
    application_id   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    application_number VARCHAR(30) NOT NULL,          -- human-facing, unique business key
    applicant_id     BIGINT UNSIGNED NOT NULL,
    service_type_id  BIGINT UNSIGNED NOT NULL,
    office_id        BIGINT UNSIGNED NOT NULL,
    assigned_officer_id BIGINT UNSIGNED NULL,
    current_status   ENUM('SUBMITTED','DOCS_PENDING','UNDER_VERIFICATION','APPOINTMENT_SCHEDULED',
                           'AWAITING_PAYMENT','APPROVED','REJECTED','COMPLETED','CANCELLED')
                      NOT NULL DEFAULT 'SUBMITTED',
    submitted_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at     DATETIME NULL,
    remarks          VARCHAR(500) NULL,
    UNIQUE KEY uq_application_number (application_number),
    KEY idx_application_status (current_status),
    KEY idx_application_office_date (office_id, submitted_at),
    KEY idx_application_applicant (applicant_id),
    CONSTRAINT fk_application_applicant FOREIGN KEY (applicant_id)
        REFERENCES applicants(applicant_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_application_service FOREIGN KEY (service_type_id)
        REFERENCES service_types(service_type_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_application_office FOREIGN KEY (office_id)
        REFERENCES rto_offices(office_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_application_officer FOREIGN KEY (assigned_officer_id)
        REFERENCES employees(employee_id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB COMMENT='Generic workflow engine driving licence/vehicle/permit services';

CREATE TABLE application_status_history (
    history_id     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT UNSIGNED NOT NULL,
    previous_status VARCHAR(30) NULL,
    new_status      VARCHAR(30) NOT NULL,
    changed_by_user_id BIGINT UNSIGNED NULL,
    changed_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason          VARCHAR(255) NULL,
    KEY idx_ash_application (application_id, changed_at),
    CONSTRAINT fk_ash_application FOREIGN KEY (application_id)
        REFERENCES applications(application_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_ash_user FOREIGN KEY (changed_by_user_id)
        REFERENCES users(user_id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB COMMENT='Append-only; populated by trigger, never updated in place';

-- file_path points at object storage, not a BLOB column: keeps the database
-- small and fast to back up, and lets file storage scale independently.
CREATE TABLE documents (
    document_id     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    application_id  BIGINT UNSIGNED NOT NULL,
    document_type_id BIGINT UNSIGNED NOT NULL,
    file_path       VARCHAR(255) NOT NULL,            -- object storage path, not BLOB
    verification_status ENUM('PENDING','VERIFIED','REJECTED','EXPIRED') NOT NULL DEFAULT 'PENDING',
    verified_by_employee_id BIGINT UNSIGNED NULL,
    rejection_reason VARCHAR(255) NULL,
    uploaded_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    verified_at      DATETIME NULL,
    KEY idx_document_application (application_id),
    CONSTRAINT fk_document_application FOREIGN KEY (application_id)
        REFERENCES applications(application_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_document_type FOREIGN KEY (document_type_id)
        REFERENCES document_types(document_type_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_document_verifier FOREIGN KEY (verified_by_employee_id)
        REFERENCES employees(employee_id) ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE TABLE appointment_slots (
    slot_id      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    office_id    BIGINT UNSIGNED NOT NULL,
    counter_id   BIGINT UNSIGNED NULL,
    slot_date    DATE NOT NULL,
    start_time   TIME NOT NULL,
    end_time     TIME NOT NULL,
    capacity     SMALLINT UNSIGNED NOT NULL DEFAULT 1,
    booked_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    UNIQUE KEY uq_slot (office_id, counter_id, slot_date, start_time),
    CONSTRAINT fk_slot_office FOREIGN KEY (office_id)
        REFERENCES rto_offices(office_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_slot_counter FOREIGN KEY (counter_id)
        REFERENCES counters(counter_id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT chk_slot_capacity CHECK (booked_count <= capacity)
) ENGINE=InnoDB COMMENT='booked_count updated transactionally to prevent overbooking';

CREATE TABLE appointments (
    appointment_id  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    application_id  BIGINT UNSIGNED NOT NULL,
    slot_id         BIGINT UNSIGNED NOT NULL,
    token_number    VARCHAR(15) NOT NULL,
    status          ENUM('BOOKED','RESCHEDULED','CANCELLED','NO_SHOW','COMPLETED') NOT NULL DEFAULT 'BOOKED',
    booked_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_appointment_application (application_id),
    KEY idx_appointment_slot (slot_id),
    CONSTRAINT fk_appointment_application FOREIGN KEY (application_id)
        REFERENCES applications(application_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_appointment_slot FOREIGN KEY (slot_id)
        REFERENCES appointment_slots(slot_id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB;
