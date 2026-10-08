-- ============================================================
-- RTO Management System
-- seed/22_sample_data.sql
-- Realistic but entirely fictional seed data for testing.
--
-- Load order exactly mirrors the FK dependency graph:
--   Reference tables → Identity → Office → Employees → RBAC →
--   Applications → Licences → Vehicles → Compliance →
--   Permits → Violations → Payments → Support → History rows
--
-- All IDs are explicit so cross-references in later inserts are
-- predictable and easy to follow.
--
-- Run AFTER the full schema (00_run_all.sql) AND the enhancement
-- files (15–20) have been applied.
-- ============================================================

USE rto_management;

-- Disable FK checks during bulk insert so the order is flexible.
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- SECTION 1: REFERENCE / LOOKUP TABLES
-- ============================================================

-- Regions: Gujarat state → Ahmedabad zone → two districts
INSERT INTO regions (region_id, region_name, region_code, parent_region_id) VALUES
    (1, 'Gujarat',              'GJ',    NULL),
    (2, 'Ahmedabad Zone',       'GJ-AZ', 1),
    (3, 'Ahmedabad District',   'GJ-AD', 2),
    (4, 'Surat District',       'GJ-SD', 2);

-- Vehicle manufacturers
INSERT INTO vehicle_manufacturers (manufacturer_id, name) VALUES
    (1, 'Maruti Suzuki'),
    (2, 'Hero MotoCorp'),
    (3, 'Tata Motors'),
    (4, 'Mahindra'),
    (5, 'Honda');

-- Vehicle models
INSERT INTO vehicle_models (model_id, manufacturer_id, model_name) VALUES
    (1,  1, 'Swift'),
    (2,  1, 'Baleno'),
    (3,  2, 'Splendor Plus'),
    (4,  2, 'Passion Pro'),
    (5,  3, 'Nexon'),
    (6,  3, 'Tiago'),
    (7,  4, 'Bolero'),
    (8,  4, 'Scorpio'),
    (9,  5, 'Activa 6G'),
    (10, 5, 'City');

-- Vehicle types
INSERT INTO vehicle_types (vehicle_type_id, type_name, is_commercial) VALUES
    (1, 'Two-Wheeler',         FALSE),
    (2, 'Light Motor Vehicle', FALSE),
    (3, 'Heavy Motor Vehicle', TRUE),
    (4, 'Transport Vehicle',   TRUE);

-- Fuel types
INSERT INTO fuel_types (fuel_type_id, fuel_name) VALUES
    (1, 'Petrol'),
    (2, 'Diesel'),
    (3, 'CNG'),
    (4, 'Electric');

-- Licence classes
INSERT INTO licence_classes (licence_class_id, class_code, description) VALUES
    (1, 'MC50CC', 'Motorcycle up to 50cc'),
    (2, 'MCWOG',  'Motorcycle without gear'),
    (3, 'LMV',    'Light Motor Vehicle'),
    (4, 'HMV',    'Heavy Motor Vehicle'),
    (5, 'TRANS',  'Transport Vehicle');

-- Document types
INSERT INTO document_types (document_type_id, type_name, is_mandatory_default, validity_period_days) VALUES
    (1, 'Aadhaar Card',         TRUE,  NULL),
    (2, 'Passport Photo',       TRUE,  NULL),
    (3, 'Address Proof',        TRUE,  NULL),
    (4, 'Medical Certificate',  FALSE, 365);

-- Service types
INSERT INTO service_types (service_type_id, service_name, service_code, base_fee, sla_days) VALUES
    (1, 'New Driving Licence',      'NEW_DL',            500.00, 14),
    (2, 'Driving Licence Renewal',  'RENEWAL_DL',        300.00, 7),
    (3, 'Ownership Transfer',       'OWNERSHIP_TRANSFER',750.00, 21),
    (4, 'New Commercial Permit',    'NEW_PERMIT',        1000.00, 30),
    (5, 'New Vehicle Registration', 'NEW_VEHICLE_REG',   800.00, 7);

-- Violation types
INSERT INTO violation_types (violation_type_id, description, base_fine_amount, is_cognizable) VALUES
    (1, 'Over Speeding',           1000.00, FALSE),
    (2, 'Driving Without Licence',  500.00, TRUE),
    (3, 'Signal Jumping',           200.00, FALSE),
    (4, 'Drunk Driving',           5000.00, TRUE),
    (5, 'Mobile Phone While Driving', 1500.00, FALSE);

-- Permit types
INSERT INTO permit_types (permit_type_id, type_name, validity_months) VALUES
    (1, 'State Permit',    12),
    (2, 'National Permit', 12),
    (3, 'Temporary Permit', 1);

-- Payable types (required by the payment engine)
INSERT INTO payable_types (payable_type_id, type_name) VALUES
    (1, 'APPLICATION'),
    (2, 'CHALLAN'),
    (3, 'PERMIT'),
    (4, 'ROAD_TAX');

-- ============================================================
-- SECTION 2: OFFICE STRUCTURE
-- ============================================================

-- RTO Offices
INSERT INTO rto_offices (office_id, region_id, office_name, office_code, address_line, is_active) VALUES
    (1, 3, 'RTO Ahmedabad Central',  'GJ-01', 'Nr. Sakar III, Income Tax, Ahmedabad 380014', TRUE),
    (2, 4, 'RTO Surat City',         'GJ-21', 'Udhna Darwaja, Surat 395002',                TRUE);

-- Departments
INSERT INTO departments (department_id, department_name) VALUES
    (1, 'Licensing'),
    (2, 'Vehicle Registration'),
    (3, 'Enforcement');

