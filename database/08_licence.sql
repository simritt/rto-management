-- ============================================================
-- RTO Management System
-- 08_licence.sql
-- Learner + driving licence subsystem, schools and tests (8 tables).
-- Depends on: 02 (licence_classes), 03 (persons, citizens),
--             04 (rto_offices), 05 (employees), 06 (users),
--             07 (applications).
-- ============================================================

USE rto_management;

CREATE TABLE driving_schools (
    school_id   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    school_name VARCHAR(120) NOT NULL,
    licence_number VARCHAR(30) NOT NULL,
    office_id   BIGINT UNSIGNED NOT NULL,
    UNIQUE KEY uq_school_licence (licence_number),
    CONSTRAINT fk_school_office FOREIGN KEY (office_id)
        REFERENCES rto_offices(office_id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE TABLE driving_instructors (
    instructor_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    person_id     BIGINT UNSIGNED NOT NULL,
    school_id     BIGINT UNSIGNED NOT NULL,
    licence_class_id BIGINT UNSIGNED NOT NULL,
    UNIQUE KEY uq_instructor_person (person_id),
    CONSTRAINT fk_instructor_person FOREIGN KEY (person_id)
        REFERENCES persons(person_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_instructor_school FOREIGN KEY (school_id)
        REFERENCES driving_schools(school_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_instructor_class FOREIGN KEY (licence_class_id)
        REFERENCES licence_classes(licence_class_id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE TABLE test_centres (
    test_centre_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    office_id      BIGINT UNSIGNED NOT NULL,
    centre_name    VARCHAR(120) NOT NULL,
    CONSTRAINT fk_testcentre_office FOREIGN KEY (office_id)
        REFERENCES rto_offices(office_id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE TABLE learner_licences (
    learner_licence_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    licence_number   VARCHAR(30) NOT NULL,
    citizen_id       BIGINT UNSIGNED NOT NULL,
    application_id   BIGINT UNSIGNED NOT NULL,
    office_id        BIGINT UNSIGNED NOT NULL,
    issue_date       DATE NOT NULL,
    expiry_date      DATE NOT NULL,
    status           ENUM('ACTIVE','EXPIRED','CONVERTED','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    UNIQUE KEY uq_learner_licence_number (licence_number),
    KEY idx_learner_citizen (citizen_id),
    CONSTRAINT fk_learner_citizen FOREIGN KEY (citizen_id)
        REFERENCES citizens(citizen_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_learner_application FOREIGN KEY (application_id)
        REFERENCES applications(application_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_learner_office FOREIGN KEY (office_id)
        REFERENCES rto_offices(office_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT chk_learner_dates CHECK (expiry_date > issue_date)
) ENGINE=InnoDB;

-- At most one ACTIVE learner licence per citizen (same generated-column
-- + UNIQUE trick used for current postings and current vehicle owners).
ALTER TABLE learner_licences
    ADD COLUMN active_flag TINYINT AS (IF(status = 'ACTIVE', 1, NULL)) STORED,
    ADD UNIQUE KEY uq_citizen_active_learner (citizen_id, active_flag);

CREATE TABLE driving_licences (
    driving_licence_id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    licence_number    VARCHAR(30) NOT NULL,
    citizen_id        BIGINT UNSIGNED NOT NULL,
    learner_licence_id BIGINT UNSIGNED NULL,
    application_id    BIGINT UNSIGNED NOT NULL,
    office_id         BIGINT UNSIGNED NOT NULL,
    issue_date        DATE NOT NULL,
    expiry_date       DATE NOT NULL,
    current_status    ENUM('ACTIVE','SUSPENDED','REVOKED','EXPIRED') NOT NULL DEFAULT 'ACTIVE',
    UNIQUE KEY uq_driving_licence_number (licence_number),
    KEY idx_driving_licence_citizen (citizen_id),
    KEY idx_driving_licence_status (current_status),
    CONSTRAINT fk_dl_citizen FOREIGN KEY (citizen_id)
        REFERENCES citizens(citizen_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_dl_learner FOREIGN KEY (learner_licence_id)
        REFERENCES learner_licences(learner_licence_id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_dl_application FOREIGN KEY (application_id)
        REFERENCES applications(application_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_dl_office FOREIGN KEY (office_id)
        REFERENCES rto_offices(office_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT chk_dl_dates CHECK (expiry_date > issue_date)
) ENGINE=InnoDB;

CREATE TABLE licence_class_assignments (
    driving_licence_id BIGINT UNSIGNED NOT NULL,
    licence_class_id   BIGINT UNSIGNED NOT NULL,
    granted_on         DATE NOT NULL,
    PRIMARY KEY (driving_licence_id, licence_class_id),
    CONSTRAINT fk_lca_licence FOREIGN KEY (driving_licence_id)
        REFERENCES driving_licences(driving_licence_id) ON DELETE CASCADE,
    CONSTRAINT fk_lca_class FOREIGN KEY (licence_class_id)
        REFERENCES licence_classes(licence_class_id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='Resolves N:M licence-to-class instead of comma-separated values';

CREATE TABLE licence_status_history (
    history_id      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    driving_licence_id BIGINT UNSIGNED NOT NULL,
    previous_status VARCHAR(20) NULL,
    new_status      VARCHAR(20) NOT NULL,
    changed_by_user_id BIGINT UNSIGNED NULL,
    changed_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason          VARCHAR(255) NULL,
    KEY idx_lsh_licence (driving_licence_id, changed_at),
    CONSTRAINT fk_lsh_licence FOREIGN KEY (driving_licence_id)
        REFERENCES driving_licences(driving_licence_id) ON DELETE CASCADE,
    CONSTRAINT fk_lsh_user FOREIGN KEY (changed_by_user_id)
        REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE driving_tests (
    test_id        BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT UNSIGNED NOT NULL,
    citizen_id     BIGINT UNSIGNED NOT NULL,
    test_centre_id BIGINT UNSIGNED NOT NULL,
    examiner_employee_id BIGINT UNSIGNED NOT NULL,
    scheduled_at   DATETIME NOT NULL,
    result         ENUM('PENDING','PASS','FAIL','ABSENT') NOT NULL DEFAULT 'PENDING',
    remarks        VARCHAR(255) NULL,
    KEY idx_test_citizen (citizen_id),
    KEY idx_test_centre_date (test_centre_id, scheduled_at),
    CONSTRAINT fk_test_application FOREIGN KEY (application_id)
        REFERENCES applications(application_id) ON DELETE CASCADE,
    CONSTRAINT fk_test_citizen FOREIGN KEY (citizen_id)
        REFERENCES citizens(citizen_id) ON DELETE RESTRICT,
    CONSTRAINT fk_test_centre FOREIGN KEY (test_centre_id)
        REFERENCES test_centres(test_centre_id) ON DELETE RESTRICT,
    CONSTRAINT fk_test_examiner FOREIGN KEY (examiner_employee_id)
        REFERENCES employees(employee_id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='Multiple rows per citizen supports retakes';
