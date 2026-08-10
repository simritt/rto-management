-- ============================================================
-- RTO Management System
-- 05_employees.sql
-- RTO staff (2 tables).
-- Depends on: 03_identity (persons), 04_office_structure (designations,
--             rto_offices, departments).
-- ============================================================

USE rto_management;

CREATE TABLE employees (
    employee_id    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    person_id      BIGINT UNSIGNED NOT NULL,
    employee_code  VARCHAR(20) NOT NULL,
    designation_id BIGINT UNSIGNED NOT NULL,
    date_joined    DATE NOT NULL,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE KEY uq_employee_person (person_id),
    UNIQUE KEY uq_employee_code (employee_code),
    CONSTRAINT fk_employee_person FOREIGN KEY (person_id)
        REFERENCES persons(person_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_employee_designation FOREIGN KEY (designation_id)
        REFERENCES designations(designation_id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB;

CREATE TABLE employee_postings (
    posting_id    BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    employee_id   BIGINT UNSIGNED NOT NULL,
    office_id     BIGINT UNSIGNED NOT NULL,
    department_id BIGINT UNSIGNED NOT NULL,
    posted_from   DATE NOT NULL,
    posted_to     DATE NULL,                        -- NULL = current posting
    KEY idx_posting_employee (employee_id, posted_to),
    CONSTRAINT fk_posting_employee FOREIGN KEY (employee_id)
        REFERENCES employees(employee_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_posting_office FOREIGN KEY (office_id)
        REFERENCES rto_offices(office_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_posting_department FOREIGN KEY (department_id)
        REFERENCES departments(department_id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB COMMENT='History of which office/department an employee is posted to';

-- Enforce "at most one CURRENT posting per employee" using a generated column
-- + unique index. current_flag is 1 for the open posting and NULL otherwise;
-- UNIQUE ignores NULLs, so only one open row per employee can exist.
ALTER TABLE employee_postings
    ADD COLUMN current_flag TINYINT AS (IF(posted_to IS NULL, 1, NULL)) STORED,
    ADD UNIQUE KEY uq_employee_current_posting (employee_id, current_flag);
