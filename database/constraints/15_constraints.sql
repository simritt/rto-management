-- ============================================================
-- RTO Management System
-- constraints/15_constraints.sql
-- Additional CHECK constraints for data integrity.
--
-- The base schema (01–14) already enforces:
--   • PRIMARY KEY on every table
--   • FOREIGN KEY with explicit ON DELETE / ON UPDATE actions
--   • UNIQUE on every business identifier (licence_number, reg_number, etc.)
--   • NOT NULL on every mandatory column
--   • DEFAULT values where the column has a system-supplied value
--   • CHECK(expiry_date > issue_date) on licence, fitness, PUC, insurance, permit
--   • CHECK(booked_count <= capacity) on appointment_slots
--
-- This file adds the business-rule CHECKs not yet present.
-- All constraints use ALTER TABLE ADD CONSTRAINT so they are safe to
-- run against a schema that was already loaded by 00_run_all.sql.
--
-- Depends on: 01–14 (every base table must already exist).
-- ============================================================

USE rto_management;

-- ------------------------------------------------------------
-- persons — contact / identity fields
-- ------------------------------------------------------------

-- Aadhaar/national-ID: 8–20 alphanumeric characters (no spaces).
ALTER TABLE persons
    ADD CONSTRAINT chk_person_national_id_len
        CHECK (CHAR_LENGTH(national_id_number) BETWEEN 8 AND 20);

-- Primary phone: digits only, 7–15 characters (covers Indian and
-- international formats without country-code prefix characters).
ALTER TABLE persons
    ADD CONSTRAINT chk_person_phone_format
        CHECK (phone_primary REGEXP '^[0-9]{7,15}$');

-- Email: a minimal sanity check (contains @ and at least one dot after @).
ALTER TABLE persons
    ADD CONSTRAINT chk_person_email_format
        CHECK (email IS NULL OR (email LIKE '%@%.%' AND CHAR_LENGTH(email) >= 5));

-- ------------------------------------------------------------
-- addresses — pincode
-- ------------------------------------------------------------

-- Indian pincode: exactly 6 digits.
ALTER TABLE addresses
    ADD CONSTRAINT chk_address_pincode_format
        CHECK (pincode REGEXP '^[0-9]{6}$');

-- valid_to must be NULL (open address) or after valid_from.
ALTER TABLE addresses
    ADD CONSTRAINT chk_address_valid_dates
        CHECK (valid_to IS NULL OR valid_to > valid_from);

-- ------------------------------------------------------------
-- citizens — code length
-- ------------------------------------------------------------

ALTER TABLE citizens
    ADD CONSTRAINT chk_citizen_code_len
        CHECK (CHAR_LENGTH(citizen_code) BETWEEN 4 AND 20);

-- ------------------------------------------------------------
-- service_types — fee and SLA
-- ------------------------------------------------------------

ALTER TABLE service_types
    ADD CONSTRAINT chk_service_base_fee_positive
        CHECK (base_fee >= 0.00);

ALTER TABLE service_types
    ADD CONSTRAINT chk_service_sla_positive
        CHECK (sla_days > 0);

-- ------------------------------------------------------------
-- violation_types — base fine must be > 0
-- ------------------------------------------------------------

ALTER TABLE violation_types
    ADD CONSTRAINT chk_violation_fine_positive
        CHECK (base_fine_amount > 0.00);

-- ------------------------------------------------------------
-- permit_types — validity must be > 0 months
-- ------------------------------------------------------------

ALTER TABLE permit_types
    ADD CONSTRAINT chk_permit_validity_positive
        CHECK (validity_months > 0);

-- ------------------------------------------------------------
-- vehicles — manufacture_year sanity (1886 = first car ever made)
-- ------------------------------------------------------------

ALTER TABLE vehicles
    ADD CONSTRAINT chk_vehicle_manufacture_year
        CHECK (manufacture_year BETWEEN 1886 AND YEAR(CURDATE()) + 1);

-- ------------------------------------------------------------
-- challans — total_amount must be positive
-- ------------------------------------------------------------

ALTER TABLE challans
    ADD CONSTRAINT chk_challan_amount_positive
        CHECK (total_amount > 0.00);

-- ------------------------------------------------------------
-- fee_structures — amounts must be positive
-- ------------------------------------------------------------

ALTER TABLE fee_structures
    ADD CONSTRAINT chk_fee_amount_positive
        CHECK (amount > 0.00);

-- effective_to must be NULL (open-ended) or after effective_from.
ALTER TABLE fee_structures
    ADD CONSTRAINT chk_fee_effective_dates
        CHECK (effective_to IS NULL OR effective_to >= effective_from);

-- ------------------------------------------------------------
-- payments — amount must be positive
-- ------------------------------------------------------------

ALTER TABLE payments
    ADD CONSTRAINT chk_payment_amount_positive
        CHECK (amount > 0.00);

-- paid_at must be NULL (unpaid) or not in the future (at creation time
-- this is enforced at the app layer; DB prevents obviously wrong future dates).
-- We allow up to +1 day to handle UTC vs IST offset edge cases.
ALTER TABLE payments
    ADD CONSTRAINT chk_payment_paid_at_not_future
        CHECK (paid_at IS NULL OR paid_at <= DATE_ADD(NOW(), INTERVAL 1 DAY));

-- ------------------------------------------------------------
-- refunds — amount must be positive
-- ------------------------------------------------------------

ALTER TABLE refunds
    ADD CONSTRAINT chk_refund_amount_positive
        CHECK (amount > 0.00);

-- ------------------------------------------------------------
-- road_tax_records — amount due must be positive
-- ------------------------------------------------------------

ALTER TABLE road_tax_records
    ADD CONSTRAINT chk_road_tax_amount_positive
        CHECK (amount_due > 0.00);

-- ------------------------------------------------------------
-- appointment_slots — time ordering
-- ------------------------------------------------------------

ALTER TABLE appointment_slots
    ADD CONSTRAINT chk_slot_time_order
        CHECK (end_time > start_time);

ALTER TABLE appointment_slots
    ADD CONSTRAINT chk_slot_capacity_positive
        CHECK (capacity > 0);

-- ------------------------------------------------------------
-- driving_tests — scheduled_at must not be obviously past-epoch
-- (prevents test data corruption; no upper bound enforced by DB)
-- ------------------------------------------------------------

ALTER TABLE driving_tests
    ADD CONSTRAINT chk_test_scheduled_not_epoch
        CHECK (scheduled_at > '2000-01-01');

-- ============================================================
-- Verification: list all CHECK constraints in this schema.
-- ============================================================
-- SELECT constraint_name, table_name, check_clause
-- FROM information_schema.check_constraints
-- WHERE constraint_schema = 'rto_management'
-- ORDER BY table_name, constraint_name;