-- Designations
INSERT INTO designations (designation_id, title, rank_level) VALUES
    (1, 'Regional Transport Officer',         1),
    (2, 'Motor Vehicle Inspector',            2),
    (3, 'Assistant Motor Vehicle Inspector',  3);

-- Counters
INSERT INTO counters (counter_id, office_id, counter_number, service_category) VALUES
    (1, 1, 'C-01', 'Licensing'),
    (2, 1, 'C-02', 'Vehicle Registration'),
    (3, 2, 'C-01', 'Licensing'),
    (4, 2, 'C-02', 'Enforcement');

-- Test centres
INSERT INTO test_centres (test_centre_id, office_id, centre_name) VALUES
    (1, 1, 'Ahmedabad Central Test Track'),
    (2, 2, 'Surat Driving Test Centre');

-- ============================================================
-- SECTION 3: IDENTITY — PERSONS
-- ============================================================
-- 15 persons: 10 will be citizens, 5 will be employees (some may overlap as citizens too).

INSERT INTO persons
    (person_id, first_name, last_name, date_of_birth, gender,
     national_id_number, phone_primary, phone_secondary, email,
     created_at, updated_at)
VALUES
-- Citizens (persons 1–10)
    (1,  'Aarav',   'Mehta',    '1990-03-14', 'MALE',   'AADH-100000001', '9876543210', NULL,         'aarav.mehta@example.com',   NOW(), NOW()),
    (2,  'Priya',   'Sharma',   '1985-07-22', 'FEMALE', 'AADH-100000002', '9876543211', '9876543299', 'priya.sharma@example.com',  NOW(), NOW()),
    (3,  'Rohan',   'Patel',    '1992-11-05', 'MALE',   'AADH-100000003', '9876543212', NULL,         'rohan.patel@example.com',   NOW(), NOW()),
    (4,  'Sunita',  'Desai',    '1988-01-30', 'FEMALE', 'AADH-100000004', '9876543213', NULL,         'sunita.desai@example.com',  NOW(), NOW()),
    (5,  'Kiran',   'Joshi',    '1995-06-18', 'MALE',   'AADH-100000005', '9876543214', NULL,         'kiran.joshi@example.com',   NOW(), NOW()),
    (6,  'Pooja',   'Singh',    '1993-09-09', 'FEMALE', 'AADH-100000006', '9876543215', NULL,         'pooja.singh@example.com',   NOW(), NOW()),
    (7,  'Amit',    'Verma',    '1980-12-25', 'MALE',   'AADH-100000007', '9876543216', NULL,         'amit.verma@example.com',    NOW(), NOW()),
    (8,  'Neha',    'Gupta',    '1997-04-02', 'FEMALE', 'AADH-100000008', '9876543217', NULL,         'neha.gupta@example.com',    NOW(), NOW()),
    (9,  'Vijay',   'Shah',     '1975-08-15', 'MALE',   'AADH-100000009', '9876543218', NULL,         'vijay.shah@example.com',    NOW(), NOW()),
    (10, 'Meera',   'Nair',     '1999-02-28', 'FEMALE', 'AADH-100000010', '9876543219', NULL,         'meera.nair@example.com',    NOW(), NOW()),
-- Employees (persons 11–15)
    (11, 'Dinesh',  'Rao',      '1978-05-20', 'MALE',   'AADH-100000011', '9000000001', NULL,         'dinesh.rao@rto.gov.in',     NOW(), NOW()),
    (12, 'Kavita',  'Iyer',     '1982-10-11', 'FEMALE', 'AADH-100000012', '9000000002', NULL,         'kavita.iyer@rto.gov.in',    NOW(), NOW()),
    (13, 'Suresh',  'Pillai',   '1970-03-03', 'MALE',   'AADH-100000013', '9000000003', NULL,         'suresh.pillai@rto.gov.in',  NOW(), NOW()),
    (14, 'Anita',   'Bose',     '1985-07-07', 'FEMALE', 'AADH-100000014', '9000000004', NULL,         'anita.bose@rto.gov.in',     NOW(), NOW()),
    (15, 'Rajan',   'Kulkarni', '1968-11-30', 'MALE',   'AADH-100000015', '9000000005', NULL,         'rajan.kulkarni@rto.gov.in', NOW(), NOW());

-- Addresses (current address per person)
INSERT INTO addresses
    (address_id, person_id, line1, line2, city, state, pincode, address_type, is_current, valid_from, valid_to)
