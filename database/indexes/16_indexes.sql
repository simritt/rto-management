-- ============================================================
-- RTO Management System
-- indexes/16_indexes.sql
-- Performance indexes beyond those already in the base schema.
--
-- Each index is justified by actual query patterns found in the
-- Spring Boot service layer (ApplicationService, VehicleService,
-- PaymentService, LicenceService, ViolationService, etc.).
--
-- Existing indexes (from the schema files — do NOT recreate):
--   persons:             idx_person_phone, idx_person_name
--   addresses:           idx_address_person, idx_address_pincode
--   applications:        idx_application_status, idx_application_office_date, idx_application_applicant
--   application_status_history: idx_ash_application
--   documents:           idx_document_application
--   appointment_slots:   (uq_slot covers office+counter+date+start)
--   appointments:        idx_appointment_slot
--   learner_licences:    idx_learner_citizen
--   driving_licences:    idx_driving_licence_citizen, idx_driving_licence_status
--   driving_tests:       idx_test_citizen, idx_test_centre_date
--   vehicle_ownerships:  idx_ownership_vehicle, idx_ownership_citizen
--   vehicles:            idx_vehicle_type_status
--   vehicle_inspections: idx_inspection_vehicle
--   fitness_certificates: idx_fitness_vehicle
--   pollution_certificates: idx_puc_vehicle
--   insurance_policies:  idx_insurance_vehicle
--   road_tax_records:    idx_tax_vehicle_year
--   permits:             idx_permit_vehicle
--   violations:          idx_violation_vehicle, idx_violation_driver
--   challans:            idx_challan_status
--   payments:            idx_payment_payable, idx_payment_status
--   employee_postings:   idx_posting_employee
--   audit_logs:          idx_audit_table_record, idx_audit_changed_at
--   licence_status_history: idx_lsh_licence
--
-- Depends on: all base schema files (01–14).
-- ============================================================

USE rto_management;

-- ------------------------------------------------------------
-- applications — additional lookup patterns
-- ------------------------------------------------------------

-- ApplicationService.list() filters on submitted_at date range.
-- The existing idx_application_office_date covers (office_id, submitted_at).
-- A standalone submitted_at index supports date-range queries without office_id.
CREATE INDEX idx_application_submitted_date
    ON applications (submitted_at);

-- Applications are frequently fetched by assigned officer for workload views.
CREATE INDEX idx_application_assigned_officer
    ON applications (assigned_officer_id, current_status);

-- Service type filter used in application listing + fee calculation.
CREATE INDEX idx_application_service_type
    ON applications (service_type_id);

-- ------------------------------------------------------------
-- citizens — citizen_code is used for search across multiple services
-- ------------------------------------------------------------
-- citizen_code already has UNIQUE (uq_citizen_code) which acts as an index.
-- Add a composite for blacklist-status filter used in CitizenService.
CREATE INDEX idx_citizen_blacklisted
    ON citizens (blacklisted);

-- ------------------------------------------------------------
-- persons — email lookup for auth / notification resolution
-- ------------------------------------------------------------
CREATE INDEX idx_person_email
    ON persons (email);

-- ------------------------------------------------------------
-- users — active user lookup for authentication (JwtAuthFilter)
-- ------------------------------------------------------------
-- username already has UNIQUE (uq_username).
-- Add composite to support "is_active AND username" lookup quickly.
CREATE INDEX idx_user_active_login
    ON users (username, is_active);

-- last_login_at queried for security/audit dashboards.
CREATE INDEX idx_user_last_login
    ON users (last_login_at);

-- ------------------------------------------------------------
-- violations — occurred_at (temporal range queries for challans/reports)
-- ------------------------------------------------------------
CREATE INDEX idx_violation_occurred
    ON violations (occurred_at);

-- Officer who issued violation — used in employee workload queries.
CREATE INDEX idx_violation_officer
    ON violations (officer_employee_id);

-- ------------------------------------------------------------
-- challans — issued_at (date range reports)
-- ------------------------------------------------------------
CREATE INDEX idx_challan_issued_at
    ON challans (issued_at);

