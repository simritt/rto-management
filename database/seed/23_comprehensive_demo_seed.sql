-- ============================================================
-- RTO Management System - Comprehensive Demo Seed Data
-- Populates every domain, status, and filter with realistic data
-- ============================================================
USE rto_management;
SET FOREIGN_KEY_CHECKS = 0;

-- Clear existing data to maintain 100% relational integrity
DELETE FROM audit_logs;
DELETE FROM notifications;
DELETE FROM appeals;
DELETE FROM complaints;
DELETE FROM refunds;
DELETE FROM payment_attempts;
DELETE FROM payments;
DELETE FROM fee_structures;
DELETE FROM challan_violations;
DELETE FROM challan_status_history;
DELETE FROM challans;
DELETE FROM violations;
DELETE FROM permit_status_history;
DELETE FROM permits;
DELETE FROM route_segments;
DELETE FROM routes;
DELETE FROM road_tax_records;
DELETE FROM insurance_policies;
DELETE FROM pollution_certificates;
DELETE FROM fitness_certificates;
DELETE FROM vehicle_inspections;
DELETE FROM ownership_transfers;
DELETE FROM vehicle_ownerships;
DELETE FROM vehicles;
DELETE FROM licence_status_history;
DELETE FROM licence_class_assignments;
DELETE FROM driving_licences;
DELETE FROM driving_tests;
DELETE FROM learner_licences;
DELETE FROM driving_instructors;
DELETE FROM driving_schools;
DELETE FROM documents;
DELETE FROM appointments;
DELETE FROM appointment_slots;
DELETE FROM application_status_history;
DELETE FROM applications;
DELETE FROM applicants;
DELETE FROM employee_postings;
DELETE FROM employees;
DELETE FROM counters;
DELETE FROM test_centres;
DELETE FROM rto_offices;
DELETE FROM addresses;
DELETE FROM citizens;
DELETE FROM user_roles;
DELETE FROM role_permissions;
DELETE FROM users;
DELETE FROM persons;
DELETE FROM payable_types;
DELETE FROM permit_types;
DELETE FROM violation_types;
DELETE FROM service_types;
DELETE FROM document_types;
DELETE FROM licence_classes;
DELETE FROM fuel_types;
DELETE FROM vehicle_types;
DELETE FROM vehicle_models;
DELETE FROM vehicle_manufacturers;
DELETE FROM designations;
DELETE FROM departments;
DELETE FROM regions;
DELETE FROM roles;
DELETE FROM permissions;

-- 1. Regions (State -> Zone -> District/City)
INSERT INTO regions (region_id, region_name, region_code, parent_region_id) VALUES
  (1, 'Karnataka', 'KA', NULL),
  (2, 'Bengaluru Urban', 'KA-BLR', 1),
  (3, 'Bengaluru Rural', 'KA-BLRD', 1),
  (4, 'Mysuru District', 'KA-MYS', 1),
  (5, 'Gujarat', 'GJ', NULL),
  (6, 'Ahmedabad District', 'GJ-AHM', 5),
  (7, 'Surat District', 'GJ-SUR', 5);

-- 2. Departments & Designations
INSERT INTO departments (department_id, department_name) VALUES
  (1, 'Licensing'),
  (2, 'Vehicle Registration'),
  (3, 'Enforcement & Challans'),
  (4, 'Accounts & Revenue'),
  (5, 'Administration & Public Grievances');

INSERT INTO designations (designation_id, title, rank_level) VALUES
  (1, 'Regional Transport Officer', 1),
  (2, 'Motor Vehicle Inspector', 2),
  (3, 'Assistant Motor Vehicle Inspector', 3),
  (4, 'Superintendent', 4),
  (5, 'Cashier & Revenue Clerk', 5);

-- 3. Reference Tables
INSERT INTO vehicle_manufacturers (manufacturer_id, name) VALUES
  (1, 'Maruti Suzuki'),
  (2, 'Hyundai Motor'),
  (3, 'Tata Motors'),
  (4, 'Mahindra & Mahindra'),
  (5, 'Honda Cars India'),
  (6, 'Hero MotoCorp'),
  (7, 'Bajaj Auto'),
  (8, 'Toyota Kirloskar'),
  (9, 'TVS Motor Company'),
  (10, 'Ather Energy'),
  (11, 'Ashok Leyland');

INSERT INTO vehicle_models (model_id, manufacturer_id, model_name) VALUES
  (1, 1, 'Swift'),
  (2, 1, 'Baleno'),
  (3, 1, 'Brezza'),
  (4, 2, 'Creta'),
  (5, 2, 'i20'),
  (6, 3, 'Nexon'),
  (7, 3, 'Tiago'),
  (8, 4, 'Thar'),
  (9, 4, 'Scorpio-N'),
  (10, 4, 'Bolero Maxi Truck'),
  (11, 5, 'City'),
  (12, 5, 'Activa 6G'),
  (13, 6, 'Splendor Plus'),
  (14, 7, 'Pulsar 150'),
  (15, 8, 'Innova Crysta'),
  (16, 9, 'Jupiter 125'),
  (17, 10, '450X'),
  (18, 11, 'Dost Strong');

INSERT INTO vehicle_types (vehicle_type_id, type_name, is_commercial) VALUES
  (1, 'Two-Wheeler', FALSE),
  (2, 'Light Motor Vehicle', FALSE),
  (3, 'Heavy Motor Vehicle', TRUE),
  (4, 'Transport Vehicle', TRUE);

INSERT INTO fuel_types (fuel_type_id, fuel_name) VALUES
  (1, 'Petrol'),
  (2, 'Diesel'),
  (3, 'CNG'),
  (4, 'Electric'),
  (5, 'Hybrid');

INSERT INTO licence_classes (licence_class_id, class_code, description) VALUES
  (1, 'MC50CC', 'Motorcycle up to 50cc'),
  (2, 'MCWOG', 'Motorcycle without gear (Scooter)'),
  (3, 'MCWG', 'Motorcycle with gear'),
  (4, 'LMV', 'Light Motor Vehicle (Car/Jeep)'),
  (5, 'HMV', 'Heavy Motor Vehicle (Truck/Bus)'),
  (6, 'TRANS', 'Commercial Transport Vehicle');

INSERT INTO document_types (document_type_id, type_name, is_mandatory_default, validity_period_days) VALUES
  (1, 'Aadhaar Card', TRUE, NULL),
  (2, 'Passport Photo', TRUE, NULL),
  (3, 'Address Proof', TRUE, NULL),
  (4, 'Medical Certificate (Form 1A)', FALSE, 365),
  (5, 'Age Proof (Birth Certificate/10th Board)', TRUE, NULL),
  (6, 'Vehicle Sales Certificate (Form 21)', TRUE, NULL),
  (7, 'Pollution Under Control (PUC)', TRUE, 180);

INSERT INTO service_types (service_type_id, service_name, service_code, base_fee, sla_days) VALUES
  (1, 'New Driving Licence', 'NEW_DL', 500.00, 14),
  (2, 'Driving Licence Renewal', 'RENEWAL_DL', 300.00, 7),
  (3, 'Ownership Transfer', 'OWNERSHIP_TRANSFER', 750.00, 21),
  (4, 'New Commercial Permit', 'NEW_PERMIT', 1000.00, 30),
  (5, 'New Vehicle Registration', 'NEW_VEHICLE_REG', 800.00, 7),
  (6, 'Learner Licence Issue', 'NEW_LL', 200.00, 3),
  (7, 'Duplicate Driving Licence', 'DUP_DL', 400.00, 5);

INSERT INTO violation_types (violation_type_id, description, base_fine_amount, is_cognizable) VALUES
  (1, 'Over Speeding', 1000.00, FALSE),
  (2, 'Driving Without Licence', 5000.00, TRUE),
  (3, 'Signal Jumping', 500.00, FALSE),
  (4, 'Drunk Driving', 10000.00, TRUE),
  (5, 'Mobile Phone While Driving', 1500.00, FALSE),
  (6, 'Riding Without Helmet / Seatbelt', 1000.00, FALSE),
  (7, 'Dangerous Driving', 5000.00, TRUE),
  (8, 'Driving Without Valid Insurance', 2000.00, FALSE);

INSERT INTO permit_types (permit_type_id, type_name, validity_months) VALUES
  (1, 'State Goods Carriage Permit', 60),
  (2, 'National Goods Carriage Permit', 60),
  (3, 'Contract Carriage / Taxi Permit', 36),
  (4, 'Stage Carriage / Bus Permit', 60),
  (5, 'Temporary Goods Permit', 1);

INSERT INTO payable_types (payable_type_id, type_name) VALUES
  (1, 'APPLICATION'),
  (2, 'CHALLAN'),
  (3, 'PERMIT'),
  (4, 'ROAD_TAX');

-- 4. RTO Offices, Counters & Test Centres
INSERT INTO rto_offices (office_id, region_id, office_name, office_code, address_line, is_active) VALUES
  (1, 2, 'RTO Bengaluru Central', 'KA-01', 'Koramangala 3rd Block, Bengaluru 560034', TRUE),
  (2, 2, 'RTO Bengaluru West', 'KA-02', 'Chord Road, Rajajinagar, Bengaluru 560010', TRUE),
  (3, 2, 'RTO Bengaluru East', 'KA-03', '100ft Road, Indiranagar, Bengaluru 560038', TRUE),
  (4, 4, 'RTO Mysuru Central', 'KA-09', 'Nazarbad Main Road, Mysuru 570010', TRUE),
  (5, 6, 'RTO Ahmedabad Central', 'GJ-01', 'Subhash Bridge, Ashram Road, Ahmedabad 380027', TRUE),
  (6, 7, 'RTO Surat City', 'GJ-05', 'Ring Road, Majura Gate, Surat 395002', TRUE);

INSERT INTO counters (counter_id, office_id, counter_number, service_category) VALUES
  (1, 1, 'C-01', 'Licensing'),
  (2, 1, 'C-02', 'Vehicle Registration'),
  (3, 1, 'C-03', 'Enforcement & Challans'),
  (4, 1, 'C-04', 'Inquiry & Fee Deposit'),
  (5, 2, 'C-01', 'Licensing'),
  (6, 2, 'C-02', 'Vehicle Registration'),
  (7, 2, 'C-03', 'Enforcement'),
  (8, 3, 'C-01', 'Licensing'),
  (9, 3, 'C-02', 'Vehicle Registration'),
  (10, 4, 'C-01', 'Licensing & Registration'),
  (11, 5, 'C-01', 'Licensing'),
  (12, 5, 'C-02', 'Vehicle Registration');

INSERT INTO test_centres (test_centre_id, office_id, centre_name) VALUES
  (1, 1, 'Bengaluru Automated Driving Test Track (Koramangala)'),
  (2, 2, 'Rajajinagar Heavy Vehicle Driving Track'),
  (3, 4, 'Mysuru Outer Ring Road Testing Track'),
  (4, 5, 'Ahmedabad Central Automated Test Facility');