VALUES
    (1,  1,  'A-12 Paldi Society', NULL,       'Ahmedabad', 'Gujarat', '380007', 'CURRENT',   TRUE, '2020-01-01', NULL),
    (2,  2,  'B-45 Navrangpura',   'Nr. HL College', 'Ahmedabad', 'Gujarat', '380009', 'CURRENT', TRUE, '2019-06-01', NULL),
    (3,  3,  'C-78 Bopal',         NULL,       'Ahmedabad', 'Gujarat', '380058', 'CURRENT',   TRUE, '2021-03-15', NULL),
    (4,  4,  'D-22 Satellite',     NULL,       'Ahmedabad', 'Gujarat', '380015', 'CURRENT',   TRUE, '2018-09-01', NULL),
    (5,  5,  'E-33 Vastrapur',     NULL,       'Ahmedabad', 'Gujarat', '380054', 'CURRENT',   TRUE, '2022-01-01', NULL),
    (6,  6,  'F-11 Adajan',        NULL,       'Surat',     'Gujarat', '395009', 'CURRENT',   TRUE, '2020-07-01', NULL),
    (7,  7,  'G-55 Ring Road',     NULL,       'Surat',     'Gujarat', '395002', 'CURRENT',   TRUE, '2017-04-01', NULL),
    (8,  8,  'H-99 Vesu',          NULL,       'Surat',     'Gujarat', '395007', 'CURRENT',   TRUE, '2023-02-01', NULL),
    (9,  9,  'I-14 Athwa',         NULL,       'Surat',     'Gujarat', '395001', 'CURRENT',   TRUE, '2015-11-01', NULL),
    (10, 10, 'J-66 Katargam',      NULL,       'Surat',     'Gujarat', '395004', 'CURRENT',   TRUE, '2024-01-01', NULL),
    (11, 11, 'RTO Colony Block A', NULL,       'Ahmedabad', 'Gujarat', '380014', 'CURRENT',   TRUE, '2010-01-01', NULL),
    (12, 12, 'RTO Colony Block B', NULL,       'Ahmedabad', 'Gujarat', '380014', 'CURRENT',   TRUE, '2012-01-01', NULL),
    (13, 13, 'RTO Surat Quarters', NULL,       'Surat',     'Gujarat', '395002', 'CURRENT',   TRUE, '2008-01-01', NULL),
    (14, 14, 'RTO Surat Quarters', 'B Wing',   'Surat',     'Gujarat', '395002', 'CURRENT',   TRUE, '2011-01-01', NULL),
    (15, 15, 'RTO Ahmedabad HQ',   NULL,       'Ahmedabad', 'Gujarat', '380014', 'CURRENT',   TRUE, '2005-01-01', NULL);

-- Citizens (10 citizens mapped to persons 1–10)
INSERT INTO citizens
    (citizen_id, person_id, citizen_code, blacklisted, blacklist_reason, registered_at)
VALUES
    (1,  1,  'CIT-AHM-00001', FALSE, NULL, '2020-01-05 10:00:00'),
    (2,  2,  'CIT-AHM-00002', FALSE, NULL, '2019-06-10 11:30:00'),
    (3,  3,  'CIT-AHM-00003', FALSE, NULL, '2021-03-20 09:15:00'),
    (4,  4,  'CIT-AHM-00004', FALSE, NULL, '2018-09-05 14:00:00'),
    (5,  5,  'CIT-AHM-00005', FALSE, NULL, '2022-01-15 10:30:00'),
    (6,  6,  'CIT-SUR-00001', FALSE, NULL, '2020-07-10 09:00:00'),
    (7,  7,  'CIT-SUR-00002', TRUE,  'Multiple unresolved challans', '2017-04-15 16:00:00'),
    (8,  8,  'CIT-SUR-00003', FALSE, NULL, '2023-02-05 11:00:00'),
    (9,  9,  'CIT-SUR-00004', FALSE, NULL, '2015-11-20 10:00:00'),
    (10, 10, 'CIT-SUR-00005', FALSE, NULL, '2024-01-10 09:30:00');

-- ============================================================
-- SECTION 4: EMPLOYEES
-- ============================================================

INSERT INTO employees
    (employee_id, person_id, employee_code, designation_id, date_joined, is_active)
VALUES
    (1, 11, 'EMP-AHM-001', 1, '2010-02-01', TRUE),  -- RTO Ahmedabad: RTO officer
    (2, 12, 'EMP-AHM-002', 2, '2012-06-01', TRUE),  -- Ahmedabad: MVI
    (3, 13, 'EMP-SUR-001', 1, '2008-04-01', TRUE),  -- RTO Surat: RTO officer
    (4, 14, 'EMP-SUR-002', 3, '2011-08-01', TRUE),  -- Surat: AMVI
    (5, 15, 'EMP-AHM-003', 2, '2005-01-15', TRUE);  -- Ahmedabad: senior MVI

INSERT INTO employee_postings
    (posting_id, employee_id, office_id, department_id, posted_from, posted_to)
VALUES
    (1, 1, 1, 1, '2010-02-01', NULL),   -- Employee 1 at Ahmedabad, Licensing (current)
    (2, 2, 1, 2, '2012-06-01', NULL),   -- Employee 2 at Ahmedabad, Vehicle Reg (current)
    (3, 3, 2, 1, '2008-04-01', NULL),   -- Employee 3 at Surat, Licensing (current)
    (4, 4, 2, 3, '2011-08-01', NULL),   -- Employee 4 at Surat, Enforcement (current)
    (5, 5, 1, 1, '2005-01-15', NULL);   -- Employee 5 at Ahmedabad, Licensing (current)

-- ============================================================
-- SECTION 5: RBAC
-- ============================================================