-- ------------------------------------------------------------
-- payments — created_at for date-range financial reports
-- ------------------------------------------------------------
CREATE INDEX idx_payment_created_at
    ON payments (created_at);

-- paid_at — for reconciliation queries (payments settled on a given day).
CREATE INDEX idx_payment_paid_at
    ON payments (paid_at);

-- ------------------------------------------------------------
-- payment_attempts — gateway_reference (idempotency check in PaymentService)
-- ------------------------------------------------------------
-- PaymentService.replayOf() does: SELECT FROM PaymentAttempt WHERE gatewayReference = ?
-- This is a hot path (called on every payment create/retry).
CREATE INDEX idx_attempt_gateway_ref
    ON payment_attempts (gateway_reference);

-- ------------------------------------------------------------
-- driving_licences — expiry_date (renewal reminders, expiry dashboards)
-- ------------------------------------------------------------
CREATE INDEX idx_dl_expiry
    ON driving_licences (expiry_date, current_status);

-- ------------------------------------------------------------
-- learner_licences — expiry_date (conversion deadline tracking)
-- ------------------------------------------------------------
CREATE INDEX idx_ll_expiry
    ON learner_licences (expiry_date, status);

-- ------------------------------------------------------------
-- permits — expiry_date + status (commercial vehicle compliance checks)
-- ------------------------------------------------------------
-- idx_permit_vehicle already covers (vehicle_id, expiry_date).
-- Add status alone for "all expiring permits regardless of vehicle".
CREATE INDEX idx_permit_expiry_status
    ON permits (expiry_date, status);

-- ------------------------------------------------------------
-- fitness_certificates — expiry + status (compliance batch jobs)
-- ------------------------------------------------------------
CREATE INDEX idx_fitness_expiry_status
    ON fitness_certificates (expiry_date, status);

-- ------------------------------------------------------------
-- pollution_certificates — expiry + status
-- ------------------------------------------------------------
CREATE INDEX idx_puc_expiry_status
    ON pollution_certificates (expiry_date, status);

-- ------------------------------------------------------------
-- insurance_policies — end_date (renewal alerts)
-- ------------------------------------------------------------
CREATE INDEX idx_insurance_end_date
    ON insurance_policies (end_date);

-- ------------------------------------------------------------
-- road_tax_records — status + due_date (overdue batch)
-- ------------------------------------------------------------
-- sp_update_overdue_road_tax() scans status='DUE' AND due_date < CURDATE().
CREATE INDEX idx_road_tax_status_due
    ON road_tax_records (status, due_date);

-- ------------------------------------------------------------
-- notifications — person + status (delivery queue processing)
-- ------------------------------------------------------------
CREATE INDEX idx_notification_person_status
    ON notifications (person_id, status);

-- sent_at for notification history reports.
CREATE INDEX idx_notification_sent_at
    ON notifications (sent_at);

-- ------------------------------------------------------------
-- complaints — status + office (staff workload view)
-- ------------------------------------------------------------
CREATE INDEX idx_complaint_office_status
    ON complaints (office_id, status);

-- ------------------------------------------------------------
-- appeals — status (admin review queue)
-- ------------------------------------------------------------
CREATE INDEX idx_appeal_status
    ON appeals (status, filed_at);

-- ------------------------------------------------------------
-- employee_postings — office + open posting (officer assignment check)
-- ------------------------------------------------------------
-- ApplicationService.assign() queries:
--   EmployeePosting WHERE employeeId = ? AND postedTo IS NULL AND officeId = ?
-- idx_posting_employee covers (employee_id, posted_to).
-- Add office side for reverse: "who is currently posted at this office?"
CREATE INDEX idx_posting_office_current
    ON employee_postings (office_id, posted_to);

-- ============================================================
-- Verification: count indexes per table.
-- ============================================================
-- SELECT table_name, index_name, non_unique,
--        GROUP_CONCAT(column_name ORDER BY seq_in_index) AS columns
-- FROM information_schema.statistics
-- WHERE table_schema = 'rto_management'
-- GROUP BY table_name, index_name, non_unique
-- ORDER BY table_name, index_name;