-- 5. Identity: Persons & Addresses
INSERT INTO persons (person_id, first_name, last_name, date_of_birth, gender, national_id_number, phone_primary, email, created_at, updated_at) VALUES
  (1, 'Super', 'Administrator', '1985-01-01', 'MALE', 'AADH-000000000', '9880000001', 'admin@rto.gov.in', NOW(), NOW()),
  (2, 'Ramesh', 'Kumar', '1976-04-12', 'MALE', 'AADH-100000001', '9880000002', 'ramesh.kumar@rto.gov.in', NOW(), NOW()),
  (3, 'Suresh', 'Bhat', '1980-08-23', 'MALE', 'AADH-100000002', '9880000003', 'suresh.bhat@rto.gov.in', NOW(), NOW()),
  (4, 'Deepa', 'Shetty', '1984-11-15', 'FEMALE', 'AADH-100000003', '9880000004', 'deepa.shetty@rto.gov.in', NOW(), NOW()),
  (5, 'Anand', 'Deshmukh', '1978-02-18', 'MALE', 'AADH-100000004', '9880000005', 'anand.d@rto.gov.in', NOW(), NOW()),
  (6, 'Meenakshi', 'Sundaram', '1982-06-30', 'FEMALE', 'AADH-100000005', '9880000006', 'meenakshi.s@rto.gov.in', NOW(), NOW()),
  (7, 'Vikram', 'Rathore', '1986-09-05', 'MALE', 'AADH-100000006', '9880000007', 'vikram.r@rto.gov.in', NOW(), NOW()),
  (8, 'Kavita', 'Patil', '1990-12-01', 'FEMALE', 'AADH-100000007', '9880000008', 'kavita.p@rto.gov.in', NOW(), NOW()),
  (9, 'Mohan', 'Lal', '1974-05-19', 'MALE', 'AADH-100000008', '9880000009', 'mohan.lal@rto.gov.in', NOW(), NOW()),
  (10, 'Aarav', 'Mehta', '1992-03-14', 'MALE', 'AADH-200000010', '9845011111', 'aarav.mehta@example.com', NOW(), NOW()),
  (11, 'Priya', 'Sharma', '1995-07-22', 'FEMALE', 'AADH-200000011', '9845022222', 'priya.sharma@example.com', NOW(), NOW()),
  (12, 'Rohan', 'Patel', '1988-11-05', 'MALE', 'AADH-200000012', '9845033333', 'rohan.patel@example.com', NOW(), NOW()),
  (13, 'Sunita', 'Desai', '1991-01-30', 'FEMALE', 'AADH-200000013', '9845044444', 'sunita.desai@example.com', NOW(), NOW()),
  (14, 'Kiran', 'Joshi', '1996-06-18', 'MALE', 'AADH-200000014', '9845055555', 'kiran.joshi@example.com', NOW(), NOW()),
  (15, 'Pooja', 'Singh', '1994-09-09', 'FEMALE', 'AADH-200000015', '9845066666', 'pooja.singh@example.com', NOW(), NOW()),
  (16, 'Amit', 'Verma', '1983-12-25', 'MALE', 'AADH-200000016', '9845077777', 'amit.verma@example.com', NOW(), NOW()),
  (17, 'Neha', 'Gupta', '1998-04-02', 'FEMALE', 'AADH-200000017', '9845088888', 'neha.gupta@example.com', NOW(), NOW()),
  (18, 'Vijay', 'Shah', '1979-08-15', 'MALE', 'AADH-200000018', '9845099999', 'vijay.shah@example.com', NOW(), NOW()),
  (19, 'Meera', 'Nair', '2000-02-28', 'FEMALE', 'AADH-200000019', '9845010101', 'meera.nair@example.com', NOW(), NOW()),
  (20, 'Deepak', 'Kumar', '1993-05-12', 'MALE', 'AADH-200000020', '9845020202', 'deepak.k@example.com', NOW(), NOW()),
  (21, 'Ananya', 'Roy', '1997-10-14', 'FEMALE', 'AADH-200000021', '9845030303', 'ananya.roy@example.com', NOW(), NOW()),
  (22, 'Rajesh', 'Iyer', '1981-04-20', 'MALE', 'AADH-200000022', '9845040404', 'rajesh.iyer@example.com', NOW(), NOW()),
  (23, 'Sneha', 'Patil', '1999-08-08', 'FEMALE', 'AADH-200000023', '9845050505', 'sneha.patil@example.com', NOW(), NOW()),
  (24, 'Mohammed', 'Farhan', '1990-12-03', 'MALE', 'AADH-200000024', '9845060606', 'm.farhan@example.com', NOW(), NOW()),
  (25, 'Kavita', 'Reddy', '1987-03-29', 'FEMALE', 'AADH-200000025', '9845070707', 'kavita.reddy@example.com', NOW(), NOW()),
  (26, 'Girish', 'Hegde', '1986-07-17', 'MALE', 'AADH-200000026', '9845080808', 'girish.hegde@example.com', NOW(), NOW()),
  (27, 'Harish', 'Chandra', '1975-01-11', 'MALE', 'AADH-200000027', '9845090909', 'harish.c@example.com', NOW(), NOW()),
  (28, 'Manish', 'Tiwari', '1989-10-25', 'MALE', 'AADH-200000028', '9845012121', 'manish.t@example.com', NOW(), NOW()),
  (29, 'Praveen', 'Sood', '1992-06-04', 'MALE', 'AADH-200000029', '9845023232', 'praveen.sood@example.com', NOW(), NOW()),
  (30, 'Tanvi', 'Kulkarni', '1994-02-14', 'FEMALE', 'AADH-200000030', '9845034343', 'tanvi.k@example.com', NOW(), NOW()),
  (31, 'Alex', 'Morgan', '1996-05-05', 'OTHER', 'AADH-200000031', '9845045454', 'alex.morgan@example.com', NOW(), NOW()),
  (32, 'Siobhán', 'O''Neill-Fernández', '1985-11-30', 'FEMALE', 'AADH-200000032', '9845056565', 'siobhan.oneill@example.com', NOW(), NOW());