-- Users (one per person, first 15)
INSERT INTO users (user_id, person_id, username, password_hash, is_active, last_login_at) VALUES
    (1,  1,  'aarav.mehta',    '$2a$12$citizenhashAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA', TRUE, '2026-10-01 08:30:00'),
    (2,  2,  'priya.sharma',   '$2a$12$citizenhashBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB', TRUE, '2026-10-02 09:00:00'),
    (3,  3,  'rohan.patel',    '$2a$12$citizenhashCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC', TRUE, '2026-10-03 10:00:00'),
    (4,  4,  'sunita.desai',   '$2a$12$citizenhashDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD', TRUE, '2026-09-25 11:00:00'),
    (5,  5,  'kiran.joshi',    '$2a$12$citizenhashEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE', TRUE, '2026-10-07 07:45:00'),
    (6,  6,  'pooja.singh',    '$2a$12$citizenhashFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF', TRUE, NULL),
    (7,  7,  'amit.verma',     '$2a$12$citizenhashGGGGGGGGGGGGGGGGGGGGGGGGGGGGGG', TRUE, '2026-08-20 14:00:00'),
    (8,  8,  'neha.gupta',     '$2a$12$citizenhashHHHHHHHHHHHHHHHHHHHHHHHHHHHHHH', TRUE, '2026-10-06 16:00:00'),
    (9,  9,  'vijay.shah',     '$2a$12$citizenhashIIIIIIIIIIIIIIIIIIIIIIIIIIIIII', TRUE, '2026-10-05 08:00:00'),
    (10, 10, 'meera.nair',     '$2a$12$citizenhashJJJJJJJJJJJJJJJJJJJJJJJJJJJJJJ', TRUE, NULL),
    (11, 11, 'dinesh.rao',     '$2a$12$employeehashKKKKKKKKKKKKKKKKKKKKKKKKKKKKKK', TRUE, '2026-10-08 08:00:00'),
    (12, 12, 'kavita.iyer',    '$2a$12$employeehashLLLLLLLLLLLLLLLLLLLLLLLLLLLLLL', TRUE, '2026-10-08 08:05:00'),
    (13, 13, 'suresh.pillai',  '$2a$12$employeehashMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM', TRUE, '2026-10-08 08:10:00'),
    (14, 14, 'anita.bose',     '$2a$12$employeehashNNNNNNNNNNNNNNNNNNNNNNNNNNNNNN', TRUE, '2026-10-07 17:00:00'),
    (15, 15, 'rajan.kulkarni', '$2a$12$employeehashOOOOOOOOOOOOOOOOOOOOOOOOOOOOOO', TRUE, '2026-10-08 07:55:00');

-- Roles
INSERT INTO roles (role_id, role_name) VALUES
    (1, 'CITIZEN'),
    (2, 'OFFICER'),
    (3, 'INSPECTOR'),
    (4, 'ADMIN'),
    (5, 'ENFORCER');

-- Permissions (key set mirroring ApplicationService.PERMISSION_FOR_TARGET etc.)
INSERT INTO permissions (permission_id, permission_key) VALUES
    (1,  'application.create'),
    (2,  'application.create_own'),
    (3,  'application.view'),
    (4,  'application.view_own'),
    (5,  'application.verify'),
    (6,  'application.approve'),
    (7,  'application.reject'),
    (8,  'application.cancel'),
    (9,  'vehicle.view'),
    (10, 'vehicle.register'),
    (11, 'licence.view'),
    (12, 'licence.issue'),
    (13, 'challan.issue'),
    (14, 'challan.view'),
    (15, 'payment.view'),
    (16, 'rbac.admin');

-- Role ↔ Permission assignments
INSERT INTO role_permissions (role_id, permission_id) VALUES
    -- CITIZEN
    (1, 2), (1, 4), (1, 8), (1, 9), (1, 11), (1, 14), (1, 15),
    -- OFFICER
    (2, 1), (2, 3), (2, 5), (2, 6), (2, 7), (2, 8),
    (2, 9), (2, 10), (2, 11), (2, 12), (2, 14), (2, 15),
    -- INSPECTOR
    (3, 3), (3, 9), (3, 11), (3, 12), (3, 14),
    -- ADMIN
    (4, 1), (4, 3), (4, 5), (4, 6), (4, 7), (4, 8),
    (4, 9), (4, 10), (4, 11), (4, 12), (4, 13), (4, 14),
    (4, 15), (4, 16),
    -- ENFORCER
    (5, 9), (5, 13), (5, 14);

-- User ↔ Role assignments
INSERT INTO user_roles (user_id, role_id) VALUES
    (1,  1), (2,  1), (3,  1), (4,  1), (5,  1),
    (6,  1), (7,  1), (8,  1), (9,  1), (10, 1),
    (11, 4),           -- Dinesh = ADMIN
    (12, 2),           -- Kavita = OFFICER
    (13, 2), (13, 3),  -- Suresh = OFFICER + INSPECTOR
    (14, 5),           -- Anita  = ENFORCER
    (15, 4), (15, 2);  -- Rajan  = ADMIN + OFFICER

-- ============================================================
-- SECTION 6: APPLICANTS AND APPLICATIONS
-- ============================================================

INSERT INTO applicants (applicant_id, citizen_id, preferred_office_id) VALUES
    (1,  1, 1),
    (2,  2, 1),
    (3,  3, 1),
    (4,  4, 1),
    (5,  5, 1),
    (6,  6, 2),
    (7,  8, 2),
    (8,  9, 2),
    (9, 10, 2);

-- Applications (various statuses to test views and queries)
INSERT INTO applications
    (application_id, application_number, applicant_id, service_type_id, office_id,
     assigned_officer_id, current_status, submitted_at, completed_at, remarks)
VALUES
    (1,  'APP-2026-00001', 1, 1, 1, 2, 'COMPLETED',          '2026-08-01 09:00:00', '2026-08-12 15:00:00', 'New DL issued'),
    (2,  'APP-2026-00002', 2, 2, 1, 2, 'APPROVED',           '2026-09-01 10:00:00', NULL,                  NULL),
    (3,  'APP-2026-00003', 3, 1, 1, 5, 'UNDER_VERIFICATION', '2026-09-15 11:00:00', NULL,                  NULL),
    (4,  'APP-2026-00004', 4, 5, 1, 2, 'AWAITING_PAYMENT',   '2026-09-20 14:00:00', NULL,                  NULL),
    (5,  'APP-2026-00005', 5, 3, 1, NULL, 'SUBMITTED',       '2026-10-01 09:30:00', NULL,                  NULL),
    (6,  'APP-2026-00006', 6, 1, 2, 3, 'COMPLETED',          '2026-07-01 09:00:00', '2026-07-10 16:00:00', 'Surat DL issued'),
    (7,  'APP-2026-00007', 7, 5, 2, 3, 'DOCS_PENDING',       '2026-10-05 10:00:00', NULL,                  'Missing address proof'),
    (8,  'APP-2026-00008', 8, 2, 2, 3, 'REJECTED',           '2026-09-10 11:00:00', NULL,                  'Documents expired'),
    (9,  'APP-2026-00009', 9, 4, 2, 3, 'SUBMITTED',          '2026-10-07 08:00:00', NULL,                  NULL),
    -- Transfer application: from citizen 9 → citizen 10
    (10, 'APP-2026-00010', 8, 3, 2, 3, 'APPROVED',           '2026-09-25 09:00:00', NULL,                  'Ownership transfer approved');

