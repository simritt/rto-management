-- ============================================================
-- RTO Management System
-- 14_support.sql
-- Notifications, complaints, appeals, audit trail (4 tables).
-- audit_logs is cross-cutting and polymorphic; old/new values are JSON
-- because that payload genuinely has no fixed shape.
-- Depends on: 03 (persons, citizens), 04 (rto_offices).
-- ============================================================

USE rto_management;

CREATE TABLE notifications (
    notification_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    person_id       BIGINT UNSIGNED NOT NULL,
    channel         ENUM('SMS','EMAIL') NOT NULL,
    subject         VARCHAR(150) NULL,
    message         VARCHAR(500) NOT NULL,
    status          ENUM('QUEUED','SENT','FAILED') NOT NULL DEFAULT 'QUEUED',
    sent_at         DATETIME NULL,
    CONSTRAINT fk_notification_person FOREIGN KEY (person_id)
        REFERENCES persons(person_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE complaints (
    complaint_id   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    citizen_id     BIGINT UNSIGNED NOT NULL,
    office_id      BIGINT UNSIGNED NOT NULL,
    subject        VARCHAR(150) NOT NULL,
    description    VARCHAR(1000) NOT NULL,
    status         ENUM('OPEN','IN_PROGRESS','RESOLVED','CLOSED') NOT NULL DEFAULT 'OPEN',
    filed_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_complaint_citizen FOREIGN KEY (citizen_id)
        REFERENCES citizens(citizen_id) ON DELETE RESTRICT,
    CONSTRAINT fk_complaint_office FOREIGN KEY (office_id)
        REFERENCES rto_offices(office_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

-- against_id is polymorphic (challan / application / licence), resolved at
-- the app layer per against_type — same pattern as payments.payable_id.
CREATE TABLE appeals (
    appeal_id      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    citizen_id     BIGINT UNSIGNED NOT NULL,
    against_type   ENUM('CHALLAN','APPLICATION_REJECTION','LICENCE_SUSPENSION') NOT NULL,
    against_id     BIGINT UNSIGNED NOT NULL,
    grounds        VARCHAR(1000) NOT NULL,
    status         ENUM('FILED','UNDER_REVIEW','UPHELD','DISMISSED') NOT NULL DEFAULT 'FILED',
    filed_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at    DATETIME NULL,
    CONSTRAINT fk_appeal_citizen FOREIGN KEY (citizen_id)
        REFERENCES citizens(citizen_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE audit_logs (
    audit_id     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    table_name   VARCHAR(64) NOT NULL,
    record_id    BIGINT UNSIGNED NOT NULL,
    action       ENUM('INSERT','UPDATE','DELETE') NOT NULL,
    old_values   JSON NULL,
    new_values   JSON NULL,
    changed_by_user_id BIGINT UNSIGNED NULL,
    changed_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_audit_table_record (table_name, record_id),
    KEY idx_audit_changed_at (changed_at)
) ENGINE=InnoDB COMMENT='Polymorphic; table_name+record_id resolved at app layer. Candidate for RANGE partitioning by changed_at once large.';