INSERT INTO addresses (address_id, person_id, line1, line2, city, state, pincode, address_type, is_current, valid_from) VALUES
  (1, 1, '#12, 5th Cross, Main Road', 'Sector 2', 'Bengaluru', 'Karnataka', '560001', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (2, 2, '#24, 5th Cross, Main Road', 'Sector 3', 'Bengaluru', 'Karnataka', '560002', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (3, 3, '#36, 5th Cross, Main Road', 'Sector 4', 'Bengaluru', 'Karnataka', '560003', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (4, 4, '#48, 5th Cross, Main Road', 'Sector 5', 'Bengaluru', 'Karnataka', '560004', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (5, 5, '#60, 5th Cross, Main Road', 'Sector 1', 'Bengaluru', 'Karnataka', '560005', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (6, 6, '#72, 5th Cross, Main Road', 'Sector 2', 'Bengaluru', 'Karnataka', '560006', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (7, 7, '#84, 5th Cross, Main Road', 'Sector 3', 'Bengaluru', 'Karnataka', '560007', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (8, 8, '#96, 5th Cross, Main Road', 'Sector 4', 'Bengaluru', 'Karnataka', '560008', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (9, 9, '#108, 5th Cross, Main Road', 'Sector 5', 'Bengaluru', 'Karnataka', '560009', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (10, 10, '#120, 5th Cross, Main Road', 'Sector 1', 'Bengaluru', 'Karnataka', '560010', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (11, 11, '#132, 5th Cross, Main Road', 'Sector 2', 'Bengaluru', 'Karnataka', '560011', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (12, 12, '#144, 5th Cross, Main Road', 'Sector 3', 'Bengaluru', 'Karnataka', '560012', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (13, 13, '#156, 5th Cross, Main Road', 'Sector 4', 'Bengaluru', 'Karnataka', '560013', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (14, 14, '#168, 5th Cross, Main Road', 'Sector 5', 'Bengaluru', 'Karnataka', '560014', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (15, 15, '#180, 5th Cross, Main Road', 'Sector 1', 'Bengaluru', 'Karnataka', '560015', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (16, 16, '#192, 5th Cross, Main Road', 'Sector 2', 'Bengaluru', 'Karnataka', '560016', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (17, 17, '#204, 5th Cross, Main Road', 'Sector 3', 'Bengaluru', 'Karnataka', '560017', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (18, 18, '#216, 5th Cross, Main Road', 'Sector 4', 'Bengaluru', 'Karnataka', '560018', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (19, 19, '#228, 5th Cross, Main Road', 'Sector 5', 'Bengaluru', 'Karnataka', '560019', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (20, 20, '#240, 5th Cross, Main Road', 'Sector 1', 'Bengaluru', 'Karnataka', '560020', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (21, 21, '#252, 5th Cross, Main Road', 'Sector 2', 'Bengaluru', 'Karnataka', '560021', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (22, 22, '#264, 5th Cross, Main Road', 'Sector 3', 'Bengaluru', 'Karnataka', '560022', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (23, 23, '#276, 5th Cross, Main Road', 'Sector 4', 'Bengaluru', 'Karnataka', '560023', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (24, 24, '#288, 5th Cross, Main Road', 'Sector 5', 'Bengaluru', 'Karnataka', '560024', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (25, 25, '#300, 5th Cross, Main Road', 'Sector 1', 'Mysuru', 'Karnataka', '570010', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (26, 26, '#312, 5th Cross, Main Road', 'Sector 2', 'Mysuru', 'Karnataka', '570010', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (27, 27, '#324, 5th Cross, Main Road', 'Sector 3', 'Mysuru', 'Karnataka', '570010', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (28, 28, '#336, 5th Cross, Main Road', 'Sector 4', 'Ahmedabad', 'Gujarat', '380027', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (29, 29, '#348, 5th Cross, Main Road', 'Sector 5', 'Ahmedabad', 'Gujarat', '380027', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (30, 30, '#360, 5th Cross, Main Road', 'Sector 1', 'Ahmedabad', 'Gujarat', '380027', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (31, 31, '#372, 5th Cross, Main Road', 'Sector 2', 'Ahmedabad', 'Gujarat', '380027', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY)),
  (32, 32, '#384, 5th Cross, Main Road', 'Sector 3', 'Ahmedabad', 'Gujarat', '380027', 'CURRENT', TRUE, (CURDATE() - INTERVAL 2486 DAY));

INSERT INTO addresses (address_id, person_id, line1, line2, city, state, pincode, address_type, is_current, valid_from, valid_to) VALUES
  (33, 10, '12, Palace Road', NULL, 'Mysuru', 'Karnataka', '570001', 'PERMANENT', TRUE, (CURDATE() - INTERVAL 4161 DAY), NULL),
  (34, 11, '77, MG Road, 3rd Floor', 'Embassy Tower', 'Bengaluru', 'Karnataka', '560001', 'OFFICE', TRUE, (CURDATE() - INTERVAL 1300 DAY), NULL),
  (35, 12, '4, Old Pune Road', NULL, 'Pune', 'Maharashtra', '411001', 'PERMANENT', TRUE, (CURDATE() - INTERVAL 5377 DAY), NULL),
  (36, 10, '9, 1st Main, Jayanagar', NULL, 'Bengaluru', 'Karnataka', '560011', 'CURRENT', FALSE, (CURDATE() - INTERVAL 3947 DAY), (CURDATE() - INTERVAL 2487 DAY)),
  (37, 2, 'RTO Office, Koramangala', 'Near Sony Signal', 'Bengaluru', 'Karnataka', '560034', 'OFFICE', TRUE, (CURDATE() - INTERVAL 2670 DAY), NULL);

-- 6. Citizens & Applicants (including blacklisted citizens)
INSERT INTO citizens (citizen_id, person_id, citizen_code, blacklisted, blacklist_reason, registered_at) VALUES
  (1, 10, 'CIT-2026-00001', FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL 1723 DAY, '10:00:00')),
  (2, 11, 'CIT-2026-00002', FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL 1694 DAY, '10:00:00')),
  (3, 12, 'CIT-2026-00003', FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL 1662 DAY, '10:00:00')),
  (4, 13, 'CIT-2026-00004', FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL 1631 DAY, '10:00:00')),
  (5, 14, 'CIT-2026-00005', FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL 1599 DAY, '10:00:00')),
  (6, 15, 'CIT-2026-00006', FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL 1568 DAY, '10:00:00')),
  (7, 16, 'CIT-2026-00007', FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL 1536 DAY, '10:00:00')),
  (8, 17, 'CIT-2026-00008', FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL 1504 DAY, '10:00:00')),
  (9, 18, 'CIT-2026-00009', FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL 1473 DAY, '10:00:00')),
  (10, 19, 'CIT-2026-00010', FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL 1441 DAY, '10:00:00')),
  (11, 20, 'CIT-2026-00011', FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL 1410 DAY, '10:00:00')),
  (12, 21, 'CIT-2026-00012', FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL 1743 DAY, '10:00:00')),
  (13, 22, 'CIT-2026-00013', FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL 1711 DAY, '10:00:00')),
  (14, 23, 'CIT-2026-00014', FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL 1682 DAY, '10:00:00')),
  (15, 24, 'CIT-2026-00015', FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL 1650 DAY, '10:00:00')),
  (16, 25, 'CIT-2026-00016', FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL 1619 DAY, '10:00:00')),
  (17, 26, 'CIT-2026-00017', FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL 1587 DAY, '10:00:00')),
  (18, 27, 'CIT-2026-00018', TRUE, 'Suspended: 3 DUI offenses in 6 months; Section 185 Motor Vehicles Act', TIMESTAMP(CURDATE() - INTERVAL 1687 DAY, '10:00:00')),
  (19, 28, 'CIT-2026-00019', TRUE, 'Debarred: Forged educational certificate submitted for transport licence', TIMESTAMP(CURDATE() - INTERVAL 1894 DAY, '11:30:00')),
  (20, 29, 'CIT-2026-00020', TRUE, 'Habitual red-light runner with 8 unpaid speed violations', TIMESTAMP(CURDATE() - INTERVAL 1371 DAY, '14:15:00')),
  (21, 30, 'CIT-2026-00021', TRUE, 'Involved in reckless hit-and-run under FIR 104/2026', TIMESTAMP(CURDATE() - INTERVAL 887 DAY, '16:45:00')),
  (22, 31, 'CIT-2026-00022', FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL 1429 DAY, '10:00:00')),
  (23, 32, 'CIT-2026-00023', FALSE, NULL, TIMESTAMP(CURDATE() - INTERVAL 1398 DAY, '10:00:00'));

INSERT INTO applicants (applicant_id, citizen_id, preferred_office_id) VALUES
  (1, 1, 2),
  (2, 2, 3),
  (3, 3, 4),
  (4, 4, 5),
  (5, 5, 1),
  (6, 6, 2),
  (7, 7, 3),
  (8, 8, 4),
  (9, 9, 5),
  (10, 10, 1),
  (11, 11, 2),
  (12, 12, 3),
  (13, 13, 4),
  (14, 14, 5),
  (15, 15, 1),
  (16, 16, 2),
  (17, 17, 3),
  (18, 18, 4),
  (19, 19, 5),
  (20, 20, 1),
  (21, 21, 2),
  (22, 22, 3),
  (23, 23, 4);

-- 7. Employees & Postings
INSERT INTO employees (employee_id, person_id, employee_code, designation_id, date_joined, is_active) VALUES
  (1, 2, 'EMP-KA-001', 1, (CURDATE() - INTERVAL 4192 DAY), TRUE),
  (2, 3, 'EMP-KA-002', 2, (CURDATE() - INTERVAL 4192 DAY), TRUE),
  (3, 4, 'EMP-KA-003', 2, (CURDATE() - INTERVAL 4192 DAY), TRUE),
  (4, 5, 'EMP-KA-004', 3, (CURDATE() - INTERVAL 4192 DAY), TRUE),
  (5, 6, 'EMP-KA-005', 2, (CURDATE() - INTERVAL 4192 DAY), TRUE),
  (6, 7, 'EMP-KA-006', 3, (CURDATE() - INTERVAL 4192 DAY), TRUE),
  (7, 8, 'EMP-KA-007', 4, (CURDATE() - INTERVAL 4192 DAY), TRUE),
  (8, 9, 'EMP-KA-008', 1, (CURDATE() - INTERVAL 4192 DAY), TRUE);

INSERT INTO employee_postings (posting_id, employee_id, office_id, department_id, posted_from, posted_to) VALUES
  (1, 1, 1, 5, (CURDATE() - INTERVAL 2486 DAY), NULL),
  (2, 2, 1, 1, (CURDATE() - INTERVAL 2486 DAY), NULL),
  (3, 3, 1, 2, (CURDATE() - INTERVAL 2486 DAY), NULL),
  (4, 4, 2, 3, (CURDATE() - INTERVAL 2486 DAY), NULL),
  (5, 5, 2, 1, (CURDATE() - INTERVAL 2486 DAY), NULL),
  (6, 6, 3, 3, (CURDATE() - INTERVAL 2486 DAY), NULL),
  (7, 7, 1, 4, (CURDATE() - INTERVAL 2486 DAY), NULL),
  (8, 8, 5, 5, (CURDATE() - INTERVAL 2486 DAY), NULL);

-- 8. RBAC: Roles, Permissions, Users, and Roles
INSERT INTO roles (role_id, role_name) VALUES
  (1, 'CITIZEN'),
  (2, 'RTO_OFFICER'),
  (3, 'COUNTER_CLERK'),
  (4, 'INSPECTOR'),
  (5, 'ENFORCEMENT_OFFICER'),
  (6, 'ACCOUNTANT'),
  (7, 'ADMIN');

INSERT INTO permissions (permission_id, permission_key) VALUES
  (1, 'rbac.manage'),
  (2, 'user.view'),
  (3, 'user.manage'),
  (4, 'audit.view'),
  (5, 'dashboard.view'),
  (6, 'reference.manage'),
  (7, 'citizen.view'),
  (8, 'citizen.create'),
  (9, 'citizen.update'),
  (10, 'citizen.view_sensitive'),
  (11, 'office.manage'),
  (12, 'employee.view'),
  (13, 'employee.manage'),
  (14, 'application.view'),
  (15, 'application.view_own'),
  (16, 'application.create'),
  (17, 'application.create_own'),
  (18, 'application.update'),
  (19, 'application.assign'),
  (20, 'application.verify'),
  (21, 'application.approve'),
  (22, 'application.reject'),
  (23, 'application.cancel'),
  (24, 'document.upload'),
  (25, 'document.view'),
  (26, 'document.verify'),
  (27, 'appointment.view'),
  (28, 'appointment.manage'),
  (29, 'appointment.book'),
  (30, 'licence.view'),
  (31, 'licence.issue'),
  (32, 'licence.suspend'),
  (33, 'licence.revoke'),
  (34, 'licence.test'),
  (35, 'licence.manage_schools'),
  (36, 'vehicle.view'),
  (37, 'vehicle.create'),
  (38, 'vehicle.update'),
  (39, 'vehicle.transfer'),
  (40, 'vehicle.transfer_approve'),
  (41, 'compliance.view'),
  (42, 'compliance.manage'),
  (43, 'permit.view'),
  (44, 'permit.create'),
  (45, 'permit.manage'),
  (46, 'route.manage'),
  (47, 'violation.view'),
  (48, 'violation.create'),
  (49, 'challan.view'),
  (50, 'challan.create'),
  (51, 'challan.manage'),
  (52, 'payment.view'),
  (53, 'payment.create'),
  (54, 'payment.refund'),
  (55, 'complaint.create'),
  (56, 'complaint.view_own'),
  (57, 'complaint.manage'),
  (58, 'appeal.create'),
  (59, 'appeal.view_own'),
  (60, 'appeal.review'),
  (61, 'notification.view'),
  (62, 'notification.view_own');

INSERT INTO role_permissions (role_id, permission_id) VALUES
  (7, 1),
  (7, 2),
  (7, 3),
  (7, 4),
  (7, 5),
  (7, 6),
  (7, 7),
  (7, 8),
  (7, 9),
  (7, 10),
  (7, 11),
  (7, 12),
  (7, 13),
  (7, 14),
  (7, 15),
  (7, 16),
  (7, 17),
  (7, 18),
  (7, 19),
  (7, 20),
  (7, 21),
  (7, 22),
  (7, 23),
  (7, 24),
  (7, 25),
  (7, 26),
  (7, 27),
  (7, 28),
  (7, 29),
  (7, 30),
  (7, 31),
  (7, 32),
  (7, 33),
  (7, 34),
  (7, 35),
  (7, 36),
  (7, 37),
  (7, 38),
  (7, 39),
  (7, 40),
  (7, 41),
  (7, 42),
  (7, 43),
  (7, 44),
  (7, 45),
  (7, 46),
  (7, 47),
  (7, 48),
  (7, 49),
  (7, 50),
  (7, 51),
  (7, 52),
  (7, 53),
  (7, 54),
  (7, 55),
  (7, 56),
  (7, 57),
  (7, 58),
  (7, 59),
  (7, 60),
  (7, 61),
  (7, 62),
  (2, 60),
  (2, 21),
  (2, 19),
  (2, 23),
  (2, 16),
  (2, 22),
  (2, 18),
  (2, 20),
  (2, 14),
  (2, 29),
  (2, 28),
  (2, 27),
  (2, 49),
  (2, 8),
  (2, 9),
  (2, 7),
  (2, 10),
  (2, 57),
  (2, 42),
  (2, 41),
  (2, 5),
  (2, 24),
  (2, 26),
  (2, 25),
  (2, 12),
  (2, 31),
  (2, 33),
  (2, 32),
  (2, 34),
  (2, 35),
  (2, 30),
  (2, 61),
  (2, 52),
  (2, 44),
  (2, 45),
  (2, 43),
  (2, 46),
  (2, 37),
  (2, 39),
  (2, 40),
  (2, 38),
  (2, 36),
  (2, 47),
  (3, 16),
  (3, 18),
  (3, 14),
  (3, 29),
  (3, 28),
  (3, 27),
  (3, 49),
  (3, 8),
  (3, 9),
  (3, 7),
  (3, 55),
  (3, 41),
  (3, 5),
  (3, 24),
  (3, 25),
  (3, 30),
  (3, 53),
  (3, 52),
  (3, 43),
  (3, 39),
  (3, 36),
  (4, 7),
  (4, 42),
  (4, 41),
  (4, 34),
  (4, 30),
  (4, 36),
  (4, 48),
  (4, 47),
  (5, 50),
  (5, 51),
  (5, 49),
  (5, 7),
  (5, 30),
  (5, 36),
  (5, 48),
  (5, 47),
  (6, 14),
  (6, 4),
  (6, 49),
  (6, 5),
  (6, 53),
  (6, 54),
  (6, 52),
  (1, 58),
  (1, 59),
  (1, 17),
  (1, 15),
  (1, 29),
  (1, 55),
  (1, 56),
  (1, 24),
  (1, 62);

INSERT INTO users (user_id, person_id, username, password_hash, is_active, last_login_at) VALUES
  (1, 1, 'admin', '$2a$12$rBFxvAroCCzh13giiIqtw.oc1Y4A4WZU/smLrhw7r6c2nk.YxFpW6', TRUE, NOW()),
  (2, 2, 'officer.kumar', '$2a$12$rBFxvAroCCzh13giiIqtw.oc1Y4A4WZU/smLrhw7r6c2nk.YxFpW6', TRUE, NOW()),
  (3, 3, 'inspector.bhat', '$2a$12$rBFxvAroCCzh13giiIqtw.oc1Y4A4WZU/smLrhw7r6c2nk.YxFpW6', TRUE, NOW()),
  (4, 5, 'inspector.anand', '$2a$12$rBFxvAroCCzh13giiIqtw.oc1Y4A4WZU/smLrhw7r6c2nk.YxFpW6', TRUE, NOW()),
  (5, 7, 'enforcer.vikram', '$2a$12$rBFxvAroCCzh13giiIqtw.oc1Y4A4WZU/smLrhw7r6c2nk.YxFpW6', TRUE, NOW()),
  (6, 10, 'aarav.mehta', '$2a$12$rBFxvAroCCzh13giiIqtw.oc1Y4A4WZU/smLrhw7r6c2nk.YxFpW6', TRUE, NOW()),
  (7, 11, 'priya.sharma', '$2a$12$rBFxvAroCCzh13giiIqtw.oc1Y4A4WZU/smLrhw7r6c2nk.YxFpW6', TRUE, NOW()),
  (8, 12, 'rohan.patel', '$2a$12$rBFxvAroCCzh13giiIqtw.oc1Y4A4WZU/smLrhw7r6c2nk.YxFpW6', TRUE, NOW()),
  (9, 13, 'sunita.desai', '$2a$12$rBFxvAroCCzh13giiIqtw.oc1Y4A4WZU/smLrhw7r6c2nk.YxFpW6', TRUE, NOW()),
  (10, 14, 'kiran.joshi', '$2a$12$rBFxvAroCCzh13giiIqtw.oc1Y4A4WZU/smLrhw7r6c2nk.YxFpW6', TRUE, NOW());

INSERT INTO user_roles (user_id, role_id) VALUES
  (1, 7), (1, 2), (1, 4),
  (2, 2),
  (3, 4),
  (4, 4),
  (5, 5),
  (6, 1), (7, 1), (8, 1), (9, 1), (10, 1);

-- 9. Applications (Full lifecycle representation across every valid status)
INSERT INTO applications (application_id, application_number, applicant_id, service_type_id, office_id, assigned_officer_id, current_status, submitted_at, completed_at, remarks) VALUES
  (1, 'APP-2026-00001', 1, 1, 1, 2, 'SUBMITTED', TIMESTAMP(CURDATE() - INTERVAL 1 DAY, '09:30:00'), NULL, NULL),
  (2, 'APP-2026-00002', 2, 2, 1, 2, 'DOCS_PENDING', TIMESTAMP(CURDATE() - INTERVAL 2 DAY, '10:15:00'), NULL, 'Medical certificate Form 1A missing doctor registration stamp'),
  (3, 'APP-2026-00003', 3, 1, 1, 3, 'UNDER_VERIFICATION', TIMESTAMP(CURDATE() - INTERVAL 4 DAY, '11:00:00'), NULL, 'Aadhaar biometric validation in progress'),
  (4, 'APP-2026-00004', 4, 6, 2, 4, 'APPOINTMENT_SCHEDULED', TIMESTAMP(CURDATE() - INTERVAL 6 DAY, '14:00:00'), NULL, 'Automated track slot booked for 2026-10-24 10:00'),
  (5, 'APP-2026-00005', 5, 5, 1, 2, 'AWAITING_PAYMENT', TIMESTAMP(CURDATE() - INTERVAL 7 DAY, '15:30:00'), NULL, 'Registration assessment fee generated; pending citizen payment'),
  (6, 'APP-2026-00006', 6, 3, 2, 5, 'APPROVED', TIMESTAMP(CURDATE() - INTERVAL 8 DAY, '09:00:00'), NULL, 'Transfer of ownership approved by MVI; smart card printing'),
  (7, 'APP-2026-00007', 7, 1, 3, 6, 'REJECTED', TIMESTAMP(CURDATE() - INTERVAL 12 DAY, '11:20:00'), NULL, 'Failed driving competency test twice on 8-track'),
  (8, 'APP-2026-00008', 8, 4, 1, 2, 'COMPLETED', TIMESTAMP(CURDATE() - INTERVAL 24 DAY, '10:00:00'), TIMESTAMP(CURDATE() - INTERVAL 17 DAY, '16:30:00'), 'Commercial route permit issued and laminated'),
  (9, 'APP-2026-00009', 9, 7, 2, 5, 'CANCELLED', TIMESTAMP(CURDATE() - INTERVAL 21 DAY, '12:00:00'), NULL, 'Applicant requested cancellation due to out-of-state transfer'),
  (10, 'APP-2026-00010', 10, 6, 1, 2, 'COMPLETED', TIMESTAMP(CURDATE() - INTERVAL 20 DAY, '09:15:00'), TIMESTAMP(CURDATE() - INTERVAL 18 DAY, '14:00:00'), 'Learner Licence issued digitally with OTP signing'),
  (11, 'APP-2026-00011', 11, 1, 1, 2, 'COMPLETED', TIMESTAMP(CURDATE() - INTERVAL 37 DAY, '10:30:00'), TIMESTAMP(CURDATE() - INTERVAL 27 DAY, '15:45:00'), 'Permanent Driving Licence printed and dispatched'),
  (12, 'APP-2026-00012', 12, 5, 2, 3, 'COMPLETED', TIMESTAMP(CURDATE() - INTERVAL 32 DAY, '11:45:00'), TIMESTAMP(CURDATE() - INTERVAL 24 DAY, '17:00:00'), 'RC Book smart card dispatched via India Post SpeedPost'),
  (13, 'APP-2026-00013', 13, 2, 3, 6, 'SUBMITTED', TIMESTAMP(CURDATE(), '08:45:00'), NULL, 'Renewal application submitted online'),
  (14, 'APP-2026-00014', 14, 6, 1, 2, 'DOCS_PENDING', TIMESTAMP(CURDATE() - INTERVAL 3 DAY, '16:10:00'), NULL, 'Uploaded address proof scan is blurred and illegible'),
  (15, 'APP-2026-00015', 15, 3, 1, 3, 'UNDER_VERIFICATION', TIMESTAMP(CURDATE() - INTERVAL 5 DAY, '13:20:00'), NULL, 'Form 29 and 30 verification in progress'),
  (16, 'APP-2026-00016', 16, 4, 2, 5, 'APPOINTMENT_SCHEDULED', TIMESTAMP(CURDATE() - INTERVAL 6 DAY, '11:00:00'), NULL, 'Physical vehicle inspection booked for 2026-10-25 11:00'),
  (17, 'APP-2026-00017', 1, 5, 1, 2, 'AWAITING_PAYMENT', TIMESTAMP(CURDATE() - INTERVAL 5 DAY, '14:40:00'), NULL, 'Road tax and smart card fee pending online payment'),
  (18, 'APP-2026-00018', 2, 1, 2, 5, 'APPROVED', TIMESTAMP(CURDATE() - INTERVAL 10 DAY, '10:00:00'), NULL, 'Test passed with 95% score; final DL approved'),
  (19, 'APP-2026-00019', 3, 4, 3, 6, 'REJECTED', TIMESTAMP(CURDATE() - INTERVAL 27 DAY, '15:00:00'), NULL, 'Vehicle does not conform to commercial passenger safety norms'),
  (20, 'APP-2026-00020', 4, 2, 1, 2, 'CANCELLED', TIMESTAMP(CURDATE() - INTERVAL 17 DAY, '09:30:00'), NULL, 'Duplicate renewal request flagged by system');

INSERT INTO application_status_history (history_id, application_id, previous_status, new_status, changed_by_user_id, changed_at, reason) VALUES
  (1, 1, NULL, 'SUBMITTED', 1, TIMESTAMP(CURDATE() - INTERVAL 1 DAY, '09:30:00'), 'Application submitted online by citizen'),
  (2, 2, NULL, 'SUBMITTED', 1, TIMESTAMP(CURDATE() - INTERVAL 2 DAY, '10:15:00'), 'Submitted'),
  (3, 2, 'SUBMITTED', 'DOCS_PENDING', 2, TIMESTAMP(CURDATE() - INTERVAL 2 DAY, '11:30:00'), 'Doctor stamp missing on Form 1A'),
  (4, 3, NULL, 'SUBMITTED', 1, TIMESTAMP(CURDATE() - INTERVAL 4 DAY, '11:00:00'), 'Submitted'),
  (5, 3, 'SUBMITTED', 'UNDER_VERIFICATION', 3, TIMESTAMP(CURDATE() - INTERVAL 3 DAY, '09:30:00'), 'Assigned for Aadhaar identity cross-check'),
  (6, 4, NULL, 'SUBMITTED', 1, TIMESTAMP(CURDATE() - INTERVAL 6 DAY, '14:00:00'), 'Submitted'),
  (7, 4, 'SUBMITTED', 'UNDER_VERIFICATION', 2, TIMESTAMP(CURDATE() - INTERVAL 5 DAY, '10:00:00'), 'Verified'),
  (8, 4, 'UNDER_VERIFICATION', 'APPOINTMENT_SCHEDULED', 2, TIMESTAMP(CURDATE() - INTERVAL 5 DAY, '14:00:00'), 'Slot booked'),
  (9, 5, NULL, 'SUBMITTED', 1, TIMESTAMP(CURDATE() - INTERVAL 7 DAY, '15:30:00'), 'Submitted'),
  (10, 5, 'SUBMITTED', 'UNDER_VERIFICATION', 2, TIMESTAMP(CURDATE() - INTERVAL 6 DAY, '10:00:00'), 'Verified invoice'),
  (11, 5, 'UNDER_VERIFICATION', 'AWAITING_PAYMENT', 2, TIMESTAMP(CURDATE() - INTERVAL 6 DAY, '11:30:00'), 'Challan generated'),
  (12, 6, NULL, 'SUBMITTED', 1, TIMESTAMP(CURDATE() - INTERVAL 8 DAY, '09:00:00'), 'Submitted'),
  (13, 6, 'SUBMITTED', 'APPROVED', 3, TIMESTAMP(CURDATE() - INTERVAL 7 DAY, '16:00:00'), 'Ownership transfer documents validated'),
  (14, 7, NULL, 'SUBMITTED', 1, TIMESTAMP(CURDATE() - INTERVAL 12 DAY, '11:20:00'), 'Submitted'),
  (15, 7, 'SUBMITTED', 'REJECTED', 2, TIMESTAMP(CURDATE() - INTERVAL 10 DAY, '14:30:00'), 'Driving test failed twice'),
  (16, 8, NULL, 'SUBMITTED', 1, TIMESTAMP(CURDATE() - INTERVAL 24 DAY, '10:00:00'), 'Submitted'),
  (17, 8, 'SUBMITTED', 'UNDER_VERIFICATION', 2, TIMESTAMP(CURDATE() - INTERVAL 23 DAY, '11:00:00'), 'Route vetted'),
  (18, 8, 'UNDER_VERIFICATION', 'APPROVED', 2, TIMESTAMP(CURDATE() - INTERVAL 20 DAY, '14:00:00'), 'Permit vetted'),
  (19, 8, 'APPROVED', 'COMPLETED', 2, TIMESTAMP(CURDATE() - INTERVAL 17 DAY, '16:30:00'), 'Permit issued'),
  (20, 9, NULL, 'SUBMITTED', 1, TIMESTAMP(CURDATE() - INTERVAL 21 DAY, '12:00:00'), 'Submitted'),
  (21, 9, 'SUBMITTED', 'CANCELLED', 1, TIMESTAMP(CURDATE() - INTERVAL 20 DAY, '10:00:00'), 'Citizen requested cancellation');

-- 10. Documents Attached to Applications
INSERT INTO documents (document_id, application_id, document_type_id, file_path, verification_status, verified_by_employee_id, rejection_reason, uploaded_at, verified_at) VALUES
  (1, 1, 1, 'storage/app1/aadhaar.pdf', 'VERIFIED', 2, NULL, TIMESTAMP(CURDATE() - INTERVAL 1 DAY, '09:35:00'), TIMESTAMP(CURDATE() - INTERVAL 1 DAY, '11:00:00')),
  (2, 1, 2, 'storage/app1/photo.jpg', 'VERIFIED', 2, NULL, TIMESTAMP(CURDATE() - INTERVAL 1 DAY, '09:36:00'), TIMESTAMP(CURDATE() - INTERVAL 1 DAY, '11:05:00')),
  (3, 2, 1, 'storage/app2/aadhaar.pdf', 'VERIFIED', 2, NULL, TIMESTAMP(CURDATE() - INTERVAL 2 DAY, '10:20:00'), TIMESTAMP(CURDATE() - INTERVAL 2 DAY, '11:00:00')),
  (4, 2, 4, 'storage/app2/medical_form1a.pdf', 'REJECTED', 2, 'Doctor stamp and reg number missing', TIMESTAMP(CURDATE() - INTERVAL 2 DAY, '10:22:00'), TIMESTAMP(CURDATE() - INTERVAL 2 DAY, '11:30:00')),
  (5, 3, 1, 'storage/app3/aadhaar.pdf', 'PENDING', NULL, NULL, TIMESTAMP(CURDATE() - INTERVAL 4 DAY, '11:05:00'), NULL),
  (6, 4, 1, 'storage/app4/aadhaar.pdf', 'VERIFIED', 3, NULL, TIMESTAMP(CURDATE() - INTERVAL 6 DAY, '14:05:00'), TIMESTAMP(CURDATE() - INTERVAL 5 DAY, '10:00:00')),
  (7, 4, 5, 'storage/app4/birth_cert.pdf', 'VERIFIED', 3, NULL, TIMESTAMP(CURDATE() - INTERVAL 6 DAY, '14:06:00'), TIMESTAMP(CURDATE() - INTERVAL 5 DAY, '10:05:00')),
  (8, 5, 6, 'storage/app5/form21_sale.pdf', 'VERIFIED', 2, NULL, TIMESTAMP(CURDATE() - INTERVAL 7 DAY, '15:35:00'), TIMESTAMP(CURDATE() - INTERVAL 6 DAY, '10:00:00')),
  (9, 6, 3, 'storage/app6/electricity_bill.pdf', 'VERIFIED', 5, NULL, TIMESTAMP(CURDATE() - INTERVAL 8 DAY, '09:10:00'), TIMESTAMP(CURDATE() - INTERVAL 7 DAY, '11:00:00')),
  (10, 7, 1, 'storage/app7/aadhaar.pdf', 'VERIFIED', 6, NULL, TIMESTAMP(CURDATE() - INTERVAL 12 DAY, '11:25:00'), TIMESTAMP(CURDATE() - INTERVAL 11 DAY, '09:30:00')),
  (11, 8, 1, 'storage/app8/aadhaar.pdf', 'VERIFIED', 2, NULL, TIMESTAMP(CURDATE() - INTERVAL 24 DAY, '10:05:00'), TIMESTAMP(CURDATE() - INTERVAL 23 DAY, '10:00:00')),
  (12, 14, 3, 'storage/app14/water_bill.pdf', 'REJECTED', 2, 'Illegible low resolution image', TIMESTAMP(CURDATE() - INTERVAL 3 DAY, '16:15:00'), TIMESTAMP(CURDATE() - INTERVAL 2 DAY, '09:00:00')),
  (13, 7, 3, 'storage/app7/old_water_bill.pdf', 'EXPIRED', 6, NULL, TIMESTAMP(CURDATE() - INTERVAL 568 DAY, '09:00:00'), TIMESTAMP(CURDATE() - INTERVAL 567 DAY, '10:00:00'));

-- 11. Appointment Slots & Booked Appointments
INSERT INTO appointment_slots (slot_id, office_id, counter_id, slot_date, start_time, end_time, capacity, booked_count) VALUES
  (1, 1, 1, (CURDATE()), '09:00:00', '09:30:00', 10, 5),
  (2, 1, 1, (CURDATE()), '09:30:00', '10:00:00', 10, 6),
  (3, 1, 1, (CURDATE() + INTERVAL 1 DAY), '09:00:00', '09:30:00', 10, 4),
  (4, 1, 1, (CURDATE() + INTERVAL 1 DAY), '09:30:00', '10:00:00', 10, 2),
  (5, 1, 2, (CURDATE() + INTERVAL 2 DAY), '10:00:00', '10:30:00', 8, 3),
  (6, 2, 5, (CURDATE() + INTERVAL 2 DAY), '10:00:00', '10:30:00', 8, 4),
  (7, 2, 5, (CURDATE() + INTERVAL 3 DAY), '11:00:00', '11:30:00', 8, 1),
  (8, 3, 8, (CURDATE() + INTERVAL 4 DAY), '14:00:00', '14:30:00', 6, 2),
  (9, 4, 10, (CURDATE() + INTERVAL 6 DAY), '10:00:00', '10:30:00', 5, 1);

INSERT INTO appointments (appointment_id, application_id, slot_id, token_number, status, booked_at) VALUES
  (1, 4, 6, 'TKN-KA02-001', 'BOOKED', TIMESTAMP(CURDATE() - INTERVAL 5 DAY, '14:00:00')),
  (2, 16, 7, 'TKN-KA02-002', 'BOOKED', TIMESTAMP(CURDATE() - INTERVAL 4 DAY, '10:30:00')),
  (3, 11, 1, 'TKN-KA01-089', 'COMPLETED', TIMESTAMP(CURDATE() - INTERVAL 34 DAY, '09:15:00')),
  (4, 10, 2, 'TKN-KA01-090', 'COMPLETED', TIMESTAMP(CURDATE() - INTERVAL 27 DAY, '10:00:00')),
  (5, 7, 3, 'TKN-KA01-045', 'NO_SHOW', TIMESTAMP(CURDATE() - INTERVAL 11 DAY, '09:30:00')),
  (6, 9, 4, 'TKN-KA01-046', 'CANCELLED', TIMESTAMP(CURDATE() - INTERVAL 21 DAY, '13:00:00')),
  (7, 15, 5, 'TKN-KA01-055', 'RESCHEDULED', TIMESTAMP(CURDATE() - INTERVAL 5 DAY, '14:00:00'));

-- 12. Driving Schools & Certified Instructors
INSERT INTO driving_schools (school_id, school_name, licence_number, office_id) VALUES
  (1, 'Maruti Suzuki Driving School - Koramangala', 'KA01-DS-2021-001', 1),
  (2, 'Apex Motor Driving Academy - Rajajinagar', 'KA02-DS-2022-014', 2),
  (3, 'Royal Star Motor Driving Institute - Indiranagar', 'KA03-DS-2023-088', 3),
  (4, 'Ahmedabad Highway Driving Academy', 'GJ01-DS-2020-005', 5);

INSERT INTO driving_instructors (instructor_id, person_id, school_id, licence_class_id) VALUES
  (1, 22, 1, 4),
  (2, 24, 1, 3),
  (3, 26, 2, 5),
  (4, 20, 3, 4);

-- 13. Learner Licences & Driving Tests
INSERT INTO learner_licences (learner_licence_id, licence_number, citizen_id, application_id, office_id, issue_date, expiry_date, status) VALUES
  (1, 'LL-KA01-2026-000101', 1, 10, 1, (CURDATE() - INTERVAL 51 DAY), (CURDATE() + INTERVAL 130 DAY), 'ACTIVE'),
  (2, 'LL-KA01-2026-000102', 2, 4, 1, (CURDATE() - INTERVAL 68 DAY), (CURDATE() + INTERVAL 116 DAY), 'ACTIVE'),
  (3, 'LL-KA02-2026-000201', 3, 6, 2, (CURDATE() - INTERVAL 165 DAY), (CURDATE() + INTERVAL 19 DAY), 'CONVERTED'),
  (4, 'LL-KA02-2026-000202', 4, 11, 2, (CURDATE() - INTERVAL 143 DAY), (CURDATE() + INTERVAL 40 DAY), 'CONVERTED'),
  (5, 'LL-KA01-2025-000055', 5, 12, 1, (CURDATE() - INTERVAL 416 DAY), (CURDATE() - INTERVAL 235 DAY), 'EXPIRED'),
  (6, 'LL-KA03-2025-000088', 6, 13, 3, (CURDATE() - INTERVAL 372 DAY), (CURDATE() - INTERVAL 190 DAY), 'EXPIRED'),
  (7, 'LL-KA01-2026-000199', 7, 7, 1, (CURDATE() - INTERVAL 94 DAY), (CURDATE() + INTERVAL 90 DAY), 'CANCELLED');

INSERT INTO driving_tests (test_id, application_id, citizen_id, test_centre_id, examiner_employee_id, scheduled_at, result, remarks) VALUES
  (1, 11, 11, 1, 2, TIMESTAMP(CURDATE() - INTERVAL 30 DAY, '10:00:00'), 'PASS', 'Flawless parallel parking and S-track navigation'),
  (2, 10, 10, 1, 2, TIMESTAMP(CURDATE() - INTERVAL 19 DAY, '11:30:00'), 'PASS', 'Theory traffic quiz passed 18/20'),
  (3, 7, 7, 1, 3, TIMESTAMP(CURDATE() - INTERVAL 11 DAY, '10:00:00'), 'FAIL', 'Hit bollard on reverse parallel bay and kerb touch'),
  (4, 4, 4, 1, 2, TIMESTAMP(CURDATE() + INTERVAL 2 DAY, '10:00:00'), 'PENDING', 'Scheduled on Automated Track C'),
  (5, 18, 2, 2, 4, TIMESTAMP(CURDATE() + INTERVAL 6 DAY, '14:00:00'), 'PENDING', 'Scheduled at Rajajinagar HMV ground'),
  (6, 19, 3, 3, 6, TIMESTAMP(CURDATE() - INTERVAL 28 DAY, '11:00:00'), 'FAIL', 'Failed emergency braking demonstration'),
  (7, 8, 3, 2, 4, TIMESTAMP(CURDATE() - INTERVAL 7 DAY, '09:30:00'), 'ABSENT', 'Candidate did not report to the test centre');

-- 14. Driving Licences (Covering ACTIVE, SUSPENDED, EXPIRED, REVOKED)
INSERT INTO driving_licences (driving_licence_id, licence_number, citizen_id, learner_licence_id, application_id, office_id, issue_date, expiry_date, current_status) VALUES
  (1, 'KA01-2026000001', 1, 1, 11, 1, (CURDATE() - INTERVAL 27 DAY), (CURDATE() + INTERVAL 7278 DAY), 'ACTIVE'),
  (2, 'KA01-2026000002', 2, 2, 10, 1, (CURDATE() - INTERVAL 73 DAY), (CURDATE() + INTERVAL 7232 DAY), 'ACTIVE'),
  (3, 'KA02-2025000015', 3, 3, 8, 2, (CURDATE() - INTERVAL 494 DAY), (CURDATE() + INTERVAL 6811 DAY), 'ACTIVE'),
  (4, 'KA03-2024000120', 4, 4, 6, 3, (CURDATE() - INTERVAL 946 DAY), (CURDATE() + INTERVAL 6359 DAY), 'ACTIVE'),
  (5, 'GJ01-2025000088', 5, NULL, 12, 5, (CURDATE() - INTERVAL 650 DAY), (CURDATE() + INTERVAL 6655 DAY), 'ACTIVE'),
  (6, 'KA01-2023000450', 6, NULL, 1, 1, (CURDATE() - INTERVAL 1289 DAY), (CURDATE() + INTERVAL 6016 DAY), 'SUSPENDED'),
  (7, 'KA02-2022000780', 20, NULL, 2, 2, (CURDATE() - INTERVAL 1447 DAY), (CURDATE() + INTERVAL 5858 DAY), 'SUSPENDED'),
  (8, 'KA01-2006000099', 8, NULL, 3, 1, (CURDATE() - INTERVAL 7526 DAY), (CURDATE() - INTERVAL 221 DAY), 'EXPIRED'),
  (9, 'KA03-2006000104', 9, NULL, 4, 3, (CURDATE() - INTERVAL 7368 DAY), (CURDATE() - INTERVAL 63 DAY), 'EXPIRED'),
  (10, 'KA01-2020000333', 18, NULL, 5, 1, (CURDATE() - INTERVAL 2348 DAY), (CURDATE() + INTERVAL 4957 DAY), 'REVOKED'),
  (11, 'KA02-2019000222', 19, NULL, 7, 2, (CURDATE() - INTERVAL 2595 DAY), (CURDATE() + INTERVAL 4710 DAY), 'REVOKED');

INSERT INTO licence_class_assignments (driving_licence_id, licence_class_id, granted_on) VALUES
  (1, 3, (CURDATE() - INTERVAL 27 DAY)), (1, 4, (CURDATE() - INTERVAL 27 DAY)),
  (2, 4, (CURDATE() - INTERVAL 73 DAY)),
  (3, 3, (CURDATE() - INTERVAL 494 DAY)), (3, 4, (CURDATE() - INTERVAL 494 DAY)),
  (4, 5, (CURDATE() - INTERVAL 946 DAY)), (4, 6, (CURDATE() - INTERVAL 946 DAY)),
  (5, 4, (CURDATE() - INTERVAL 650 DAY)),
  (6, 4, (CURDATE() - INTERVAL 1289 DAY)),
  (7, 3, (CURDATE() - INTERVAL 1447 DAY)), (7, 4, (CURDATE() - INTERVAL 1447 DAY)),
  (8, 4, (CURDATE() - INTERVAL 7526 DAY)),
  (9, 4, (CURDATE() - INTERVAL 7368 DAY)),
  (10, 4, (CURDATE() - INTERVAL 2348 DAY)),
  (11, 5, (CURDATE() - INTERVAL 2595 DAY));

INSERT INTO licence_status_history (history_id, driving_licence_id, previous_status, new_status, changed_by_user_id, changed_at, reason) VALUES
  (1, 6, 'ACTIVE', 'SUSPENDED', 1, TIMESTAMP(CURDATE() - INTERVAL 68 DAY, '10:00:00'), 'Suspended for 3 months following 3rd signal violation'),
  (2, 7, 'ACTIVE', 'SUSPENDED', 1, TIMESTAMP(CURDATE() - INTERVAL 42 DAY, '14:30:00'), 'Dangerous rash driving incident on Airport expressway'),
  (3, 8, 'ACTIVE', 'EXPIRED', 1, TIMESTAMP(CURDATE() - INTERVAL 220 DAY, '00:00:00'), 'Validity lapsed after 20 years'),
  (4, 9, 'ACTIVE', 'EXPIRED', 1, TIMESTAMP(CURDATE() - INTERVAL 62 DAY, '00:00:00'), 'Validity lapsed after 20 years'),
  (5, 10, 'ACTIVE', 'REVOKED', 1, TIMESTAMP(CURDATE() - INTERVAL 110 DAY, '11:15:00'), 'Revoked permanently under Section 19 due to fatal collision under influence'),
  (6, 11, 'ACTIVE', 'REVOKED', 1, TIMESTAMP(CURDATE() - INTERVAL 82 DAY, '16:00:00'), 'Revoked due to submission of forged heavy vehicle certificate');

-- 15. Vehicles, Ownership & Transfers (ACTIVE, BLACKLISTED, SCRAPPED, DEREGISTERED)
INSERT INTO vehicles (vehicle_id, registration_number, chassis_number, engine_number, manufacturer_id, model_id, vehicle_type_id, fuel_type_id, manufacture_year, color, registering_office_id, registration_date, status) VALUES
  (1, 'KA01AB1234', 'CH-SWIFT-90001', 'EN-SWIFT-90001', 1, 1, 2, 1, 2022, 'Pearl Arctic White', 1, (CURDATE() - INTERVAL 1682 DAY), 'ACTIVE'),
  (2, 'KA01CD5678', 'CH-NEXON-90002', 'EN-NEXON-90002', 3, 6, 2, 2, 2023, 'Daytona Grey', 1, (CURDATE() - INTERVAL 1251 DAY), 'ACTIVE'),
  (3, 'KA02EF9012', 'CH-SPLEN-90003', 'EN-SPLEN-90003', 6, 13, 1, 1, 2021, 'Black with Silver', 2, (CURDATE() - INTERVAL 1930 DAY), 'ACTIVE'),
  (4, 'KA02GH3456', 'CH-ATHER-90004', 'EN-ATHER-90004', 10, 17, 1, 4, 2024, 'Space Grey', 2, (CURDATE() - INTERVAL 977 DAY), 'ACTIVE'),
  (5, 'KA03IJ7890', 'CH-CRETA-90005', 'EN-CRETA-90005', 2, 4, 2, 2, 2022, 'Titan Grey', 3, (CURDATE() - INTERVAL 1447 DAY), 'ACTIVE'),
  (6, 'KA01KL2345', 'CH-BOLER-90006', 'EN-BOLER-90006', 4, 10, 4, 2, 2020, 'Diamond White', 1, (CURDATE() - INTERVAL 2260 DAY), 'ACTIVE'),
  (7, 'KA09MN6789', 'CH-ALDOST-90007', 'EN-ALDOST-90007', 11, 18, 3, 2, 2019, 'Golden Brown', 4, (CURDATE() - INTERVAL 2517 DAY), 'ACTIVE'),
  (8, 'GJ01OP0123', 'CH-ACTVA-90008', 'EN-ACTVA-90008', 5, 12, 1, 1, 2023, 'Matte Axis Grey', 5, (CURDATE() - INTERVAL 1276 DAY), 'ACTIVE'),
  (9, 'KA01ST4567', 'CH-THAR-90009', 'EN-THAR-90009', 4, 8, 2, 2, 2023, 'Napoli Black', 1, (CURDATE() - INTERVAL 1381 DAY), 'BLACKLISTED'),
  (10, 'KA02UV8901', 'CH-PULSR-90010', 'EN-PULSR-90010', 7, 14, 1, 1, 2022, 'Sparkle Blue', 2, (CURDATE() - INTERVAL 1495 DAY), 'BLACKLISTED'),
  (11, 'GJ01WX2345', 'CH-BALEN-90011', 'EN-BALEN-90011', 1, 2, 2, 1, 2021, 'Nexa Blue', 5, (CURDATE() - INTERVAL 1940 DAY), 'BLACKLISTED'),
  (12, 'KA01YZ6789', 'CH-INDCA-90012', 'EN-INDCA-90012', 3, 7, 2, 2, 2005, 'Silver', 1, (CURDATE() - INTERVAL 7863 DAY), 'SCRAPPED'),
  (13, 'KA02AA1122', 'CH-HERO-90013', 'EN-HERO-90013', 6, 13, 1, 1, 2004, 'Red', 2, (CURDATE() - INTERVAL 8079 DAY), 'SCRAPPED'),
  (14, 'KA01CC5566', 'CH-HYUND-90014', 'EN-HYUND-90014', 2, 5, 2, 1, 2018, 'Polar White', 1, (CURDATE() - INTERVAL 3172 DAY), 'DEREGISTERED'),
  (15, 'KA03DD7788', 'CH-HCITY-90015', 'EN-HCITY-90015', 5, 11, 2, 1, 2017, 'Modern Steel', 3, (CURDATE() - INTERVAL 3406 DAY), 'DEREGISTERED');

INSERT INTO vehicle_ownerships (ownership_id, vehicle_id, citizen_id, effective_from, effective_to) VALUES
  (1, 1, 1, (CURDATE() - INTERVAL 1682 DAY), NULL),
  (2, 2, 2, (CURDATE() - INTERVAL 1251 DAY), NULL),
  (3, 3, 3, (CURDATE() - INTERVAL 1930 DAY), NULL),
  (4, 4, 4, (CURDATE() - INTERVAL 977 DAY), NULL),
  (5, 5, 5, (CURDATE() - INTERVAL 1447 DAY), NULL),
  (6, 6, 6, (CURDATE() - INTERVAL 2260 DAY), NULL),
  (7, 7, 7, (CURDATE() - INTERVAL 2517 DAY), NULL),
  (8, 8, 8, (CURDATE() - INTERVAL 1276 DAY), NULL),
  (9, 9, 18, (CURDATE() - INTERVAL 1381 DAY), NULL),
  (10, 10, 19, (CURDATE() - INTERVAL 1495 DAY), NULL),
  (11, 11, 20, (CURDATE() - INTERVAL 1940 DAY), NULL),
  (12, 12, 9, (CURDATE() - INTERVAL 7863 DAY), NULL),
  (13, 13, 10, (CURDATE() - INTERVAL 8079 DAY), NULL),
  (14, 1, 11, (CURDATE() - INTERVAL 2477 DAY), (CURDATE() - INTERVAL 1683 DAY)),
  (15, 14, 12, (CURDATE() - INTERVAL 3172 DAY), (CURDATE() - INTERVAL 280 DAY)),
  (16, 15, 13, (CURDATE() - INTERVAL 3406 DAY), (CURDATE() - INTERVAL 226 DAY));

INSERT INTO ownership_transfers (transfer_id, vehicle_id, from_citizen_id, to_citizen_id, application_id, requested_at, approved_at, status) VALUES
  (1, 1, 11, 1, 6, TIMESTAMP(CURDATE() - INTERVAL 1696 DAY, '10:00:00'), TIMESTAMP(CURDATE() - INTERVAL 1683 DAY, '15:30:00'), 'APPROVED'),
  (2, 5, 5, 14, 15, TIMESTAMP(CURDATE() - INTERVAL 5 DAY, '13:20:00'), NULL, 'PENDING'),
  (3, 3, 3, 15, 16, TIMESTAMP(CURDATE() - INTERVAL 12 DAY, '11:00:00'), NULL, 'PENDING'),
  (4, 2, 2, 16, 7, TIMESTAMP(CURDATE() - INTERVAL 37 DAY, '09:30:00'), TIMESTAMP(CURDATE() - INTERVAL 32 DAY, '14:00:00'), 'REJECTED');

-- 16. Vehicle Compliance & Inspections (Designed for 7, 15, 30, 60, 90 day filter thresholds)
INSERT INTO vehicle_inspections (inspection_id, vehicle_id, inspector_employee_id, inspected_at, result, remarks) VALUES
  (1, 6, 3, TIMESTAMP(CURDATE() - INTERVAL 362 DAY, '10:00:00'), 'PASS', 'All safety lighting, air brakes, and chassis rivets intact'),
  (2, 7, 4, TIMESTAMP(CURDATE() - INTERVAL 351 DAY, '11:30:00'), 'PASS', 'Commercial vehicle fitness inspection passed'),
  (3, 1, 2, TIMESTAMP(CURDATE() - INTERVAL 338 DAY, '14:00:00'), 'PASS', 'Headlight high/low beam aligned; emission levels within limits'),
  (4, 2, 2, TIMESTAMP(CURDATE() - INTERVAL 311 DAY, '09:30:00'), 'PASS', 'Comprehensive annual mechanical check verified'),
  (5, 9, 3, TIMESTAMP(CURDATE() - INTERVAL 438 DAY, '15:00:00'), 'FAIL', 'Brake fluid leakage and severely cracked front windshield'),
  (6, 10, 4, TIMESTAMP(CURDATE() - INTERVAL 402 DAY, '16:15:00'), 'FAIL', 'Silencer catalytic converter removed; excessive decibel noise');

INSERT INTO fitness_certificates (certificate_id, vehicle_id, inspection_id, issue_date, expiry_date, status) VALUES
  (1, 6, 1, (CURDATE() - INTERVAL 361 DAY), (CURDATE() + INTERVAL 4 DAY), 'ACTIVE'),
  (2, 7, 2, (CURDATE() - INTERVAL 351 DAY), (CURDATE() + INTERVAL 14 DAY), 'ACTIVE'),
  (3, 1, 3, (CURDATE() - INTERVAL 338 DAY), (CURDATE() + INTERVAL 27 DAY), 'ACTIVE'),
  (4, 2, 4, (CURDATE() - INTERVAL 311 DAY), (CURDATE() + INTERVAL 54 DAY), 'ACTIVE'),
  (5, 3, NULL, (CURDATE() - INTERVAL 280 DAY), (CURDATE() + INTERVAL 85 DAY), 'ACTIVE'),
  (6, 9, 5, (CURDATE() - INTERVAL 438 DAY), (CURDATE() - INTERVAL 73 DAY), 'EXPIRED'),
  (7, 12, NULL, (CURDATE() - INTERVAL 923 DAY), (CURDATE() - INTERVAL 558 DAY), 'EXPIRED'),
  (8, 4, NULL, (CURDATE() - INTERVAL 143 DAY), (CURDATE() + INTERVAL 222 DAY), 'REVOKED');  -- revoked while still within validity

INSERT INTO pollution_certificates (puc_id, vehicle_id, issue_date, expiry_date, status) VALUES
  (1, 1, (CURDATE() - INTERVAL 178 DAY), (CURDATE() + INTERVAL 5 DAY), 'ACTIVE'),
  (2, 3, (CURDATE() - INTERVAL 173 DAY), (CURDATE() + INTERVAL 11 DAY), 'ACTIVE'),
  (3, 5, (CURDATE() - INTERVAL 160 DAY), (CURDATE() + INTERVAL 24 DAY), 'ACTIVE'),
  (4, 6, (CURDATE() - INTERVAL 124 DAY), (CURDATE() + INTERVAL 59 DAY), 'ACTIVE'),
  (5, 8, (CURDATE() - INTERVAL 96 DAY), (CURDATE() + INTERVAL 88 DAY), 'ACTIVE'),
  (6, 2, (CURDATE() - INTERVAL 221 DAY), (CURDATE() - INTERVAL 37 DAY), 'EXPIRED'),
  (7, 7, (CURDATE() - INTERVAL 275 DAY), (CURDATE() - INTERVAL 94 DAY), 'EXPIRED');

INSERT INTO insurance_policies (policy_id, vehicle_id, provider_name, policy_number, start_date, end_date) VALUES
  (1, 1, 'New India Assurance', 'NIA-KA-2025-00192', (CURDATE() - INTERVAL 359 DAY), (CURDATE() + INTERVAL 6 DAY)),
  (2, 3, 'HDFC ERGO General Insurance', 'HDF-KA-2025-00441', (CURDATE() - INTERVAL 352 DAY), (CURDATE() + INTERVAL 13 DAY)),
  (3, 6, 'ICICI Lombard General Insurance', 'ICI-KA-2025-00812', (CURDATE() - INTERVAL 337 DAY), (CURDATE() + INTERVAL 28 DAY)),
  (4, 2, 'Bajaj Allianz General Insurance', 'BAJ-KA-2025-00334', (CURDATE() - INTERVAL 301 DAY), (CURDATE() + INTERVAL 64 DAY)),
  (5, 5, 'Tata AIG General Insurance', 'AIG-KA-2026-00911', (CURDATE() - INTERVAL 275 DAY), (CURDATE() + INTERVAL 90 DAY)),
  (6, 4, 'Oriental Insurance Company', 'ORI-KA-2025-00045', (CURDATE() - INTERVAL 479 DAY), (CURDATE() - INTERVAL 114 DAY)),
  (7, 7, 'National Insurance Company', 'NIC-KA-2025-00789', (CURDATE() - INTERVAL 433 DAY), (CURDATE() - INTERVAL 68 DAY));

INSERT INTO road_tax_records (tax_record_id, vehicle_id, assessment_year, amount_due, due_date, status) VALUES
  (1, 1, 2026, 4500.00, (CURDATE() - INTERVAL 205 DAY), 'PAID'),
  (2, 2, 2026, 5200.00, (CURDATE() - INTERVAL 205 DAY), 'PAID'),
  (3, 3, 2026, 1200.00, (CURDATE() - INTERVAL 205 DAY), 'PAID'),
  (4, 4, 2026, 800.00, (CURDATE() - INTERVAL 205 DAY), 'PAID'),
  (5, 5, 2026, 5500.00, (CURDATE() + INTERVAL 39 DAY), 'DUE'),
  (6, 6, 2026, 8200.00, (CURDATE() + INTERVAL 24 DAY), 'DUE'),
  (7, 7, 2026, 12500.00, (CURDATE() - INTERVAL 52 DAY), 'OVERDUE'),
  (8, 8, 2026, 1500.00, (CURDATE() - INTERVAL 22 DAY), 'OVERDUE'),
  (9, 9, 2026, 6500.00, (CURDATE() - INTERVAL 83 DAY), 'OVERDUE');

-- 17. Routes, Segments & Commercial Permits (ACTIVE, EXPIRED, SUSPENDED, CANCELLED)
INSERT INTO routes (route_id, route_name, origin, destination) VALUES
  (1, 'Bengaluru – Mysuru Expressway Route', 'Bengaluru', 'Mysuru'),
  (2, 'Bengaluru – Chennai Industrial Corridor', 'Bengaluru', 'Chennai'),
  (3, 'Ahmedabad – Surat Superfast Highway', 'Ahmedabad', 'Surat'),
  (4, 'Bengaluru – Mangaluru Coastal Route', 'Bengaluru', 'Mangaluru');

INSERT INTO route_segments (segment_id, route_id, sequence_no, segment_name) VALUES
  (1, 1, 1, 'Bengaluru Kengeri'), (2, 1, 2, 'Bidadi Industrial Area'), (3, 1, 3, 'Ramanagara'), (4, 1, 4, 'Mandya'), (5, 1, 5, 'Mysuru Ring Road'),
  (6, 2, 1, 'Bengaluru Electronic City'), (7, 2, 2, 'Hosur'), (8, 2, 3, 'Krishnagiri'), (9, 2, 4, 'Vellore'), (10, 2, 5, 'Chennai Sriperumbudur'),
  (11, 3, 1, 'Ahmedabad Subhash Bridge'), (12, 3, 2, 'Nadiad'), (13, 3, 3, 'Anand'), (14, 3, 4, 'Vadodara Bypass'), (15, 3, 5, 'Surat Ring Road');

INSERT INTO permits (permit_id, permit_number, vehicle_id, citizen_id, permit_type_id, route_id, application_id, issue_date, expiry_date, status) VALUES
  (1, 'PERM-KA01-2026-001', 6, 6, 1, 1, 8, (CURDATE() - INTERVAL 280 DAY), (CURDATE() + INTERVAL 1546 DAY), 'ACTIVE'),
  (2, 'PERM-KA01-2026-002', 7, 7, 2, 2, 8, (CURDATE() - INTERVAL 346 DAY), (CURDATE() + INTERVAL 1480 DAY), 'ACTIVE'),
  (3, 'PERM-KA02-2026-003', 1, 1, 3, 1, 8, (CURDATE() - INTERVAL 351 DAY), (CURDATE() + INTERVAL 14 DAY), 'ACTIVE'),
  (4, 'PERM-GJ01-2025-004', 8, 8, 4, 3, 8, (CURDATE() - INTERVAL 569 DAY), (CURDATE() + INTERVAL 1257 DAY), 'ACTIVE'),
  (5, 'PERM-KA01-2021-005', 6, 6, 5, 1, 8, (CURDATE() - INTERVAL 82 DAY), (CURDATE() - INTERVAL 51 DAY), 'EXPIRED'),
  (6, 'PERM-KA02-2021-006', 7, 7, 5, 2, 8, (CURDATE() - INTERVAL 99 DAY), (CURDATE() - INTERVAL 68 DAY), 'EXPIRED'),
  (7, 'PERM-KA01-2024-007', 6, 6, 1, 1, 8, (CURDATE() - INTERVAL 895 DAY), (CURDATE() + INTERVAL 931 DAY), 'SUSPENDED'),
  (8, 'PERM-KA03-2023-008', 7, 7, 2, 4, 8, (CURDATE() - INTERVAL 1346 DAY), (CURDATE() + INTERVAL 480 DAY), 'CANCELLED');

INSERT INTO permit_status_history (history_id, permit_id, previous_status, new_status, changed_at, reason) VALUES
  (1, 5, 'ACTIVE', 'EXPIRED', TIMESTAMP(CURDATE() - INTERVAL 50 DAY, '00:00:00'), 'Validity period completed'),
  (2, 6, 'ACTIVE', 'EXPIRED', TIMESTAMP(CURDATE() - INTERVAL 67 DAY, '00:00:00'), 'Validity period completed'),
  (3, 7, 'ACTIVE', 'SUSPENDED', TIMESTAMP(CURDATE() - INTERVAL 40 DAY, '11:30:00'), 'Overloading by 4.8 tonnes flagged at weighbridge'),
  (4, 8, 'ACTIVE', 'CANCELLED', TIMESTAMP(CURDATE() - INTERVAL 94 DAY, '15:45:00'), 'Commercial vehicle operated on non-permitted diversion corridor');

-- 18. Violations & E-Challans (Covering ISSUED, DISPUTED, PAID, CANCELLED)
INSERT INTO violations (violation_id, vehicle_id, driver_citizen_id, violation_type_id, officer_employee_id, location, occurred_at) VALUES
  (1, 1, 10, 1, 4, 'Hosur Road Electronic City Flyover', TIMESTAMP(CURDATE() - INTERVAL 7 DAY, '08:30:00')),
  (2, 1, 10, 3, 4, 'Madiwala St. Johns Signal', TIMESTAMP(CURDATE() - INTERVAL 7 DAY, '08:45:00')),
  (3, 2, 11, 5, 4, 'Outer Ring Road, Marathahalli', TIMESTAMP(CURDATE() - INTERVAL 10 DAY, '11:20:00')),
  (4, 3, 12, 6, 6, '100ft Road Indiranagar Junction', TIMESTAMP(CURDATE() - INTERVAL 12 DAY, '17:15:00')),
  (5, 5, 14, 4, 4, 'Koramangala 80ft Road, Sony Signal', TIMESTAMP(CURDATE() - INTERVAL 14 DAY, '22:30:00')),
  (6, 6, 15, 2, 6, 'Yeshwantpur Toll Gate', TIMESTAMP(CURDATE() - INTERVAL 24 DAY, '14:00:00')),
  (7, 7, 16, 7, 4, 'Mysuru Road Satellite Bus Stand', TIMESTAMP(CURDATE() - INTERVAL 32 DAY, '19:45:00')),
  (8, 8, 17, 1, 8, 'Ahmedabad SG Highway Karnavati Club', TIMESTAMP(CURDATE() - INTERVAL 17 DAY, '10:10:00')),
  (9, 9, 18, 8, 4, 'MG Road Brigade Junction', TIMESTAMP(CURDATE() - INTERVAL 34 DAY, '16:00:00')),
  (10, 10, 19, 7, 6, 'Rajajinagar 1st Block Navrang Circle', TIMESTAMP(CURDATE() - INTERVAL 37 DAY, '21:00:00'));

INSERT INTO challans (challan_id, challan_number, total_amount, status, issued_at) VALUES
  (1, 'CHAL-2026-000001', 1500.0, 'ISSUED', TIMESTAMP(CURDATE() - INTERVAL 7 DAY, '09:00:00')),
  (2, 'CHAL-2026-000002', 1500.0, 'ISSUED', TIMESTAMP(CURDATE() - INTERVAL 10 DAY, '12:00:00')),
  (3, 'CHAL-2026-000003', 10000.0, 'DISPUTED', TIMESTAMP(CURDATE() - INTERVAL 14 DAY, '23:00:00')),
  (4, 'CHAL-2026-000004', 1000.0, 'DISPUTED', TIMESTAMP(CURDATE() - INTERVAL 12 DAY, '18:00:00')),
  (5, 'CHAL-2026-000005', 5000.0, 'PAID', TIMESTAMP(CURDATE() - INTERVAL 24 DAY, '15:00:00')),
  (6, 'CHAL-2026-000006', 5000.0, 'PAID', TIMESTAMP(CURDATE() - INTERVAL 32 DAY, '20:30:00')),
  (7, 'CHAL-2026-000007', 1000.0, 'PAID', TIMESTAMP(CURDATE() - INTERVAL 17 DAY, '11:00:00')),
  (8, 'CHAL-2026-000008', 2000.0, 'CANCELLED', TIMESTAMP(CURDATE() - INTERVAL 34 DAY, '17:00:00')),
  (9, 'CHAL-2026-000009', 5000.0, 'CANCELLED', TIMESTAMP(CURDATE() - INTERVAL 37 DAY, '22:00:00'));

INSERT INTO challan_violations (challan_id, violation_id) VALUES
  (1, 1), (1, 2), (2, 3), (3, 5), (4, 4), (5, 6), (6, 7), (7, 8), (8, 9), (9, 10);

INSERT INTO challan_status_history (history_id, challan_id, previous_status, new_status, changed_at) VALUES
  (1, 1, 'NULL', 'ISSUED', TIMESTAMP(CURDATE() - INTERVAL 7 DAY, '09:00:00')),
  (2, 3, 'ISSUED', 'DISPUTED', TIMESTAMP(CURDATE() - INTERVAL 12 DAY, '14:00:00')),
  (3, 4, 'ISSUED', 'DISPUTED', TIMESTAMP(CURDATE() - INTERVAL 10 DAY, '11:30:00')),
  (4, 5, 'ISSUED', 'PAID', TIMESTAMP(CURDATE() - INTERVAL 23 DAY, '10:15:00')),
  (5, 6, 'ISSUED', 'PAID', TIMESTAMP(CURDATE() - INTERVAL 30 DAY, '16:45:00')),
  (6, 7, 'ISSUED', 'PAID', TIMESTAMP(CURDATE() - INTERVAL 16 DAY, '09:30:00')),
  (7, 8, 'ISSUED', 'CANCELLED', TIMESTAMP(CURDATE() - INTERVAL 32 DAY, '12:00:00')),
  (8, 9, 'ISSUED', 'CANCELLED', TIMESTAMP(CURDATE() - INTERVAL 34 DAY, '10:00:00'));

-- 19. Fee Structures, Payments & Refunds (SUCCESS, PENDING, FAILED, REFUNDED)
INSERT INTO fee_structures (fee_id, service_type_id, component_name, amount, effective_from, effective_to) VALUES
  (1, 1, 'DL Smart Card Fee', 200.00, (CURDATE() - INTERVAL 1025 DAY), NULL),
  (2, 1, 'Automated Driving Test Fee', 300.00, (CURDATE() - INTERVAL 1025 DAY), NULL),
  (3, 2, 'Licence Renewal Processing Charge', 300.00, (CURDATE() - INTERVAL 1025 DAY), NULL),
  (4, 3, 'Vehicle Ownership Transfer Endorsement', 750.00, (CURDATE() - INTERVAL 1025 DAY), NULL),
  (5, 4, 'Commercial Route Permit Issuance', 1000.00, (CURDATE() - INTERVAL 1025 DAY), NULL),
  (6, 5, 'Vehicle Registration Smart Card & Number Plate', 800.00, (CURDATE() - INTERVAL 1025 DAY), NULL),
  (7, 6, 'Learner Licence Computer Test Fee', 200.00, (CURDATE() - INTERVAL 1025 DAY), NULL);

INSERT INTO payments (payment_id, receipt_number, payable_type_id, payable_id, amount, status, paid_at, created_at) VALUES
  (1, 'RCT-2026-000001', 1, 11, 500.0, 'SUCCESS', TIMESTAMP(CURDATE() - INTERVAL 37 DAY, '11:00:00'), TIMESTAMP(CURDATE() - INTERVAL 37 DAY, '10:55:00')),
  (2, 'RCT-2026-000002', 1, 10, 200.0, 'SUCCESS', TIMESTAMP(CURDATE() - INTERVAL 20 DAY, '09:20:00'), TIMESTAMP(CURDATE() - INTERVAL 20 DAY, '09:18:00')),
  (3, 'RCT-2026-000003', 2, 5, 5000.0, 'SUCCESS', TIMESTAMP(CURDATE() - INTERVAL 23 DAY, '10:15:00'), TIMESTAMP(CURDATE() - INTERVAL 23 DAY, '10:10:00')),
  (4, 'RCT-2026-000004', 2, 6, 5000.0, 'SUCCESS', TIMESTAMP(CURDATE() - INTERVAL 30 DAY, '16:45:00'), TIMESTAMP(CURDATE() - INTERVAL 30 DAY, '16:40:00')),
  (5, 'RCT-2026-000005', 3, 1, 1000.0, 'SUCCESS', TIMESTAMP(CURDATE() - INTERVAL 280 DAY, '11:00:00'), TIMESTAMP(CURDATE() - INTERVAL 280 DAY, '10:50:00')),
  (6, 'RCT-2026-000006', 4, 1, 4500.0, 'SUCCESS', TIMESTAMP(CURDATE() - INTERVAL 211 DAY, '14:30:00'), TIMESTAMP(CURDATE() - INTERVAL 211 DAY, '14:20:00')),
  (7, 'RCT-2026-000007', 4, 2, 5200.0, 'SUCCESS', TIMESTAMP(CURDATE() - INTERVAL 208 DAY, '16:00:00'), TIMESTAMP(CURDATE() - INTERVAL 208 DAY, '15:50:00')),
  (8, 'RCT-2026-000008', 1, 5, 800.0, 'PENDING', NULL, TIMESTAMP(CURDATE() - INTERVAL 7 DAY, '15:35:00')),
  (9, 'RCT-2026-000009', 2, 1, 1500.0, 'PENDING', NULL, TIMESTAMP(CURDATE() - INTERVAL 6 DAY, '11:00:00')),
  (10, 'RCT-2026-000010', 1, 17, 300.0, 'FAILED', NULL, TIMESTAMP(CURDATE() - INTERVAL 5 DAY, '14:45:00')),
  (11, 'RCT-2026-000011', 1, 1, 500.0, 'FAILED', NULL, TIMESTAMP(CURDATE() - INTERVAL 1 DAY, '09:35:00')),
  (12, 'RCT-2026-000012', 1, 9, 400.0, 'REFUNDED', TIMESTAMP(CURDATE() - INTERVAL 21 DAY, '12:15:00'), TIMESTAMP(CURDATE() - INTERVAL 21 DAY, '12:05:00')),
  (13, 'RCT-2026-000013', 2, 8, 2000.0, 'REFUNDED', TIMESTAMP(CURDATE() - INTERVAL 34 DAY, '18:00:00'), TIMESTAMP(CURDATE() - INTERVAL 34 DAY, '17:50:00'));

INSERT INTO payment_attempts (attempt_id, payment_id, attempt_number, gateway_reference, outcome, attempted_at) VALUES
  (1, 1, 1, 'GW-SBIPAY-88001', 'SUCCESS', TIMESTAMP(CURDATE() - INTERVAL 37 DAY, '11:00:00')),
  (2, 2, 1, 'GW-HDFCPG-88002', 'SUCCESS', TIMESTAMP(CURDATE() - INTERVAL 20 DAY, '09:20:00')),
  (3, 3, 1, 'GW-PAYTM-88003', 'SUCCESS', TIMESTAMP(CURDATE() - INTERVAL 23 DAY, '10:15:00')),
  (4, 4, 1, 'GW-RAZOR-88004', 'SUCCESS', TIMESTAMP(CURDATE() - INTERVAL 30 DAY, '16:45:00')),
  (5, 8, 1, 'GW-SBIPAY-88005', 'TIMEOUT', TIMESTAMP(CURDATE() - INTERVAL 7 DAY, '15:36:00')),
  (6, 10, 1, 'GW-ICICI-88006', 'FAILED', TIMESTAMP(CURDATE() - INTERVAL 5 DAY, '14:46:00')),
  (7, 11, 1, 'GW-SBIPAY-88007', 'FAILED', TIMESTAMP(CURDATE() - INTERVAL 1 DAY, '09:36:00')),
  (8, 12, 1, 'GW-HDFCPG-88008', 'SUCCESS', TIMESTAMP(CURDATE() - INTERVAL 21 DAY, '12:15:00')),
  (9, 13, 1, 'GW-RAZOR-88009', 'SUCCESS', TIMESTAMP(CURDATE() - INTERVAL 34 DAY, '18:00:00'));

INSERT INTO refunds (refund_id, payment_id, amount, reason, status, processed_at) VALUES
  (1, 12, 400.00, 'Duplicate Application Cancelled by citizen within grace period', 'COMPLETED', TIMESTAMP(CURDATE() - INTERVAL 19 DAY, '14:00:00')),
  (2, 13, 2000.00, 'Challan quashed by Appellate Tribunal order #AT-2026-08', 'COMPLETED', TIMESTAMP(CURDATE() - INTERVAL 27 DAY, '16:30:00')),
  (3, 3, 5000.00, 'Double deduction during UPI server glitch', 'INITIATED', NULL),
  (4, 1, 500.00, 'Citizen requested withdrawal of DL application', 'INITIATED', NULL),
  (5, 4, 1500.00, 'Bank account IFSC code mismatch during NEFT reversal', 'FAILED', TIMESTAMP(CURDATE() - INTERVAL 28 DAY, '11:20:00'));

-- 20. Citizen Complaints & Grievance Tickets
INSERT INTO complaints (complaint_id, citizen_id, office_id, subject, description, status, filed_at) VALUES
  (1, 1, 1, 'High wait time at Koramangala counter C-02', 'Waited for 2.5 hours past scheduled token time. Only one officer was processing vehicle registration paperwork.', 'OPEN', TIMESTAMP(CURDATE() - INTERVAL 1 DAY, '14:30:00')),
  (2, 2, 2, 'Driving Licence Smart Card dispatch delayed', 'Passed driving test 4 weeks ago; smart card tracking ID shows not dispatched from Centralized Print Facility.', 'OPEN', TIMESTAMP(CURDATE() - INTERVAL 2 DAY, '11:15:00')),
  (3, 3, 1, 'Excessive user fee collected for duplicate NOC', 'Charged additional 200 rupees convenience fee without printed government receipt.', 'IN_PROGRESS', TIMESTAMP(CURDATE() - INTERVAL 4 DAY, '16:00:00')),
  (4, 4, 3, 'Incorrect spelling of father name on RC Book', 'Application Form 20 had correct spelling "SURYAKANT", but printed RC shows "SURAYKANT". Need correction.', 'IN_PROGRESS', TIMESTAMP(CURDATE() - INTERVAL 6 DAY, '10:45:00')),
  (5, 5, 1, 'Automated Test Track sensor malfunction', 'Track sensor flagged boundary foul during reverse S manoeuvre even though vehicle tyres were 8 inches inside yellow marker.', 'RESOLVED', TIMESTAMP(CURDATE() - INTERVAL 12 DAY, '09:30:00')),
  (6, 6, 2, 'Online slot booking calendar freezing at 10 AM', 'System throws Gateway Timeout 504 when attempting to book morning inspection slot for heavy vehicles.', 'RESOLVED', TIMESTAMP(CURDATE() - INTERVAL 17 DAY, '10:15:00')),
  (7, 7, 5, 'RTO Ahmedabad counter closed during official public hours', 'Arrived at 12:45 PM for fitness certificate collection, counter shutter was down before designated lunch break.', 'CLOSED', TIMESTAMP(CURDATE() - INTERVAL 27 DAY, '15:00:00')),
  (8, 8, 1, 'Duplicate payment deducted for Learner Licence slot', 'UPI payment debited twice for appointment token. Need refund credited back to Kotak bank account.', 'CLOSED', TIMESTAMP(CURDATE() - INTERVAL 34 DAY, '12:00:00'));

-- 21. Legal & Statutory Appeals (CHALLAN, APPLICATION_REJECTION, LICENCE_SUSPENSION)
INSERT INTO appeals (appeal_id, citizen_id, against_type, against_id, grounds, status, filed_at, resolved_at) VALUES
  (1, 10, 'CHALLAN', 3, 'Challan issued for alleged drunk driving at 11 PM, but government hospital blood test report shows 0.00 BAC. Request quashing of 10000 penalty.', 'FILED', TIMESTAMP(CURDATE() - INTERVAL 10 DAY, '11:00:00'), NULL),
  (2, 11, 'APPLICATION_REJECTION', 7, 'Driving test rejected claiming parallel bay kerb touch, but Dashcam footage proves vehicle remained within designated parking bay. Appeal for re-evaluation.', 'UNDER_REVIEW', TIMESTAMP(CURDATE() - INTERVAL 8 DAY, '14:30:00'), NULL),
  (3, 12, 'LICENCE_SUSPENSION', 6, 'Licence suspended following 3 red light cameras, but vehicle was stolen on 2026-08-10 with police FIR #312 registered at Madiwala station.', 'UPHELD', TIMESTAMP(CURDATE() - INTERVAL 63 DAY, '10:00:00'), TIMESTAMP(CURDATE() - INTERVAL 47 DAY, '16:00:00')),
  (4, 13, 'CHALLAN', 4, 'Speed radar captured adjacent overtaking BMW, not my vehicle which was cruising at 52 km/h. Optical radar calibration certificate is expired.', 'UPHELD', TIMESTAMP(CURDATE() - INTERVAL 11 DAY, '15:00:00'), TIMESTAMP(CURDATE() - INTERVAL 4 DAY, '11:30:00')),
  (5, 18, 'LICENCE_SUSPENSION', 7, 'Appellant claims emergency medical transit during rash driving incident, but failed to provide hospital admission record or ambulance escort slip.', 'DISMISSED', TIMESTAMP(CURDATE() - INTERVAL 37 DAY, '09:30:00'), TIMESTAMP(CURDATE() - INTERVAL 24 DAY, '14:00:00')),
  (6, 19, 'APPLICATION_REJECTION', 19, 'Rejection of commercial permit grounds upheld as vehicle chassis length exceeds maximum permissible length for intra-city school bus transport.', 'DISMISSED', TIMESTAMP(CURDATE() - INTERVAL 22 DAY, '11:00:00'), TIMESTAMP(CURDATE() - INTERVAL 14 DAY, '17:00:00'));

-- 22. Digital Notifications (SMS & EMAIL) & System Audit Logs
INSERT INTO notifications (notification_id, person_id, channel, subject, message, status, sent_at) VALUES
  (1, 10, 'SMS', NULL, 'Your DL application APP-2026-00001 has been successfully submitted. Tracking Token: APP-00001.', 'SENT', TIMESTAMP(CURDATE() - INTERVAL 1 DAY, '09:32:00')),
  (2, 11, 'EMAIL', 'Document Re-upload Required', 'Medical Certificate Form 1A for APP-2026-00002 was rejected due to missing doctor stamp. Please re-upload.', 'SENT', TIMESTAMP(CURDATE() - INTERVAL 2 DAY, '11:32:00')),
  (3, 12, 'SMS', NULL, 'E-Challan CHAL-2026-000001 of Rs. 1500 issued for vehicle KA01AB1234. Pay online via Parivahan/RTO portal.', 'SENT', TIMESTAMP(CURDATE() - INTERVAL 7 DAY, '09:05:00')),
  (4, 13, 'EMAIL', 'Driving Licence Approved', 'Congratulations! Your Permanent Driving Licence KA01-2026000001 has been APPROVED. Smart card queued for dispatch.', 'SENT', TIMESTAMP(CURDATE() - INTERVAL 27 DAY, '15:46:00')),
  (5, 14, 'SMS', NULL, 'Appointment scheduled for driving test on 2026-10-24 10:00 at Koramangala Test Track. Token: TKN-KA02-001.', 'QUEUED', NULL),
  (6, 15, 'EMAIL', 'Commercial Permit Renewal Notice', 'Your permit PERM-KA02-2026-003 is expiring in 14 days. Please apply for extension to avoid enforcement penalties.', 'QUEUED', NULL),
  (7, 16, 'SMS', NULL, 'Payment gateway alert: Transaction timeout for receipt RCT-2026-000008. Amount will be refunded within 3 days if debited.', 'FAILED', TIMESTAMP(CURDATE() - INTERVAL 7 DAY, '15:40:00'));

INSERT INTO audit_logs (audit_id, table_name, record_id, action, old_values, new_values, changed_by_user_id, changed_at) VALUES
  (1, 'applications', 1, 'INSERT', NULL, '{"application_number":"APP-2026-00001","status":"SUBMITTED"}', 1, TIMESTAMP(CURDATE() - INTERVAL 1 DAY, '09:30:00')),
  (2, 'applications', 2, 'UPDATE', '{"status":"SUBMITTED"}', '{"status":"DOCS_PENDING","reason":"Missing doctor stamp"}', 2, TIMESTAMP(CURDATE() - INTERVAL 2 DAY, '11:30:00')),
  (3, 'vehicles', 1, 'INSERT', NULL, '{"registration_number":"KA01AB1234","status":"ACTIVE"}', 1, TIMESTAMP(CURDATE() - INTERVAL 1682 DAY, '10:00:00')),
  (4, 'driving_licences', 1, 'INSERT', NULL, '{"licence_number":"KA01-2026000001","status":"ACTIVE"}', 2, TIMESTAMP(CURDATE() - INTERVAL 27 DAY, '15:45:00')),
  (5, 'driving_licences', 6, 'UPDATE', '{"status":"ACTIVE"}', '{"status":"SUSPENDED","reason":"Section 19 suspension"}', 1, TIMESTAMP(CURDATE() - INTERVAL 68 DAY, '10:00:00')),
  (6, 'challans', 1, 'INSERT', NULL, '{"challan_number":"CHAL-2026-000001","total_amount":1500.00,"status":"ISSUED"}', 4, TIMESTAMP(CURDATE() - INTERVAL 7 DAY, '09:00:00')),
  (7, 'challans', 5, 'UPDATE', '{"status":"ISSUED"}', '{"status":"PAID","receipt_number":"RCT-2026-000003"}', 2, TIMESTAMP(CURDATE() - INTERVAL 23 DAY, '10:15:00')),
  (8, 'complaints', 1, 'INSERT', NULL, '{"citizen_id":1,"office_id":1,"status":"OPEN"}', 6, TIMESTAMP(CURDATE() - INTERVAL 1 DAY, '14:30:00')),
  (9, 'permits', 1, 'INSERT', NULL, '{"permit_number":"PERM-KA01-2026-001","status":"ACTIVE"}', 2, TIMESTAMP(CURDATE() - INTERVAL 280 DAY, '11:00:00')),
  (10, 'appeals', 3, 'UPDATE', '{"status":"UNDER_REVIEW"}', '{"status":"UPHELD","resolution":"FIR confirmed vehicle theft"}', 1, TIMESTAMP(CURDATE() - INTERVAL 47 DAY, '16:00:00')),
  (11, 'documents', 12, 'DELETE', '{"verification_status":"REJECTED","file_path":"storage/app14/water_bill.pdf"}', NULL, 1, TIMESTAMP(CURDATE() - INTERVAL 2 DAY, '09:30:00')),
  (12, 'applications', 4, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":12}', 3, TIMESTAMP(CURDATE() - INTERVAL 27 DAY, '10:24:00')),
  (13, 'vehicles', 5, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":13}', 4, TIMESTAMP(CURDATE() - INTERVAL 25 DAY, '11:31:00')),
  (14, 'payments', 6, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":14}', 5, TIMESTAMP(CURDATE() - INTERVAL 51 DAY, '12:38:00')),
  (15, 'citizens', 7, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":15}', 1, TIMESTAMP(CURDATE() - INTERVAL 49 DAY, '13:45:00')),
  (16, 'challans', 8, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":16}', 2, TIMESTAMP(CURDATE() - INTERVAL 47 DAY, '14:52:00')),
  (17, 'documents', 9, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":17}', 3, TIMESTAMP(CURDATE() - INTERVAL 45 DAY, '15:59:00')),
  (18, 'applications', 1, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":18}', 4, TIMESTAMP(CURDATE() - INTERVAL 43 DAY, '16:06:00')),
  (19, 'vehicles', 2, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":19}', 5, TIMESTAMP(CURDATE() - INTERVAL 41 DAY, '17:13:00')),
  (20, 'payments', 3, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":20}', 1, TIMESTAMP(CURDATE() - INTERVAL 39 DAY, '08:20:00')),
  (21, 'citizens', 4, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":21}', 2, TIMESTAMP(CURDATE() - INTERVAL 37 DAY, '09:27:00')),
  (22, 'challans', 5, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":22}', 3, TIMESTAMP(CURDATE() - INTERVAL 35 DAY, '10:34:00')),
  (23, 'documents', 6, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":23}', 4, TIMESTAMP(CURDATE() - INTERVAL 33 DAY, '11:41:00')),
  (24, 'applications', 7, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":24}', 5, TIMESTAMP(CURDATE() - INTERVAL 31 DAY, '12:48:00')),
  (25, 'vehicles', 8, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":25}', 1, TIMESTAMP(CURDATE() - INTERVAL 29 DAY, '13:55:00')),
  (26, 'payments', 9, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":26}', 2, TIMESTAMP(CURDATE() - INTERVAL 27 DAY, '14:02:00')),
  (27, 'citizens', 1, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":27}', 3, TIMESTAMP(CURDATE() - INTERVAL 25 DAY, '15:09:00')),
  (28, 'challans', 2, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":28}', 4, TIMESTAMP(CURDATE() - INTERVAL 51 DAY, '16:16:00')),
  (29, 'documents', 3, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":29}', 5, TIMESTAMP(CURDATE() - INTERVAL 49 DAY, '17:23:00')),
  (30, 'applications', 4, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":30}', 1, TIMESTAMP(CURDATE() - INTERVAL 47 DAY, '08:30:00')),
  (31, 'vehicles', 5, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":31}', 2, TIMESTAMP(CURDATE() - INTERVAL 45 DAY, '09:37:00')),
  (32, 'payments', 6, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":32}', 3, TIMESTAMP(CURDATE() - INTERVAL 43 DAY, '10:44:00')),
  (33, 'citizens', 7, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":33}', 4, TIMESTAMP(CURDATE() - INTERVAL 41 DAY, '11:51:00')),
  (34, 'challans', 8, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":34}', 5, TIMESTAMP(CURDATE() - INTERVAL 39 DAY, '12:58:00')),
  (35, 'documents', 9, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":35}', 1, TIMESTAMP(CURDATE() - INTERVAL 37 DAY, '13:05:00')),
  (36, 'applications', 1, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":36}', 2, TIMESTAMP(CURDATE() - INTERVAL 35 DAY, '14:12:00')),
  (37, 'vehicles', 2, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":37}', 3, TIMESTAMP(CURDATE() - INTERVAL 33 DAY, '15:19:00')),
  (38, 'payments', 3, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":38}', 4, TIMESTAMP(CURDATE() - INTERVAL 31 DAY, '16:26:00')),
  (39, 'citizens', 4, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":39}', 5, TIMESTAMP(CURDATE() - INTERVAL 29 DAY, '17:33:00')),
  (40, 'challans', 5, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":40}', 1, TIMESTAMP(CURDATE() - INTERVAL 27 DAY, '08:40:00')),
  (41, 'documents', 6, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":41}', 2, TIMESTAMP(CURDATE() - INTERVAL 25 DAY, '09:47:00')),
  (42, 'applications', 7, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":42}', 3, TIMESTAMP(CURDATE() - INTERVAL 51 DAY, '10:54:00')),
  (43, 'vehicles', 8, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":43}', 4, TIMESTAMP(CURDATE() - INTERVAL 49 DAY, '11:01:00')),
  (44, 'payments', 9, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":44}', 5, TIMESTAMP(CURDATE() - INTERVAL 47 DAY, '12:08:00')),
  (45, 'citizens', 1, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":45}', 1, TIMESTAMP(CURDATE() - INTERVAL 45 DAY, '13:15:00')),
  (46, 'challans', 2, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":46}', 2, TIMESTAMP(CURDATE() - INTERVAL 43 DAY, '14:22:00')),
  (47, 'documents', 3, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":47}', 3, TIMESTAMP(CURDATE() - INTERVAL 41 DAY, '15:29:00')),
  (48, 'applications', 4, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":48}', 4, TIMESTAMP(CURDATE() - INTERVAL 39 DAY, '16:36:00')),
  (49, 'vehicles', 5, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":49}', 5, TIMESTAMP(CURDATE() - INTERVAL 37 DAY, '17:43:00')),
  (50, 'payments', 6, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":50}', 1, TIMESTAMP(CURDATE() - INTERVAL 35 DAY, '08:50:00')),
  (51, 'citizens', 7, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":51}', 2, TIMESTAMP(CURDATE() - INTERVAL 33 DAY, '09:57:00')),
  (52, 'challans', 8, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":52}', 3, TIMESTAMP(CURDATE() - INTERVAL 31 DAY, '10:04:00')),
  (53, 'documents', 9, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":53}', 4, TIMESTAMP(CURDATE() - INTERVAL 29 DAY, '11:11:00')),
  (54, 'applications', 1, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":54}', 5, TIMESTAMP(CURDATE() - INTERVAL 27 DAY, '12:18:00')),
  (55, 'vehicles', 2, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":55}', 1, TIMESTAMP(CURDATE() - INTERVAL 25 DAY, '13:25:00')),
  (56, 'payments', 3, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":56}', 2, TIMESTAMP(CURDATE() - INTERVAL 51 DAY, '14:32:00')),
  (57, 'citizens', 4, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":57}', 3, TIMESTAMP(CURDATE() - INTERVAL 49 DAY, '15:39:00')),
  (58, 'challans', 5, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":58}', 4, TIMESTAMP(CURDATE() - INTERVAL 47 DAY, '16:46:00')),
  (59, 'documents', 6, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":59}', 5, TIMESTAMP(CURDATE() - INTERVAL 45 DAY, '17:53:00')),
  (60, 'applications', 7, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":60}', 1, TIMESTAMP(CURDATE() - INTERVAL 43 DAY, '08:00:00')),
  (61, 'vehicles', 8, 'UPDATE', '{"status":"PENDING"}', '{"status":"REVIEWED","batch":61}', 2, TIMESTAMP(CURDATE() - INTERVAL 41 DAY, '09:07:00'));

SET FOREIGN_KEY_CHECKS = 1;

-- End of seed script