-- Application status histories (pre-populate for LAG() window function demo)
INSERT INTO application_status_history
    (history_id, application_id, previous_status, new_status, changed_by_user_id, changed_at, reason)
VALUES
    -- App 1 (COMPLETED): full lifecycle
    (1,  1, NULL,                 'SUBMITTED',          1,  '2026-08-01 09:00:00', 'Application submitted'),
    (2,  1, 'SUBMITTED',          'UNDER_VERIFICATION', 12, '2026-08-02 09:30:00', NULL),
    (3,  1, 'UNDER_VERIFICATION', 'AWAITING_PAYMENT',   12, '2026-08-05 11:00:00', NULL),
    (4,  1, 'AWAITING_PAYMENT',   'APPROVED',           12, '2026-08-10 14:00:00', NULL),
    (5,  1, 'APPROVED',           'COMPLETED',          12, '2026-08-12 15:00:00', 'DL issued'),
    -- App 3 (UNDER_VERIFICATION)
    (6,  3, NULL,                 'SUBMITTED',          3,  '2026-09-15 11:00:00', 'Application submitted'),
    (7,  3, 'SUBMITTED',          'DOCS_PENDING',       15, '2026-09-16 10:00:00', 'Additional doc needed'),
    (8,  3, 'DOCS_PENDING',       'UNDER_VERIFICATION', 15, '2026-09-18 14:00:00', NULL),
    -- App 6 (COMPLETED): Surat office
    (9,  6, NULL,                 'SUBMITTED',          6,  '2026-07-01 09:00:00', 'Application submitted'),
    (10, 6, 'SUBMITTED',          'UNDER_VERIFICATION', 13, '2026-07-03 10:00:00', NULL),
    (11, 6, 'UNDER_VERIFICATION', 'AWAITING_PAYMENT',   13, '2026-07-07 11:00:00', NULL),
    (12, 6, 'AWAITING_PAYMENT',   'APPROVED',           13, '2026-07-09 14:00:00', NULL),
    (13, 6, 'APPROVED',           'COMPLETED',          13, '2026-07-10 16:00:00', 'DL issued Surat'),
    -- App 8 (REJECTED)
    (14, 8, NULL,                 'SUBMITTED',          8,  '2026-09-10 11:00:00', 'Application submitted'),
    (15, 8, 'SUBMITTED',          'DOCS_PENDING',       13, '2026-09-11 09:00:00', 'Documents expired'),
    (16, 8, 'DOCS_PENDING',       'REJECTED',           13, '2026-09-13 15:00:00', 'Documents expired and not replaced');

-- ============================================================
-- SECTION 7: APPOINTMENT SLOTS AND APPOINTMENTS
-- ============================================================

INSERT INTO appointment_slots
    (slot_id, office_id, counter_id, slot_date, start_time, end_time, capacity, booked_count)
VALUES
    (1, 1, 1, '2026-10-15', '09:00:00', '09:30:00', 5, 1),
    (2, 1, 1, '2026-10-15', '09:30:00', '10:00:00', 5, 0),
    (3, 2, 3, '2026-10-16', '10:00:00', '10:30:00', 3, 1);

INSERT INTO appointments
    (appointment_id, application_id, slot_id, token_number, status, booked_at)
VALUES
    (1, 3, 1, 'TKN-0001', 'BOOKED',    '2026-09-20 11:00:00'),
    (2, 7, 3, 'TKN-0002', 'BOOKED',    '2026-10-06 10:00:00');

-- ============================================================
-- SECTION 8: DOCUMENTS
-- ============================================================

INSERT INTO documents
    (document_id, application_id, document_type_id, file_path, verification_status,
     verified_by_employee_id, rejection_reason, uploaded_at, verified_at)
VALUES
    (1, 1, 1, 'storage/app1/aadhaar.pdf',   'VERIFIED', 2, NULL,              '2026-08-01 09:30:00', '2026-08-02 09:00:00'),
    (2, 1, 2, 'storage/app1/photo.jpg',     'VERIFIED', 2, NULL,              '2026-08-01 09:31:00', '2026-08-02 09:05:00'),
    (3, 3, 1, 'storage/app3/aadhaar.pdf',   'VERIFIED', 5, NULL,              '2026-09-15 11:30:00', '2026-09-18 10:00:00'),
    (4, 7, 3, 'storage/app7/address.pdf',   'REJECTED', 4, 'Address mismatch','2026-10-05 10:30:00', '2026-10-06 09:00:00');

-- ============================================================
-- SECTION 9: DRIVING LICENCES
-- ============================================================

INSERT INTO driving_licences
    (driving_licence_id, licence_number, citizen_id, learner_licence_id,
     application_id, office_id, issue_date, expiry_date, current_status)
VALUES
    (1, 'GJ01-20260001', 1, NULL, 1, 1, '2026-08-12', '2036-08-12', 'ACTIVE'),
    (2, 'GJ21-20260001', 6, NULL, 6, 2, '2026-07-10', '2036-07-10', 'ACTIVE'),
    (3, 'GJ01-20240010', 9, NULL, 8, 1, '2024-01-15', '2034-01-15', 'SUSPENDED');

-- Licence class assignments
INSERT INTO licence_class_assignments (driving_licence_id, licence_class_id, granted_on) VALUES
    (1, 3, '2026-08-12'),   -- Aarav: LMV
    (2, 3, '2026-07-10'),   -- Pooja: LMV
    (2, 2, '2026-07-10'),   -- Pooja: MCWOG (two classes)
    (3, 3, '2024-01-15'),   -- Vijay: LMV (now suspended)
    (3, 4, '2024-01-15');   -- Vijay: HMV

-- Licence status history (populated by trigger trg_after_licence_status_change,
-- but pre-seeded here so window queries have data before the trigger fires)
INSERT INTO licence_status_history
    (history_id, driving_licence_id, previous_status, new_status, changed_by_user_id, changed_at, reason)
VALUES
    (1, 3, 'ACTIVE', 'SUSPENDED', 15, '2026-03-01 10:00:00', 'Multiple unresolved challans');

-- Driving tests
INSERT INTO driving_tests
    (test_id, application_id, citizen_id, test_centre_id, examiner_employee_id,
     scheduled_at, result, remarks)
VALUES
    (1, 1, 1, 1, 5, '2026-08-08 10:00:00', 'PASS', 'Excellent control'),
    (2, 6, 6, 2, 3, '2026-07-07 09:00:00', 'PASS', 'Good performance'),
    (3, 3, 3, 1, 2, '2026-09-25 11:00:00', 'PENDING', NULL),
    (4, 8, 9, 1, 5, '2026-05-10 09:00:00', 'FAIL',  'Failed to stop at signal'),
    (5, 8, 9, 1, 5, '2026-06-14 10:00:00', 'PASS',  'Passed on retry');

-- ============================================================
-- SECTION 10: VEHICLES AND OWNERSHIP
-- ============================================================

INSERT INTO vehicles
    (vehicle_id, registration_number, chassis_number, engine_number,
     manufacturer_id, model_id, vehicle_type_id, fuel_type_id,
     manufacture_year, color, registering_office_id, registration_date, status)
VALUES
    (1, 'GJ01AB1234', 'CH-SWIFT-10001', 'EN-SWIFT-10001', 1, 1, 2, 1, 2022, 'White',  1, '2022-03-15', 'ACTIVE'),
    (2, 'GJ01CD5678', 'CH-HERO-20002',  'EN-HERO-20002',  2, 3, 1, 1, 2021, 'Black',  1, '2021-07-20', 'ACTIVE'),
    (3, 'GJ21EF9012', 'CH-TATA-30003',  'EN-TATA-30003',  3, 5, 2, 2, 2020, 'Blue',   2, '2020-11-10', 'ACTIVE'),
    (4, 'GJ21GH3456', 'CH-MAHI-40004',  'EN-MAHI-40004',  4, 7, 3, 2, 2018, 'Red',    2, '2018-05-25', 'ACTIVE'),
    (5, 'GJ01IJ7890', 'CH-SWFT-50005',  'EN-SWFT-50005',  1, 2, 2, 1, 2023, 'Silver', 1, '2023-01-05', 'BLACKLISTED');

-- Vehicle ownerships (effective_to = NULL → current owner)
INSERT INTO vehicle_ownerships
    (ownership_id, vehicle_id, citizen_id, effective_from, effective_to)
VALUES
    (1, 1, 1, '2022-03-15', NULL),   -- Vehicle 1: Aarav (current)
    (2, 2, 4, '2021-07-20', NULL),   -- Vehicle 2: Sunita (current)
    (3, 3, 6, '2020-11-10', '2026-09-30'), -- Vehicle 3: Pooja (closed — transferred)
    (4, 3, 9, '2026-10-01', NULL),   -- Vehicle 3: Vijay (current after transfer)
    (5, 4, 7, '2018-05-25', NULL),   -- Vehicle 4: Amit (blacklisted citizen)
    (6, 5, 9, '2023-01-05', NULL);   -- Vehicle 5: Vijay (blacklisted vehicle)

-- Ownership transfers
INSERT INTO ownership_transfers
    (transfer_id, vehicle_id, from_citizen_id, to_citizen_id,
     application_id, requested_at, approved_at, status)
VALUES
    (1, 3, 6, 9, 10, '2026-09-25 09:00:00', '2026-09-30 14:00:00', 'APPROVED'),
    (2, 1, 1, 5, 5,  '2026-10-01 09:30:00', NULL,                  'PENDING');

-- ============================================================
-- SECTION 11: COMPLIANCE
-- ============================================================

-- Vehicle inspections
INSERT INTO vehicle_inspections
    (inspection_id, vehicle_id, inspector_employee_id, inspected_at, result, remarks)
VALUES
    (1, 1, 2, '2024-03-10 10:00:00', 'PASS', 'All systems OK'),
    (2, 3, 4, '2024-06-15 11:00:00', 'PASS', 'Passed fitness test'),
    (3, 4, 4, '2025-01-20 09:00:00', 'FAIL', 'Brake system failure');

-- Fitness certificates
INSERT INTO fitness_certificates
    (certificate_id, vehicle_id, inspection_id, issue_date, expiry_date, status)
VALUES
    (1, 1, 1, '2024-03-10', '2026-03-10', 'EXPIRED'),   -- already expired
    (2, 3, 2, '2024-06-15', '2026-10-20', 'ACTIVE'),    -- expiring in ~12 days (triggers expiry view)
    (3, 4, NULL, '2025-02-01', '2027-02-01', 'ACTIVE');

-- Pollution certificates
INSERT INTO pollution_certificates
    (puc_id, vehicle_id, issue_date, expiry_date, status)
VALUES
    (1, 1, '2026-07-01', '2026-10-15', 'ACTIVE'),   -- expiring in 7 days
    (2, 2, '2026-04-01', '2026-07-01', 'EXPIRED'),
    (3, 3, '2026-09-01', '2026-12-01', 'ACTIVE'),
    (4, 4, '2026-01-01', '2026-04-01', 'EXPIRED');

-- Insurance policies
INSERT INTO insurance_policies
    (policy_id, vehicle_id, provider_name, policy_number, start_date, end_date)
VALUES
    (1, 1, 'New India Assurance',  'NIA-GJ-2024-001', '2024-03-15', '2026-10-25'),  -- expiring ~17 days
    (2, 2, 'Bajaj Allianz',        'BAJ-GJ-2021-002', '2025-07-20', '2026-07-20'),  -- expired
    (3, 3, 'HDFC Ergo',            'HDF-GJ-2020-003', '2026-01-01', '2027-01-01'),
    (4, 5, 'Oriental Insurance',   'ORI-GJ-2023-004', '2023-01-05', '2025-01-05');  -- expired

-- Road tax records
INSERT INTO road_tax_records
    (tax_record_id, vehicle_id, assessment_year, amount_due, due_date, status)
VALUES
    (1, 1, 2026, 5000.00, '2026-03-31', 'PAID'),
    (2, 2, 2026, 3000.00, '2026-03-31', 'PAID'),
    (3, 3, 2026, 5000.00, '2026-03-31', 'OVERDUE'),  -- past due, not paid
    (4, 4, 2026, 8000.00, '2026-03-31', 'DUE'),      -- still due (will flip to OVERDUE via sp/trigger)
    (5, 5, 2026, 5000.00, '2026-03-31', 'OVERDUE'),
    (6, 1, 2025, 4500.00, '2025-03-31', 'PAID');

-- ============================================================
-- SECTION 12: ROUTES AND PERMITS
-- ============================================================

INSERT INTO routes (route_id, route_name, origin, destination) VALUES
    (1, 'Ahmedabad – Surat Express', 'Ahmedabad', 'Surat'),
    (2, 'Ahmedabad – Vadodara',      'Ahmedabad', 'Vadodara');

INSERT INTO route_segments (segment_id, route_id, sequence_no, segment_name) VALUES
    (1, 1, 1, 'Ahmedabad'),
    (2, 1, 2, 'Anand'),
    (3, 1, 3, 'Vadodara'),
    (4, 1, 4, 'Bharuch'),
    (5, 1, 5, 'Surat');

INSERT INTO permits
    (permit_id, permit_number, vehicle_id, citizen_id, permit_type_id,
     route_id, application_id, issue_date, expiry_date, status)
VALUES
    (1, 'PERM-2026-001', 4, 7, 1, 1, 9, '2026-01-15', '2027-01-15', 'ACTIVE'),
    (2, 'PERM-2026-002', 3, 9, 2, 1, 6, '2025-10-01', '2026-10-01', 'EXPIRED');

-- Permit status history (seeded; additional entries auto-generated by trigger)
INSERT INTO permit_status_history
    (history_id, permit_id, previous_status, new_status, changed_at, reason)
VALUES
    (1, 2, 'ACTIVE', 'EXPIRED', '2026-10-01 00:00:00', 'Permit validity lapsed');

-- ============================================================
-- SECTION 13: VIOLATIONS AND CHALLANS
-- ============================================================

-- Violations (attached to vehicles, some with known driver)
INSERT INTO violations
    (violation_id, vehicle_id, driver_citizen_id, violation_type_id,
     officer_employee_id, location, occurred_at)
VALUES
    (1, 2, 4, 1, 4, 'SG Highway, Ahmedabad', '2026-06-10 08:30:00'),  -- Over Speeding, Sunita
    (2, 2, 4, 3, 4, 'SG Highway, Ahmedabad', '2026-06-10 08:30:00'),  -- Signal Jump, same event
    (3, 4, 7, 4, 4, 'Varachha Road, Surat',   '2026-04-15 22:00:00'), -- Drunk Driving, Amit
    (4, 3, NULL, 2, 4, 'Adajan, Surat',        '2026-08-20 14:00:00'), -- No Licence (driver unknown)
    (5, 1, 1, 5, 4, 'Ashram Road, Ahmedabad', '2026-09-05 17:30:00'); -- Mobile phone, Aarav

-- Challans (multiple violations can be on one challan)
INSERT INTO challans
    (challan_id, challan_number, total_amount, status, issued_at)
VALUES
    (1, 'CHAL-2026-001', 1200.00, 'PAID',     '2026-06-10 09:00:00'),  -- Violations 1+2
    (2, 'CHAL-2026-002', 5000.00, 'ISSUED',   '2026-04-15 23:00:00'),  -- Violation 3 (outstanding)
    (3, 'CHAL-2026-003',  500.00, 'ISSUED',   '2026-08-20 14:30:00'),  -- Violation 4
    (4, 'CHAL-2026-004', 1500.00, 'DISPUTED', '2026-09-05 18:00:00'); -- Violation 5

-- Challan ↔ Violation mapping
INSERT INTO challan_violations (challan_id, violation_id) VALUES
    (1, 1), (1, 2),   -- Two violations in one challan
    (2, 3),
    (3, 4),
    (4, 5);

-- Challan status histories
INSERT INTO challan_status_history
    (history_id, challan_id, previous_status, new_status, changed_at)
VALUES
    (1, 1, 'ISSUED', 'PAID',      '2026-06-15 10:00:00'),
    (2, 4, 'ISSUED', 'DISPUTED',  '2026-09-10 09:00:00');

-- ============================================================
-- SECTION 14: PAYMENTS
-- ============================================================

-- Payment for Application 1 (DL fee)
INSERT INTO payments
    (payment_id, receipt_number, payable_type_id, payable_id, amount,
     status, paid_at, created_at)
VALUES
    (1, 'RCT-2026-00001', 1, 1, 500.00, 'SUCCESS', '2026-08-09 14:00:00', '2026-08-09 13:55:00'),
    (2, 'RCT-2026-00002', 1, 4, 800.00, 'PENDING', NULL,                  '2026-09-20 14:05:00'),
    (3, 'RCT-2026-00003', 2, 1, 1200.00,'SUCCESS', '2026-06-15 09:50:00', '2026-06-15 09:45:00'),
    (4, 'RCT-2026-00004', 4, 1, 5000.00,'SUCCESS', '2026-04-01 11:00:00', '2026-04-01 10:55:00'),
    (5, 'RCT-2026-00005', 1, 6, 500.00, 'SUCCESS', '2026-07-09 14:00:00', '2026-07-09 13:55:00');

-- Payment attempts
INSERT INTO payment_attempts
    (attempt_id, payment_id, attempt_number, gateway_reference, outcome, attempted_at)
VALUES
    (1, 1, 1, 'GW-2026-000001', 'SUCCESS', '2026-08-09 14:00:00'),
    (2, 2, 1, 'GW-2026-000002', 'TIMEOUT', '2026-09-20 14:06:00'),
    (3, 3, 1, 'GW-2026-000003', 'SUCCESS', '2026-06-15 09:50:00'),
    (4, 4, 1, 'GW-2026-000004', 'SUCCESS', '2026-04-01 11:00:00'),
    (5, 5, 1, 'GW-2026-000005', 'SUCCESS', '2026-07-09 14:00:00');

-- Fee structures (processing + smart card components for NEW_DL)
INSERT INTO fee_structures
    (fee_id, service_type_id, component_name, amount, effective_from, effective_to)
VALUES
    (1, 1, 'Processing Fee',  300.00, '2024-04-01', NULL),
    (2, 1, 'Smart Card Fee',  200.00, '2024-04-01', NULL),
    (3, 2, 'Renewal Fee',     300.00, '2024-04-01', NULL),
    (4, 3, 'Transfer Fee',    750.00, '2024-04-01', NULL),
    (5, 5, 'Registration Fee',800.00, '2024-04-01', NULL);

-- ============================================================
-- SECTION 15: SUPPORT — NOTIFICATIONS, COMPLAINTS, APPEALS
-- ============================================================

INSERT INTO notifications
    (notification_id, person_id, channel, subject, message, status, sent_at)
VALUES
    (1,  1, 'SMS',   NULL,                         'Your DL application APP-2026-00001 has been COMPLETED.',   'SENT',   '2026-08-12 15:05:00'),
    (2,  2, 'EMAIL', 'Application Approved',         'Your renewal APP-2026-00002 has been APPROVED.',           'SENT',   '2026-09-01 10:05:00'),
    (3,  3, 'SMS',   NULL,                         'Your application APP-2026-00003 is UNDER_VERIFICATION.',   'SENT',   '2026-09-18 14:05:00'),
    (4,  4, 'SMS',   NULL,                         'Your application APP-2026-00004 is AWAITING_PAYMENT.',     'SENT',   '2026-09-20 14:05:00'),
    (5,  1, 'EMAIL', 'Challan Issued',               'A challan of 1500.00 has been issued for vehicle GJ01AB1234.', 'QUEUED', NULL);

INSERT INTO complaints
    (complaint_id, citizen_id, office_id, subject, description, status, filed_at)
VALUES
    (1, 8, 2, 'Slow service at Surat RTO',
       'Waited 3 hours to submit basic documents. Staff was unresponsive.',
       'OPEN',        '2026-10-05 12:00:00'),
    (2, 5, 1, 'Wrong fee collected',
       'Was charged extra fee beyond the official schedule.',
       'IN_PROGRESS', '2026-09-28 11:00:00');

INSERT INTO appeals
    (appeal_id, citizen_id, against_type, against_id, grounds, status, filed_at, resolved_at)
VALUES
    (1, 9, 'CHALLAN',              4, 'Challan issued at wrong vehicle. I was not driving.',
       'UNDER_REVIEW', '2026-09-15 09:00:00', NULL),
    (2, 8, 'APPLICATION_REJECTION', 8, 'Documents were valid; rejection was in error.',
       'DISMISSED',    '2026-09-20 10:00:00', '2026-09-25 14:00:00');

-- Re-enable FK checks.
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- Quick sanity check: count rows in key tables.
-- Run after loading seed data to confirm success.
-- ============================================================
SELECT 'persons'                AS tbl, COUNT(*) AS rows FROM persons
UNION ALL SELECT 'citizens',      COUNT(*) FROM citizens
UNION ALL SELECT 'employees',     COUNT(*) FROM employees
UNION ALL SELECT 'applications',  COUNT(*) FROM applications
UNION ALL SELECT 'vehicles',      COUNT(*) FROM vehicles
UNION ALL SELECT 'vehicle_ownerships', COUNT(*) FROM vehicle_ownerships
UNION ALL SELECT 'driving_licences', COUNT(*) FROM driving_licences
UNION ALL SELECT 'challans',      COUNT(*) FROM challans
UNION ALL SELECT 'payments',      COUNT(*) FROM payments
UNION ALL SELECT 'violations',    COUNT(*) FROM violations;
