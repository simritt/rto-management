# Script to generate comprehensive, realistic seed data for RTO Management System
import os
import re
from datetime import date

sql_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "23_comprehensive_demo_seed.sql")

# Common BCrypt hash used for every seed user, so one password works for all of them
# (handy for role-switching in demos). Replace the hash to change that shared password.
PASSWORD_HASH = "$2a$12$rBFxvAroCCzh13giiIqtw.oc1Y4A4WZU/smLrhw7r6c2nk.YxFpW6"

sql = []

# The literal dates below were authored around this day. Every standalone 'YYYY-MM-DD[ HH:MM:SS]'
# literal is rewritten as an offset from CURDATE(), so "expires in 4 days", "appointment tomorrow",
# "licence expired last spring" etc. stay true whenever the seed is loaded. Birth dates (person rows,
# recognisable by their 'AADH-' national id) are left absolute so ages do not drift.
ANCHOR = date(2026, 10, 22)
_DATE_LITERAL = re.compile(r"'(\d{4})-(\d{2})-(\d{2})(?: (\d{2}:\d{2}:\d{2}))?'")

def _relative(m):
    days = (date(int(m[1]), int(m[2]), int(m[3])) - ANCHOR).days
    base = "CURDATE()" if days == 0 else f"CURDATE() {'+' if days > 0 else '-'} INTERVAL {abs(days)} DAY"
    return f"TIMESTAMP({base}, '{m[4]}')" if m[4] else f"({base})"

def w(line=""):
    if "'AADH-" not in line:
        line = _DATE_LITERAL.sub(_relative, line)
    sql.append(line)

w("-- ============================================================")
w("-- RTO Management System - Comprehensive Demo Seed Data")
w("-- Populates every domain, status, and filter with realistic data")
w("-- ============================================================")
w("USE rto_management;")
w("SET FOREIGN_KEY_CHECKS = 0;")
w("")

# List of tables to clear for a pristine, consistent seed
tables_to_clean = [
    "audit_logs", "notifications", "appeals", "complaints", "refunds",
    "payment_attempts", "payments", "fee_structures", "challan_violations",
    "challan_status_history", "challans", "violations", "permit_status_history",
    "permits", "route_segments", "routes", "road_tax_records", "insurance_policies",
    "pollution_certificates", "fitness_certificates", "vehicle_inspections",
    "ownership_transfers", "vehicle_ownerships", "vehicles", "licence_status_history",
    "licence_class_assignments", "driving_licences", "driving_tests",
    "learner_licences", "driving_instructors", "driving_schools", "documents",
    "appointments", "appointment_slots", "application_status_history",
    "applications", "applicants", "employee_postings", "employees",
    "counters", "test_centres", "rto_offices", "addresses", "citizens",
    "user_roles", "role_permissions", "users", "persons",
    "payable_types", "permit_types", "violation_types", "service_types",
    "document_types", "licence_classes", "fuel_types", "vehicle_types",
    "vehicle_models", "vehicle_manufacturers", "designations", "departments",
    "regions", "roles", "permissions"
]

w("-- Clear existing data to maintain 100% relational integrity")
for t in tables_to_clean:
    w(f"DELETE FROM {t};")
w("")

# 1. Regions
w("-- 1. Regions (State -> Zone -> District/City)")
w("INSERT INTO regions (region_id, region_name, region_code, parent_region_id) VALUES")
w("  (1, 'Karnataka', 'KA', NULL),")
w("  (2, 'Bengaluru Urban', 'KA-BLR', 1),")
w("  (3, 'Bengaluru Rural', 'KA-BLRD', 1),")
w("  (4, 'Mysuru District', 'KA-MYS', 1),")
w("  (5, 'Gujarat', 'GJ', NULL),")
w("  (6, 'Ahmedabad District', 'GJ-AHM', 5),")
w("  (7, 'Surat District', 'GJ-SUR', 5);")
w("")

# 2. Departments & Designations
w("-- 2. Departments & Designations")
w("INSERT INTO departments (department_id, department_name) VALUES")
w("  (1, 'Licensing'),")
w("  (2, 'Vehicle Registration'),")
w("  (3, 'Enforcement & Challans'),")
w("  (4, 'Accounts & Revenue'),")
w("  (5, 'Administration & Public Grievances');")
w("")
w("INSERT INTO designations (designation_id, title, rank_level) VALUES")
w("  (1, 'Regional Transport Officer', 1),")
w("  (2, 'Motor Vehicle Inspector', 2),")
w("  (3, 'Assistant Motor Vehicle Inspector', 3),")
w("  (4, 'Superintendent', 4),")
w("  (5, 'Cashier & Revenue Clerk', 5);")
w("")

# 3. Reference Tables
w("-- 3. Reference Tables")
w("INSERT INTO vehicle_manufacturers (manufacturer_id, name) VALUES")
w("  (1, 'Maruti Suzuki'),")
w("  (2, 'Hyundai Motor'),")
w("  (3, 'Tata Motors'),")
w("  (4, 'Mahindra & Mahindra'),")
w("  (5, 'Honda Cars India'),")
w("  (6, 'Hero MotoCorp'),")
w("  (7, 'Bajaj Auto'),")
w("  (8, 'Toyota Kirloskar'),")
w("  (9, 'TVS Motor Company'),")
w("  (10, 'Ather Energy'),")
w("  (11, 'Ashok Leyland');")
w("")
w("INSERT INTO vehicle_models (model_id, manufacturer_id, model_name) VALUES")
w("  (1, 1, 'Swift'),")
w("  (2, 1, 'Baleno'),")
w("  (3, 1, 'Brezza'),")
w("  (4, 2, 'Creta'),")
w("  (5, 2, 'i20'),")
w("  (6, 3, 'Nexon'),")
w("  (7, 3, 'Tiago'),")
w("  (8, 4, 'Thar'),")
w("  (9, 4, 'Scorpio-N'),")
w("  (10, 4, 'Bolero Maxi Truck'),")
w("  (11, 5, 'City'),")
w("  (12, 5, 'Activa 6G'),")
w("  (13, 6, 'Splendor Plus'),")
w("  (14, 7, 'Pulsar 150'),")
w("  (15, 8, 'Innova Crysta'),")
w("  (16, 9, 'Jupiter 125'),")
w("  (17, 10, '450X'),")
w("  (18, 11, 'Dost Strong');")
w("")
w("INSERT INTO vehicle_types (vehicle_type_id, type_name, is_commercial) VALUES")
w("  (1, 'Two-Wheeler', FALSE),")
w("  (2, 'Light Motor Vehicle', FALSE),")
w("  (3, 'Heavy Motor Vehicle', TRUE),")
w("  (4, 'Transport Vehicle', TRUE);")
w("")
w("INSERT INTO fuel_types (fuel_type_id, fuel_name) VALUES")
w("  (1, 'Petrol'),")
w("  (2, 'Diesel'),")
w("  (3, 'CNG'),")
w("  (4, 'Electric'),")
w("  (5, 'Hybrid');")
w("")
w("INSERT INTO licence_classes (licence_class_id, class_code, description) VALUES")
w("  (1, 'MC50CC', 'Motorcycle up to 50cc'),")
w("  (2, 'MCWOG', 'Motorcycle without gear (Scooter)'),")
w("  (3, 'MCWG', 'Motorcycle with gear'),")
w("  (4, 'LMV', 'Light Motor Vehicle (Car/Jeep)'),")
w("  (5, 'HMV', 'Heavy Motor Vehicle (Truck/Bus)'),")
w("  (6, 'TRANS', 'Commercial Transport Vehicle');")
w("")
w("INSERT INTO document_types (document_type_id, type_name, is_mandatory_default, validity_period_days) VALUES")
w("  (1, 'Aadhaar Card', TRUE, NULL),")
w("  (2, 'Passport Photo', TRUE, NULL),")
w("  (3, 'Address Proof', TRUE, NULL),")
w("  (4, 'Medical Certificate (Form 1A)', FALSE, 365),")
w("  (5, 'Age Proof (Birth Certificate/10th Board)', TRUE, NULL),")
w("  (6, 'Vehicle Sales Certificate (Form 21)', TRUE, NULL),")
w("  (7, 'Pollution Under Control (PUC)', TRUE, 180);")
w("")
w("INSERT INTO service_types (service_type_id, service_name, service_code, base_fee, sla_days) VALUES")
w("  (1, 'New Driving Licence', 'NEW_DL', 500.00, 14),")
w("  (2, 'Driving Licence Renewal', 'RENEWAL_DL', 300.00, 7),")
w("  (3, 'Ownership Transfer', 'OWNERSHIP_TRANSFER', 750.00, 21),")
w("  (4, 'New Commercial Permit', 'NEW_PERMIT', 1000.00, 30),")
w("  (5, 'New Vehicle Registration', 'NEW_VEHICLE_REG', 800.00, 7),")
w("  (6, 'Learner Licence Issue', 'NEW_LL', 200.00, 3),")
w("  (7, 'Duplicate Driving Licence', 'DUP_DL', 400.00, 5);")
w("")
w("INSERT INTO violation_types (violation_type_id, description, base_fine_amount, is_cognizable) VALUES")
w("  (1, 'Over Speeding', 1000.00, FALSE),")
w("  (2, 'Driving Without Licence', 5000.00, TRUE),")
w("  (3, 'Signal Jumping', 500.00, FALSE),")
w("  (4, 'Drunk Driving', 10000.00, TRUE),")
w("  (5, 'Mobile Phone While Driving', 1500.00, FALSE),")
w("  (6, 'Riding Without Helmet / Seatbelt', 1000.00, FALSE),")
w("  (7, 'Dangerous Driving', 5000.00, TRUE),")
w("  (8, 'Driving Without Valid Insurance', 2000.00, FALSE);")
w("")
w("INSERT INTO permit_types (permit_type_id, type_name, validity_months) VALUES")
w("  (1, 'State Goods Carriage Permit', 60),")
w("  (2, 'National Goods Carriage Permit', 60),")
w("  (3, 'Contract Carriage / Taxi Permit', 36),")
w("  (4, 'Stage Carriage / Bus Permit', 60),")
w("  (5, 'Temporary Goods Permit', 1);")
w("")
w("INSERT INTO payable_types (payable_type_id, type_name) VALUES")
w("  (1, 'APPLICATION'),")
w("  (2, 'CHALLAN'),")
w("  (3, 'PERMIT'),")
w("  (4, 'ROAD_TAX');")
w("")

# 4. RTO Offices, Counters & Test Centres
w("-- 4. RTO Offices, Counters & Test Centres")
w("INSERT INTO rto_offices (office_id, region_id, office_name, office_code, address_line, is_active) VALUES")
w("  (1, 2, 'RTO Bengaluru Central', 'KA-01', 'Koramangala 3rd Block, Bengaluru 560034', TRUE),")
w("  (2, 2, 'RTO Bengaluru West', 'KA-02', 'Chord Road, Rajajinagar, Bengaluru 560010', TRUE),")
w("  (3, 2, 'RTO Bengaluru East', 'KA-03', '100ft Road, Indiranagar, Bengaluru 560038', TRUE),")
w("  (4, 4, 'RTO Mysuru Central', 'KA-09', 'Nazarbad Main Road, Mysuru 570010', TRUE),")
w("  (5, 6, 'RTO Ahmedabad Central', 'GJ-01', 'Subhash Bridge, Ashram Road, Ahmedabad 380027', TRUE),")
w("  (6, 7, 'RTO Surat City', 'GJ-05', 'Ring Road, Majura Gate, Surat 395002', TRUE);")
w("")
w("INSERT INTO counters (counter_id, office_id, counter_number, service_category) VALUES")
w("  (1, 1, 'C-01', 'Licensing'),")
w("  (2, 1, 'C-02', 'Vehicle Registration'),")
w("  (3, 1, 'C-03', 'Enforcement & Challans'),")
w("  (4, 1, 'C-04', 'Inquiry & Fee Deposit'),")
w("  (5, 2, 'C-01', 'Licensing'),")
w("  (6, 2, 'C-02', 'Vehicle Registration'),")
w("  (7, 2, 'C-03', 'Enforcement'),")
w("  (8, 3, 'C-01', 'Licensing'),")
w("  (9, 3, 'C-02', 'Vehicle Registration'),")
w("  (10, 4, 'C-01', 'Licensing & Registration'),")
w("  (11, 5, 'C-01', 'Licensing'),")
w("  (12, 5, 'C-02', 'Vehicle Registration');")
w("")
w("INSERT INTO test_centres (test_centre_id, office_id, centre_name) VALUES")
w("  (1, 1, 'Bengaluru Automated Driving Test Track (Koramangala)'),")
w("  (2, 2, 'Rajajinagar Heavy Vehicle Driving Track'),")
w("  (3, 4, 'Mysuru Outer Ring Road Testing Track'),")
w("  (4, 5, 'Ahmedabad Central Automated Test Facility');")
w("")

# 5. Persons & Addresses
w("-- 5. Identity: Persons & Addresses")
persons = [
    # id, first, last, dob, gender, national_id, phone, email
    (1, 'Super', 'Administrator', '1985-01-01', 'MALE', 'AADH-000000000', '9880000001', 'admin@rto.gov.in'),
    (2, 'Ramesh', 'Kumar', '1976-04-12', 'MALE', 'AADH-100000001', '9880000002', 'ramesh.kumar@rto.gov.in'),
    (3, 'Suresh', 'Bhat', '1980-08-23', 'MALE', 'AADH-100000002', '9880000003', 'suresh.bhat@rto.gov.in'),
    (4, 'Deepa', 'Shetty', '1984-11-15', 'FEMALE', 'AADH-100000003', '9880000004', 'deepa.shetty@rto.gov.in'),
    (5, 'Anand', 'Deshmukh', '1978-02-18', 'MALE', 'AADH-100000004', '9880000005', 'anand.d@rto.gov.in'),
    (6, 'Meenakshi', 'Sundaram', '1982-06-30', 'FEMALE', 'AADH-100000005', '9880000006', 'meenakshi.s@rto.gov.in'),
    (7, 'Vikram', 'Rathore', '1986-09-05', 'MALE', 'AADH-100000006', '9880000007', 'vikram.r@rto.gov.in'),
    (8, 'Kavita', 'Patil', '1990-12-01', 'FEMALE', 'AADH-100000007', '9880000008', 'kavita.p@rto.gov.in'),
    (9, 'Mohan', 'Lal', '1974-05-19', 'MALE', 'AADH-100000008', '9880000009', 'mohan.lal@rto.gov.in'),
    # Citizens:
    (10, 'Aarav', 'Mehta', '1992-03-14', 'MALE', 'AADH-200000010', '9845011111', 'aarav.mehta@example.com'),
    (11, 'Priya', 'Sharma', '1995-07-22', 'FEMALE', 'AADH-200000011', '9845022222', 'priya.sharma@example.com'),
    (12, 'Rohan', 'Patel', '1988-11-05', 'MALE', 'AADH-200000012', '9845033333', 'rohan.patel@example.com'),
    (13, 'Sunita', 'Desai', '1991-01-30', 'FEMALE', 'AADH-200000013', '9845044444', 'sunita.desai@example.com'),
    (14, 'Kiran', 'Joshi', '1996-06-18', 'MALE', 'AADH-200000014', '9845055555', 'kiran.joshi@example.com'),
    (15, 'Pooja', 'Singh', '1994-09-09', 'FEMALE', 'AADH-200000015', '9845066666', 'pooja.singh@example.com'),
    (16, 'Amit', 'Verma', '1983-12-25', 'MALE', 'AADH-200000016', '9845077777', 'amit.verma@example.com'),
    (17, 'Neha', 'Gupta', '1998-04-02', 'FEMALE', 'AADH-200000017', '9845088888', 'neha.gupta@example.com'),
    (18, 'Vijay', 'Shah', '1979-08-15', 'MALE', 'AADH-200000018', '9845099999', 'vijay.shah@example.com'),
    (19, 'Meera', 'Nair', '2000-02-28', 'FEMALE', 'AADH-200000019', '9845010101', 'meera.nair@example.com'),
    (20, 'Deepak', 'Kumar', '1993-05-12', 'MALE', 'AADH-200000020', '9845020202', 'deepak.k@example.com'),
    (21, 'Ananya', 'Roy', '1997-10-14', 'FEMALE', 'AADH-200000021', '9845030303', 'ananya.roy@example.com'),
    (22, 'Rajesh', 'Iyer', '1981-04-20', 'MALE', 'AADH-200000022', '9845040404', 'rajesh.iyer@example.com'),
    (23, 'Sneha', 'Patil', '1999-08-08', 'FEMALE', 'AADH-200000023', '9845050505', 'sneha.patil@example.com'),
    (24, 'Mohammed', 'Farhan', '1990-12-03', 'MALE', 'AADH-200000024', '9845060606', 'm.farhan@example.com'),
    (25, 'Kavita', 'Reddy', '1987-03-29', 'FEMALE', 'AADH-200000025', '9845070707', 'kavita.reddy@example.com'),
    (26, 'Girish', 'Hegde', '1986-07-17', 'MALE', 'AADH-200000026', '9845080808', 'girish.hegde@example.com'),
    # Blacklisted Citizens:
    (27, 'Harish', 'Chandra', '1975-01-11', 'MALE', 'AADH-200000027', '9845090909', 'harish.c@example.com'),
    (28, 'Manish', 'Tiwari', '1989-10-25', 'MALE', 'AADH-200000028', '9845012121', 'manish.t@example.com'),
    (29, 'Praveen', 'Sood', '1992-06-04', 'MALE', 'AADH-200000029', '9845023232', 'praveen.sood@example.com'),
    (30, 'Tanvi', 'Kulkarni', '1994-02-14', 'FEMALE', 'AADH-200000030', '9845034343', 'tanvi.k@example.com'),
    # Edge cases: gender OTHER, accents / apostrophe / hyphen in names (SQL + HTML escaping),
    # and two citizens who have no applications, vehicles or licences (empty states).
    (31, 'Alex', 'Morgan', '1996-05-05', 'OTHER', 'AADH-200000031', '9845045454', 'alex.morgan@example.com'),
    (32, 'Siobhán', "O''Neill-Fernández", '1985-11-30', 'FEMALE', 'AADH-200000032', '9845056565', 'siobhan.oneill@example.com'),
]

w("INSERT INTO persons (person_id, first_name, last_name, date_of_birth, gender, national_id_number, phone_primary, email, created_at, updated_at) VALUES")
for i, p in enumerate(persons):
    comma = "," if i < len(persons) - 1 else ";"
    w(f"  ({p[0]}, '{p[1]}', '{p[2]}', '{p[3]}', '{p[4]}', '{p[5]}', '{p[6]}', '{p[7]}', NOW(), NOW()){comma}")
w("")

w("INSERT INTO addresses (address_id, person_id, line1, line2, city, state, pincode, address_type, is_current, valid_from) VALUES")
for i, p in enumerate(persons):
    comma = "," if i < len(persons) - 1 else ";"
    city = 'Bengaluru' if p[0] <= 24 else ('Mysuru' if p[0] <= 27 else 'Ahmedabad')
    state = 'Gujarat' if city == 'Ahmedabad' else 'Karnataka'
    pin = '380027' if city == 'Ahmedabad' else ('570010' if city == 'Mysuru' else f'5600{p[0]:02d}')
    w(f"  ({p[0]}, {p[0]}, '#{p[0]*12}, 5th Cross, Main Road', 'Sector {p[0]%5+1}', '{city}', '{state}', '{pin}', 'CURRENT', TRUE, '2020-01-01'){comma}")
w("")

# Extra addresses: PERMANENT and OFFICE types, a person with two different current addresses,
# and a past (closed) CURRENT address so the address-history view shows both states.
w("INSERT INTO addresses (address_id, person_id, line1, line2, city, state, pincode, address_type, is_current, valid_from, valid_to) VALUES")
w("  (33, 10, '12, Palace Road', NULL, 'Mysuru', 'Karnataka', '570001', 'PERMANENT', TRUE, '2015-06-01', NULL),")
w("  (34, 11, '77, MG Road, 3rd Floor', 'Embassy Tower', 'Bengaluru', 'Karnataka', '560001', 'OFFICE', TRUE, '2023-04-01', NULL),")
w("  (35, 12, '4, Old Pune Road', NULL, 'Pune', 'Maharashtra', '411001', 'PERMANENT', TRUE, '2012-02-01', NULL),")
w("  (36, 10, '9, 1st Main, Jayanagar', NULL, 'Bengaluru', 'Karnataka', '560011', 'CURRENT', FALSE, '2016-01-01', '2019-12-31'),")
w("  (37, 2, 'RTO Office, Koramangala', 'Near Sony Signal', 'Bengaluru', 'Karnataka', '560034', 'OFFICE', TRUE, '2019-07-01', NULL);")
w("")

# 6. Citizens & Applicants
w("-- 6. Citizens & Applicants (including blacklisted citizens)")
# Citizens 10 to 30 mapped to persons 10 to 30
w("INSERT INTO citizens (citizen_id, person_id, citizen_code, blacklisted, blacklist_reason, registered_at) VALUES")
citizen_rows = []
for cid, pid in enumerate(range(10, 33), start=1):
    code = f"CIT-2026-{cid:05d}"
    if cid == 18: # person 27
        citizen_rows.append((cid, pid, code, "TRUE", "'Suspended: 3 DUI offenses in 6 months; Section 185 Motor Vehicles Act'", "'2022-03-10 10:00:00'"))
    elif cid == 19: # person 28
        citizen_rows.append((cid, pid, code, "TRUE", "'Debarred: Forged educational certificate submitted for transport licence'", "'2021-08-15 11:30:00'"))
    elif cid == 20: # person 29
        citizen_rows.append((cid, pid, code, "TRUE", "'Habitual red-light runner with 8 unpaid speed violations'", "'2023-01-20 14:15:00'"))
    elif cid == 21: # person 30
        citizen_rows.append((cid, pid, code, "TRUE", "'Involved in reckless hit-and-run under FIR 104/2026'", "'2024-05-18 16:45:00'"))
    else:
        citizen_rows.append((cid, pid, code, "FALSE", "NULL", f"'2022-{(cid%12)+1:02d}-{(cid%25)+1:02d} 10:00:00'"))

for i, c in enumerate(citizen_rows):
    comma = "," if i < len(citizen_rows) - 1 else ";"
    w(f"  ({c[0]}, {c[1]}, '{c[2]}', {c[3]}, {c[4]}, {c[5]}){comma}")
w("")

w("INSERT INTO applicants (applicant_id, citizen_id, preferred_office_id) VALUES")
for i, c in enumerate(citizen_rows):
    comma = "," if i < len(citizen_rows) - 1 else ";"
    pref_off = (c[0] % 5) + 1
    w(f"  ({c[0]}, {c[0]}, {pref_off}){comma}")
w("")

# 7. Employees & Postings
w("-- 7. Employees & Postings")
employees = [
    # emp_id, person_id, code, desig_id, office_id, dept_id
    (1, 2, 'EMP-KA-001', 1, 1, 5), # RTO Koramangala
    (2, 3, 'EMP-KA-002', 2, 1, 1), # MVI Licensing
    (3, 4, 'EMP-KA-003', 2, 1, 2), # MVI Vehicle Reg
    (4, 5, 'EMP-KA-004', 3, 2, 3), # AMVI Rajajinagar Enforcement
    (5, 6, 'EMP-KA-005', 2, 2, 1), # MVI Rajajinagar Licensing
    (6, 7, 'EMP-KA-006', 3, 3, 3), # AMVI Indiranagar Enforcement
    (7, 8, 'EMP-KA-007', 4, 1, 4), # Superintendent Accounts
    (8, 9, 'EMP-KA-008', 1, 5, 5), # RTO Ahmedabad Central
]
w("INSERT INTO employees (employee_id, person_id, employee_code, designation_id, date_joined, is_active) VALUES")
for i, e in enumerate(employees):
    comma = "," if i < len(employees) - 1 else ";"
    w(f"  ({e[0]}, {e[1]}, '{e[2]}', {e[3]}, '2015-05-01', TRUE){comma}")
w("")

w("INSERT INTO employee_postings (posting_id, employee_id, office_id, department_id, posted_from, posted_to) VALUES")
for i, e in enumerate(employees):
    comma = "," if i < len(employees) - 1 else ";"
    w(f"  ({e[0]}, {e[0]}, {e[4]}, {e[5]}, '2020-01-01', NULL){comma}")
w("")

# 8. RBAC: Roles, Permissions, Users, User Roles
w("-- 8. RBAC: Roles, Permissions, Users, and Roles")
w("INSERT INTO roles (role_id, role_name) VALUES")
w("  (1, 'CITIZEN'),")
w("  (2, 'RTO_OFFICER'),")
w("  (3, 'COUNTER_CLERK'),")
w("  (4, 'INSPECTOR'),")
w("  (5, 'ENFORCEMENT_OFFICER'),")
w("  (6, 'ACCOUNTANT'),")
w("  (7, 'ADMIN');")
w("")

PERMS_ALL = [
    "rbac.manage", "user.view", "user.manage", "audit.view", "dashboard.view", "reference.manage",
    "citizen.view", "citizen.create", "citizen.update", "citizen.view_sensitive",
    "office.manage", "employee.view", "employee.manage",
    "application.view", "application.view_own", "application.create", "application.create_own",
    "application.update", "application.assign", "application.verify", "application.approve", "application.reject", "application.cancel",
    "document.upload", "document.view", "document.verify",
    "appointment.view", "appointment.manage", "appointment.book",
    "licence.view", "licence.issue", "licence.suspend", "licence.revoke", "licence.test", "licence.manage_schools",
    "vehicle.view", "vehicle.create", "vehicle.update", "vehicle.transfer", "vehicle.transfer_approve",
    "compliance.view", "compliance.manage",
    "permit.view", "permit.create", "permit.manage", "route.manage",
    "violation.view", "violation.create",
    "challan.view", "challan.create", "challan.manage",
    "payment.view", "payment.create", "payment.refund",
    "complaint.create", "complaint.view_own", "complaint.manage",
    "appeal.create", "appeal.view_own", "appeal.review",
    "notification.view", "notification.view_own"
]
perm_id_map = {p: i for i, p in enumerate(PERMS_ALL, start=1)}

w("INSERT INTO permissions (permission_id, permission_key) VALUES")
for i, p in enumerate(PERMS_ALL, start=1):
    comma = "," if i < len(PERMS_ALL) else ";"
    w(f"  ({i}, '{p}'){comma}")
w("")

ROLE_GRANTS = {
    7: PERMS_ALL, # ADMIN gets ALL
    2: ["appeal.review", "application.approve", "application.assign", "application.cancel", "application.create", "application.reject", "application.update", "application.verify", "application.view", "appointment.book", "appointment.manage", "appointment.view", "challan.view", "citizen.create", "citizen.update", "citizen.view", "citizen.view_sensitive", "complaint.manage", "compliance.manage", "compliance.view", "dashboard.view", "document.upload", "document.verify", "document.view", "employee.view", "licence.issue", "licence.revoke", "licence.suspend", "licence.test", "licence.manage_schools", "licence.view", "notification.view", "payment.view", "permit.create", "permit.manage", "permit.view", "route.manage", "vehicle.create", "vehicle.transfer", "vehicle.transfer_approve", "vehicle.update", "vehicle.view", "violation.view"],
    3: ["application.create", "application.update", "application.view", "appointment.book", "appointment.manage", "appointment.view", "challan.view", "citizen.create", "citizen.update", "citizen.view", "complaint.create", "compliance.view", "dashboard.view", "document.upload", "document.view", "licence.view", "payment.create", "payment.view", "permit.view", "vehicle.transfer", "vehicle.view"],
    4: ["citizen.view", "compliance.manage", "compliance.view", "licence.test", "licence.view", "vehicle.view", "violation.create", "violation.view"],
    5: ["challan.create", "challan.manage", "challan.view", "citizen.view", "licence.view", "vehicle.view", "violation.create", "violation.view"],
    6: ["application.view", "audit.view", "challan.view", "dashboard.view", "payment.create", "payment.refund", "payment.view"],
    1: ["appeal.create", "appeal.view_own", "application.create_own", "application.view_own", "appointment.book", "complaint.create", "complaint.view_own", "document.upload", "notification.view_own"]
}

role_perm_entries = []
for role_id, perms in ROLE_GRANTS.items():
    for p in perms:
        if p in perm_id_map:
            role_perm_entries.append((role_id, perm_id_map[p]))

w("INSERT INTO role_permissions (role_id, permission_id) VALUES")
for i, rp in enumerate(role_perm_entries):
    comma = "," if i < len(role_perm_entries) - 1 else ";"
    w(f"  ({rp[0]}, {rp[1]}){comma}")
w("")

# Users:
w("INSERT INTO users (user_id, person_id, username, password_hash, is_active, last_login_at) VALUES")
w(f"  (1, 1, 'admin', '{PASSWORD_HASH}', TRUE, NOW()),")
w(f"  (2, 2, 'officer.kumar', '{PASSWORD_HASH}', TRUE, NOW()),")
w(f"  (3, 3, 'inspector.bhat', '{PASSWORD_HASH}', TRUE, NOW()),")
w(f"  (4, 5, 'inspector.anand', '{PASSWORD_HASH}', TRUE, NOW()),")
w(f"  (5, 7, 'enforcer.vikram', '{PASSWORD_HASH}', TRUE, NOW()),")
w(f"  (6, 10, 'aarav.mehta', '{PASSWORD_HASH}', TRUE, NOW()),")
w(f"  (7, 11, 'priya.sharma', '{PASSWORD_HASH}', TRUE, NOW()),")
w(f"  (8, 12, 'rohan.patel', '{PASSWORD_HASH}', TRUE, NOW()),")
w(f"  (9, 13, 'sunita.desai', '{PASSWORD_HASH}', TRUE, NOW()),")
w(f"  (10, 14, 'kiran.joshi', '{PASSWORD_HASH}', TRUE, NOW());")
w("")

w("INSERT INTO user_roles (user_id, role_id) VALUES")
w("  (1, 7), (1, 2), (1, 4),") # admin = ADMIN + RTO_OFFICER + INSPECTOR
w("  (2, 2),")                # officer.kumar
w("  (3, 4),")                # inspector.bhat
w("  (4, 4),")                # inspector.anand
w("  (5, 5),")                # enforcer.vikram
w("  (6, 1), (7, 1), (8, 1), (9, 1), (10, 1);") # citizens
w("")

# 9. Applications (COVERING ALL STATUSES!)
w("-- 9. Applications (Full lifecycle representation across every valid status)")
apps = [
    # id, app_num, applicant_id, service_type_id, office_id, assigned_officer, status, submitted_at, completed_at, remarks
    (1, 'APP-2026-00001', 1, 1, 1, 2, 'SUBMITTED', '2026-10-21 09:30:00', 'NULL', 'NULL'),
    (2, 'APP-2026-00002', 2, 2, 1, 2, 'DOCS_PENDING', '2026-10-20 10:15:00', 'NULL', "'Medical certificate Form 1A missing doctor registration stamp'"),
    (3, 'APP-2026-00003', 3, 1, 1, 3, 'UNDER_VERIFICATION', '2026-10-18 11:00:00', 'NULL', "'Aadhaar biometric validation in progress'"),
    (4, 'APP-2026-00004', 4, 6, 2, 4, 'APPOINTMENT_SCHEDULED', '2026-10-16 14:00:00', 'NULL', "'Automated track slot booked for 2026-10-24 10:00'"),
    (5, 'APP-2026-00005', 5, 5, 1, 2, 'AWAITING_PAYMENT', '2026-10-15 15:30:00', 'NULL', "'Registration assessment fee generated; pending citizen payment'"),
    (6, 'APP-2026-00006', 6, 3, 2, 5, 'APPROVED', '2026-10-14 09:00:00', 'NULL', "'Transfer of ownership approved by MVI; smart card printing'"),
    (7, 'APP-2026-00007', 7, 1, 3, 6, 'REJECTED', '2026-10-10 11:20:00', 'NULL', "'Failed driving competency test twice on 8-track'"),
    (8, 'APP-2026-00008', 8, 4, 1, 2, 'COMPLETED', '2026-09-28 10:00:00', "'2026-10-05 16:30:00'", "'Commercial route permit issued and laminated'"),
    (9, 'APP-2026-00009', 9, 7, 2, 5, 'CANCELLED', '2026-10-01 12:00:00', 'NULL', "'Applicant requested cancellation due to out-of-state transfer'"),
    (10, 'APP-2026-00010', 10, 6, 1, 2, 'COMPLETED', '2026-10-02 09:15:00', "'2026-10-04 14:00:00'", "'Learner Licence issued digitally with OTP signing'"),
    (11, 'APP-2026-00011', 11, 1, 1, 2, 'COMPLETED', '2026-09-15 10:30:00', "'2026-09-25 15:45:00'", "'Permanent Driving Licence printed and dispatched'"),
    (12, 'APP-2026-00012', 12, 5, 2, 3, 'COMPLETED', '2026-09-20 11:45:00', "'2026-09-28 17:00:00'", "'RC Book smart card dispatched via India Post SpeedPost'"),
    (13, 'APP-2026-00013', 13, 2, 3, 6, 'SUBMITTED', '2026-10-22 08:45:00', 'NULL', "'Renewal application submitted online'"),
    (14, 'APP-2026-00014', 14, 6, 1, 2, 'DOCS_PENDING', '2026-10-19 16:10:00', 'NULL', "'Uploaded address proof scan is blurred and illegible'"),
    (15, 'APP-2026-00015', 15, 3, 1, 3, 'UNDER_VERIFICATION', '2026-10-17 13:20:00', 'NULL', "'Form 29 and 30 verification in progress'"),
    (16, 'APP-2026-00016', 16, 4, 2, 5, 'APPOINTMENT_SCHEDULED', '2026-10-16 11:00:00', 'NULL', "'Physical vehicle inspection booked for 2026-10-25 11:00'"),
    (17, 'APP-2026-00017', 1, 5, 1, 2, 'AWAITING_PAYMENT', '2026-10-17 14:40:00', 'NULL', "'Road tax and smart card fee pending online payment'"),
    (18, 'APP-2026-00018', 2, 1, 2, 5, 'APPROVED', '2026-10-12 10:00:00', 'NULL', "'Test passed with 95% score; final DL approved'"),
    (19, 'APP-2026-00019', 3, 4, 3, 6, 'REJECTED', '2026-09-25 15:00:00', 'NULL', "'Vehicle does not conform to commercial passenger safety norms'"),
    (20, 'APP-2026-00020', 4, 2, 1, 2, 'CANCELLED', '2026-10-05 09:30:00', 'NULL', "'Duplicate renewal request flagged by system'")
]

w("INSERT INTO applications (application_id, application_number, applicant_id, service_type_id, office_id, assigned_officer_id, current_status, submitted_at, completed_at, remarks) VALUES")
for i, a in enumerate(apps):
    comma = "," if i < len(apps) - 1 else ";"
    w(f"  ({a[0]}, '{a[1]}', {a[2]}, {a[3]}, {a[4]}, {a[5]}, '{a[6]}', '{a[7]}', {a[8]}, {a[9]}){comma}")
w("")

# Application Status Histories
w("INSERT INTO application_status_history (history_id, application_id, previous_status, new_status, changed_by_user_id, changed_at, reason) VALUES")
ash = [
    (1, 1, 'NULL', "'SUBMITTED'", 1, "'2026-10-21 09:30:00'", "'Application submitted online by citizen'"),
    (2, 2, 'NULL', "'SUBMITTED'", 1, "'2026-10-20 10:15:00'", "'Submitted'"),
    (3, 2, "'SUBMITTED'", "'DOCS_PENDING'", 2, "'2026-10-20 11:30:00'", "'Doctor stamp missing on Form 1A'"),
    (4, 3, 'NULL', "'SUBMITTED'", 1, "'2026-10-18 11:00:00'", "'Submitted'"),
    (5, 3, "'SUBMITTED'", "'UNDER_VERIFICATION'", 3, "'2026-10-19 09:30:00'", "'Assigned for Aadhaar identity cross-check'"),
    (6, 4, 'NULL', "'SUBMITTED'", 1, "'2026-10-16 14:00:00'", "'Submitted'"),
    (7, 4, "'SUBMITTED'", "'UNDER_VERIFICATION'", 2, "'2026-10-17 10:00:00'", "'Verified'"),
    (8, 4, "'UNDER_VERIFICATION'", "'APPOINTMENT_SCHEDULED'", 2, "'2026-10-17 14:00:00'", "'Slot booked'"),
    (9, 5, 'NULL', "'SUBMITTED'", 1, "'2026-10-15 15:30:00'", "'Submitted'"),
    (10, 5, "'SUBMITTED'", "'UNDER_VERIFICATION'", 2, "'2026-10-16 10:00:00'", "'Verified invoice'"),
    (11, 5, "'UNDER_VERIFICATION'", "'AWAITING_PAYMENT'", 2, "'2026-10-16 11:30:00'", "'Challan generated'"),
    (12, 6, 'NULL', "'SUBMITTED'", 1, "'2026-10-14 09:00:00'", "'Submitted'"),
    (13, 6, "'SUBMITTED'", "'APPROVED'", 3, "'2026-10-15 16:00:00'", "'Ownership transfer documents validated'"),
    (14, 7, 'NULL', "'SUBMITTED'", 1, "'2026-10-10 11:20:00'", "'Submitted'"),
    (15, 7, "'SUBMITTED'", "'REJECTED'", 2, "'2026-10-12 14:30:00'", "'Driving test failed twice'"),
    (16, 8, 'NULL', "'SUBMITTED'", 1, "'2026-09-28 10:00:00'", "'Submitted'"),
    (17, 8, "'SUBMITTED'", "'UNDER_VERIFICATION'", 2, "'2026-09-29 11:00:00'", "'Route vetted'"),
    (18, 8, "'UNDER_VERIFICATION'", "'APPROVED'", 2, "'2026-10-02 14:00:00'", "'Permit vetted'"),
    (19, 8, "'APPROVED'", "'COMPLETED'", 2, "'2026-10-05 16:30:00'", "'Permit issued'"),
    (20, 9, 'NULL', "'SUBMITTED'", 1, "'2026-10-01 12:00:00'", "'Submitted'"),
    (21, 9, "'SUBMITTED'", "'CANCELLED'", 1, "'2026-10-02 10:00:00'", "'Citizen requested cancellation'")
]
for i, h in enumerate(ash):
    comma = "," if i < len(ash) - 1 else ";"
    w(f"  ({h[0]}, {h[1]}, {h[2]}, {h[3]}, {h[4]}, {h[5]}, {h[6]}){comma}")
w("")

# 10. Documents
w("-- 10. Documents Attached to Applications")
docs = [
    (1, 1, 1, 'storage/app1/aadhaar.pdf', 'VERIFIED', 2, 'NULL', '2026-10-21 09:35:00', "'2026-10-21 11:00:00'"),
    (2, 1, 2, 'storage/app1/photo.jpg', 'VERIFIED', 2, 'NULL', '2026-10-21 09:36:00', "'2026-10-21 11:05:00'"),
    (3, 2, 1, 'storage/app2/aadhaar.pdf', 'VERIFIED', 2, 'NULL', '2026-10-20 10:20:00', "'2026-10-20 11:00:00'"),
    (4, 2, 4, 'storage/app2/medical_form1a.pdf', 'REJECTED', 2, "'Doctor stamp and reg number missing'", '2026-10-20 10:22:00', "'2026-10-20 11:30:00'"),
    (5, 3, 1, 'storage/app3/aadhaar.pdf', 'PENDING', 'NULL', 'NULL', '2026-10-18 11:05:00', 'NULL'),
    (6, 4, 1, 'storage/app4/aadhaar.pdf', 'VERIFIED', 3, 'NULL', '2026-10-16 14:05:00', "'2026-10-17 10:00:00'"),
    (7, 4, 5, 'storage/app4/birth_cert.pdf', 'VERIFIED', 3, 'NULL', '2026-10-16 14:06:00', "'2026-10-17 10:05:00'"),
    (8, 5, 6, 'storage/app5/form21_sale.pdf', 'VERIFIED', 2, 'NULL', '2026-10-15 15:35:00', "'2026-10-16 10:00:00'"),
    (9, 6, 3, 'storage/app6/electricity_bill.pdf', 'VERIFIED', 5, 'NULL', '2026-10-14 09:10:00', "'2026-10-15 11:00:00'"),
    (10, 7, 1, 'storage/app7/aadhaar.pdf', 'VERIFIED', 6, 'NULL', '2026-10-10 11:25:00', "'2026-10-11 09:30:00'"),
    (11, 8, 1, 'storage/app8/aadhaar.pdf', 'VERIFIED', 2, 'NULL', '2026-09-28 10:05:00', "'2026-09-29 10:00:00'"),
    (12, 14, 3, 'storage/app14/water_bill.pdf', 'REJECTED', 2, "'Illegible low resolution image'", '2026-10-19 16:15:00', "'2026-10-20 09:00:00'"),
    # EXPIRED document: was verified, then its validity period lapsed
    (13, 7, 3, 'storage/app7/old_water_bill.pdf', 'EXPIRED', 6, 'NULL', '2025-04-02 09:00:00', "'2025-04-03 10:00:00'")
]
w("INSERT INTO documents (document_id, application_id, document_type_id, file_path, verification_status, verified_by_employee_id, rejection_reason, uploaded_at, verified_at) VALUES")
for i, d in enumerate(docs):
    comma = "," if i < len(docs) - 1 else ";"
    emp = d[5] if d[5] != 'NULL' else 'NULL'
    w(f"  ({d[0]}, {d[1]}, {d[2]}, '{d[3]}', '{d[4]}', {emp}, {d[6]}, '{d[7]}', {d[8]}){comma}")
w("")

# 11. Appointment Slots & Appointments
w("-- 11. Appointment Slots & Booked Appointments")
slots = [
    # id, office, counter, date, start, end, cap, booked
    (1, 1, 1, '2026-10-22', '09:00:00', '09:30:00', 10, 5),
    (2, 1, 1, '2026-10-22', '09:30:00', '10:00:00', 10, 6),
    (3, 1, 1, '2026-10-23', '09:00:00', '09:30:00', 10, 4),
    (4, 1, 1, '2026-10-23', '09:30:00', '10:00:00', 10, 2),
    (5, 1, 2, '2026-10-24', '10:00:00', '10:30:00', 8, 3),
    (6, 2, 5, '2026-10-24', '10:00:00', '10:30:00', 8, 4),
    (7, 2, 5, '2026-10-25', '11:00:00', '11:30:00', 8, 1),
    (8, 3, 8, '2026-10-26', '14:00:00', '14:30:00', 6, 2),
    (9, 4, 10, '2026-10-28', '10:00:00', '10:30:00', 5, 1)
]
w("INSERT INTO appointment_slots (slot_id, office_id, counter_id, slot_date, start_time, end_time, capacity, booked_count) VALUES")
for i, s in enumerate(slots):
    comma = "," if i < len(slots) - 1 else ";"
    w(f"  ({s[0]}, {s[1]}, {s[2]}, '{s[3]}', '{s[4]}', '{s[5]}', {s[6]}, {s[7]}){comma}")
w("")

w("INSERT INTO appointments (appointment_id, application_id, slot_id, token_number, status, booked_at) VALUES")
appointments = [
    (1, 4, 6, 'TKN-KA02-001', 'BOOKED', '2026-10-17 14:00:00'),
    (2, 16, 7, 'TKN-KA02-002', 'BOOKED', '2026-10-18 10:30:00'),
    (3, 11, 1, 'TKN-KA01-089', 'COMPLETED', '2026-09-18 09:15:00'),
    (4, 10, 2, 'TKN-KA01-090', 'COMPLETED', '2026-09-25 10:00:00'),
    (5, 7, 3, 'TKN-KA01-045', 'NO_SHOW', '2026-10-11 09:30:00'),
    (6, 9, 4, 'TKN-KA01-046', 'CANCELLED', '2026-10-01 13:00:00'),
    (7, 15, 5, 'TKN-KA01-055', 'RESCHEDULED', '2026-10-17 14:00:00')
]
for i, appt in enumerate(appointments):
    comma = "," if i < len(appointments) - 1 else ";"
    w(f"  ({appt[0]}, {appt[1]}, {appt[2]}, '{appt[3]}', '{appt[4]}', '{appt[5]}'){comma}")
w("")

# 12. Driving Schools & Instructors
w("-- 12. Driving Schools & Certified Instructors")
w("INSERT INTO driving_schools (school_id, school_name, licence_number, office_id) VALUES")
w("  (1, 'Maruti Suzuki Driving School - Koramangala', 'KA01-DS-2021-001', 1),")
w("  (2, 'Apex Motor Driving Academy - Rajajinagar', 'KA02-DS-2022-014', 2),")
w("  (3, 'Royal Star Motor Driving Institute - Indiranagar', 'KA03-DS-2023-088', 3),")
w("  (4, 'Ahmedabad Highway Driving Academy', 'GJ01-DS-2020-005', 5);")
w("")

w("INSERT INTO driving_instructors (instructor_id, person_id, school_id, licence_class_id) VALUES")
w("  (1, 22, 1, 4),") # Rajesh Iyer at Maruti (LMV)
w("  (2, 24, 1, 3),") # Mohammed Farhan at Maruti (MCWG)
w("  (3, 26, 2, 5),") # Girish Hegde at Apex (HMV)
w("  (4, 20, 3, 4);") # Deepak Kumar at Royal Star (LMV)
w("")

# 13. Learner Licences & Driving Tests
w("-- 13. Learner Licences & Driving Tests")
ll_list = [
    # id, lic_num, citizen_id, app_id, office_id, issue_date, expiry_date, status
    (1, 'LL-KA01-2026-000101', 1, 10, 1, '2026-09-01', '2027-03-01', 'ACTIVE'),
    (2, 'LL-KA01-2026-000102', 2, 4, 1, '2026-08-15', '2027-02-15', 'ACTIVE'),
    (3, 'LL-KA02-2026-000201', 3, 6, 2, '2026-05-10', '2026-11-10', 'CONVERTED'),
    (4, 'LL-KA02-2026-000202', 4, 11, 2, '2026-06-01', '2026-12-01', 'CONVERTED'),
    (5, 'LL-KA01-2025-000055', 5, 12, 1, '2025-09-01', '2026-03-01', 'EXPIRED'),
    (6, 'LL-KA03-2025-000088', 6, 13, 3, '2025-10-15', '2026-04-15', 'EXPIRED'),
    (7, 'LL-KA01-2026-000199', 7, 7, 1, '2026-07-20', '2027-01-20', 'CANCELLED')
]
w("INSERT INTO learner_licences (learner_licence_id, licence_number, citizen_id, application_id, office_id, issue_date, expiry_date, status) VALUES")
for i, l in enumerate(ll_list):
    comma = "," if i < len(ll_list) - 1 else ";"
    w(f"  ({l[0]}, '{l[1]}', {l[2]}, {l[3]}, {l[4]}, '{l[5]}', '{l[6]}', '{l[7]}'){comma}")
w("")

driving_tests = [
    # id, app_id, citizen_id, test_centre, examiner, scheduled_at, result, remarks
    (1, 11, 11, 1, 2, '2026-09-22 10:00:00', 'PASS', "'Flawless parallel parking and S-track navigation'"),
    (2, 10, 10, 1, 2, '2026-10-03 11:30:00', 'PASS', "'Theory traffic quiz passed 18/20'"),
    (3, 7, 7, 1, 3, '2026-10-11 10:00:00', 'FAIL', "'Hit bollard on reverse parallel bay and kerb touch'"),
    (4, 4, 4, 1, 2, '2026-10-24 10:00:00', 'PENDING', "'Scheduled on Automated Track C'"),
    (5, 18, 2, 2, 4, '2026-10-28 14:00:00', 'PENDING', "'Scheduled at Rajajinagar HMV ground'"),
    (6, 19, 3, 3, 6, '2026-09-24 11:00:00', 'FAIL', "'Failed emergency braking demonstration'"),
    (7, 8, 3, 2, 4, '2026-10-15 09:30:00', 'ABSENT', "'Candidate did not report to the test centre'")
]
w("INSERT INTO driving_tests (test_id, application_id, citizen_id, test_centre_id, examiner_employee_id, scheduled_at, result, remarks) VALUES")
for i, t in enumerate(driving_tests):
    comma = "," if i < len(driving_tests) - 1 else ";"
    w(f"  ({t[0]}, {t[1]}, {t[2]}, {t[3]}, {t[4]}, '{t[5]}', '{t[6]}', {t[7]}){comma}")
w("")

# 14. Driving Licences (COVERING ALL STATUSES: ACTIVE, SUSPENDED, EXPIRED, REVOKED)
w("-- 14. Driving Licences (Covering ACTIVE, SUSPENDED, EXPIRED, REVOKED)")
dls = [
    # id, lic_num, citizen_id, ll_id, app_id, office_id, issue_date, expiry_date, current_status
    (1, 'KA01-2026000001', 1, 1, 11, 1, '2026-09-25', '2046-09-25', 'ACTIVE'),
    (2, 'KA01-2026000002', 2, 2, 10, 1, '2026-08-10', '2046-08-10', 'ACTIVE'),
    (3, 'KA02-2025000015', 3, 3, 8, 2, '2025-06-15', '2045-06-15', 'ACTIVE'),
    (4, 'KA03-2024000120', 4, 4, 6, 3, '2024-03-20', '2044-03-20', 'ACTIVE'),
    (5, 'GJ01-2025000088', 5, 'NULL', 12, 5, '2025-01-10', '2045-01-10', 'ACTIVE'),
    # Suspended:
    (6, 'KA01-2023000450', 6, 'NULL', 1, 1, '2023-04-12', '2043-04-12', 'SUSPENDED'),
    (7, 'KA02-2022000780', 20, 'NULL', 2, 2, '2022-11-05', '2042-11-05', 'SUSPENDED'),
    # Expired:
    (8, 'KA01-2006000099', 8, 'NULL', 3, 1, '2006-03-15', '2026-03-15', 'EXPIRED'),
    (9, 'KA03-2006000104', 9, 'NULL', 4, 3, '2006-08-20', '2026-08-20', 'EXPIRED'),
    # Revoked:
    (10, 'KA01-2020000333', 18, 'NULL', 5, 1, '2020-05-18', '2040-05-18', 'REVOKED'),
    (11, 'KA02-2019000222', 19, 'NULL', 7, 2, '2019-09-14', '2039-09-14', 'REVOKED')
]
w("INSERT INTO driving_licences (driving_licence_id, licence_number, citizen_id, learner_licence_id, application_id, office_id, issue_date, expiry_date, current_status) VALUES")
for i, dl in enumerate(dls):
    comma = "," if i < len(dls) - 1 else ";"
    ll_val = dl[3] if dl[3] != 'NULL' else 'NULL'
    w(f"  ({dl[0]}, '{dl[1]}', {dl[2]}, {ll_val}, {dl[4]}, {dl[5]}, '{dl[6]}', '{dl[7]}', '{dl[8]}'){comma}")
w("")

w("INSERT INTO licence_class_assignments (driving_licence_id, licence_class_id, granted_on) VALUES")
w("  (1, 3, '2026-09-25'), (1, 4, '2026-09-25'),") # MCWG, LMV
w("  (2, 4, '2026-08-10'),")                     # LMV
w("  (3, 3, '2025-06-15'), (3, 4, '2025-06-15'),") # MCWG, LMV
w("  (4, 5, '2024-03-20'), (4, 6, '2024-03-20'),") # HMV, TRANS
w("  (5, 4, '2025-01-10'),")                     # LMV
w("  (6, 4, '2023-04-12'),")                     # LMV
w("  (7, 3, '2022-11-05'), (7, 4, '2022-11-05'),") # MCWG, LMV
w("  (8, 4, '2006-03-15'),")                     # LMV
w("  (9, 4, '2006-08-20'),")                     # LMV
w("  (10, 4, '2020-05-18'),")                    # LMV
w("  (11, 5, '2019-09-14');")                    # HMV
w("")

w("INSERT INTO licence_status_history (history_id, driving_licence_id, previous_status, new_status, changed_by_user_id, changed_at, reason) VALUES")
w("  (1, 6, 'ACTIVE', 'SUSPENDED', 1, '2026-08-15 10:00:00', 'Suspended for 3 months following 3rd signal violation'),")
w("  (2, 7, 'ACTIVE', 'SUSPENDED', 1, '2026-09-10 14:30:00', 'Dangerous rash driving incident on Airport expressway'),")
w("  (3, 8, 'ACTIVE', 'EXPIRED', 1, '2026-03-16 00:00:00', 'Validity lapsed after 20 years'),")
w("  (4, 9, 'ACTIVE', 'EXPIRED', 1, '2026-08-21 00:00:00', 'Validity lapsed after 20 years'),")
w("  (5, 10, 'ACTIVE', 'REVOKED', 1, '2026-07-04 11:15:00', 'Revoked permanently under Section 19 due to fatal collision under influence'),")
w("  (6, 11, 'ACTIVE', 'REVOKED', 1, '2026-08-01 16:00:00', 'Revoked due to submission of forged heavy vehicle certificate');")
w("")

# 15. Vehicles, Ownership & Transfers
w("-- 15. Vehicles, Ownership & Transfers (ACTIVE, BLACKLISTED, SCRAPPED, DEREGISTERED)")
vehicles = [
    # id, reg_no, chassis, engine, mfr_id, model_id, type_id, fuel_id, year, color, office_id, reg_date, status
    (1, 'KA01AB1234', 'CH-SWIFT-90001', 'EN-SWIFT-90001', 1, 1, 2, 1, 2022, 'Pearl Arctic White', 1, '2022-03-15', 'ACTIVE'),
    (2, 'KA01CD5678', 'CH-NEXON-90002', 'EN-NEXON-90002', 3, 6, 2, 2, 2023, 'Daytona Grey', 1, '2023-05-20', 'ACTIVE'),
    (3, 'KA02EF9012', 'CH-SPLEN-90003', 'EN-SPLEN-90003', 6, 13, 1, 1, 2021, 'Black with Silver', 2, '2021-07-10', 'ACTIVE'),
    (4, 'KA02GH3456', 'CH-ATHER-90004', 'EN-ATHER-90004', 10, 17, 1, 4, 2024, 'Space Grey', 2, '2024-02-18', 'ACTIVE'),
    (5, 'KA03IJ7890', 'CH-CRETA-90005', 'EN-CRETA-90005', 2, 4, 2, 2, 2022, 'Titan Grey', 3, '2022-11-05', 'ACTIVE'),
    (6, 'KA01KL2345', 'CH-BOLER-90006', 'EN-BOLER-90006', 4, 10, 4, 2, 2020, 'Diamond White', 1, '2020-08-14', 'ACTIVE'),
    (7, 'KA09MN6789', 'CH-ALDOST-90007', 'EN-ALDOST-90007', 11, 18, 3, 2, 2019, 'Golden Brown', 4, '2019-12-01', 'ACTIVE'),
    (8, 'GJ01OP0123', 'CH-ACTVA-90008', 'EN-ACTVA-90008', 5, 12, 1, 1, 2023, 'Matte Axis Grey', 5, '2023-04-25', 'ACTIVE'),
    # Blacklisted:
    (9, 'KA01ST4567', 'CH-THAR-90009', 'EN-THAR-90009', 4, 8, 2, 2, 2023, 'Napoli Black', 1, '2023-01-10', 'BLACKLISTED'),
    (10, 'KA02UV8901', 'CH-PULSR-90010', 'EN-PULSR-90010', 7, 14, 1, 1, 2022, 'Sparkle Blue', 2, '2022-09-18', 'BLACKLISTED'),
    (11, 'GJ01WX2345', 'CH-BALEN-90011', 'EN-BALEN-90011', 1, 2, 2, 1, 2021, 'Nexa Blue', 5, '2021-06-30', 'BLACKLISTED'),
    # Scrapped:
    (12, 'KA01YZ6789', 'CH-INDCA-90012', 'EN-INDCA-90012', 3, 7, 2, 2, 2005, 'Silver', 1, '2005-04-12', 'SCRAPPED'),
    (13, 'KA02AA1122', 'CH-HERO-90013', 'EN-HERO-90013', 6, 13, 1, 1, 2004, 'Red', 2, '2004-09-08', 'SCRAPPED'),
    # Deregistered:
    (14, 'KA01CC5566', 'CH-HYUND-90014', 'EN-HYUND-90014', 2, 5, 2, 1, 2018, 'Polar White', 1, '2018-02-14', 'DEREGISTERED'),
    (15, 'KA03DD7788', 'CH-HCITY-90015', 'EN-HCITY-90015', 5, 11, 2, 1, 2017, 'Modern Steel', 3, '2017-06-25', 'DEREGISTERED')
]

w("INSERT INTO vehicles (vehicle_id, registration_number, chassis_number, engine_number, manufacturer_id, model_id, vehicle_type_id, fuel_type_id, manufacture_year, color, registering_office_id, registration_date, status) VALUES")
for i, v in enumerate(vehicles):
    comma = "," if i < len(vehicles) - 1 else ";"
    w(f"  ({v[0]}, '{v[1]}', '{v[2]}', '{v[3]}', {v[4]}, {v[5]}, {v[6]}, {v[7]}, {v[8]}, '{v[9]}', {v[10]}, '{v[11]}', '{v[12]}'){comma}")
w("")

# Vehicle Ownerships
w("INSERT INTO vehicle_ownerships (ownership_id, vehicle_id, citizen_id, effective_from, effective_to) VALUES")
ownerships = [
    # Active current ownerships:
    (1, 1, 1, '2022-03-15', 'NULL'), # Aarav Mehta
    (2, 2, 2, '2023-05-20', 'NULL'), # Priya Sharma
    (3, 3, 3, '2021-07-10', 'NULL'), # Rohan Patel
    (4, 4, 4, '2024-02-18', 'NULL'), # Sunita Desai
    (5, 5, 5, '2022-11-05', 'NULL'), # Kiran Joshi
    (6, 6, 6, '2020-08-14', 'NULL'), # Pooja Singh (Bolero Truck)
    (7, 7, 7, '2019-12-01', 'NULL'), # Amit Verma (Ashok Leyland)
    (8, 8, 8, '2023-04-25', 'NULL'), # Neha Gupta (Activa)
    (9, 9, 18, '2023-01-10', 'NULL'), # Harish Chandra (Blacklisted Thar)
    (10, 10, 19, '2022-09-18', 'NULL'), # Manish Tiwari (Blacklisted Pulsar)
    (11, 11, 20, '2021-06-30', 'NULL'), # Praveen Sood (Blacklisted Baleno)
    (12, 12, 9, '2005-04-12', 'NULL'), # Scrapped
    (13, 13, 10, '2004-09-08', 'NULL'), # Scrapped
    # Change of ownership history for vehicle 1 (previous owner citizen 11 transferred to citizen 1):
    (14, 1, 11, '2020-01-10', "'2022-03-14'"),
    # Vehicle 14 deregistered:
    (15, 14, 12, '2018-02-14', "'2026-01-15'"),
    (16, 15, 13, '2017-06-25', "'2026-03-10'")
]
for i, o in enumerate(ownerships):
    comma = "," if i < len(ownerships) - 1 else ";"
    w(f"  ({o[0]}, {o[1]}, {o[2]}, '{o[3]}', {o[4]}){comma}")
w("")

# Ownership Transfers
w("INSERT INTO ownership_transfers (transfer_id, vehicle_id, from_citizen_id, to_citizen_id, application_id, requested_at, approved_at, status) VALUES")
w("  (1, 1, 11, 1, 6, '2022-03-01 10:00:00', '2022-03-14 15:30:00', 'APPROVED'),")
w("  (2, 5, 5, 14, 15, '2026-10-17 13:20:00', NULL, 'PENDING'),")
w("  (3, 3, 3, 15, 16, '2026-10-10 11:00:00', NULL, 'PENDING'),")
w("  (4, 2, 2, 16, 7, '2026-09-15 09:30:00', '2026-09-20 14:00:00', 'REJECTED');")
w("")

# 16. Compliance: Inspections, Fitness, PUC, Insurance, Road Tax
# CURRENT SIMULATED DATE: 2026-10-22
# To support all compliance filters (7, 15, 30, 60, 90 days expiring):
# - 7-day: expiry between 2026-10-23 and 2026-10-29
# - 15-day: expiry between 2026-10-30 and 2026-11-06
# - 30-day: expiry between 2026-11-07 and 2026-11-21
# - 60-day: expiry in Dec 2026
# - 90-day: expiry in Jan 2027
w("-- 16. Vehicle Compliance & Inspections (Designed for 7, 15, 30, 60, 90 day filter thresholds)")
inspections = [
    # id, vehicle_id, inspector, inspected_at, result, remarks
    (1, 6, 3, '2025-10-25 10:00:00', 'PASS', "'All safety lighting, air brakes, and chassis rivets intact'"),
    (2, 7, 4, '2025-11-05 11:30:00', 'PASS', "'Commercial vehicle fitness inspection passed'"),
    (3, 1, 2, '2025-11-18 14:00:00', 'PASS', "'Headlight high/low beam aligned; emission levels within limits'"),
    (4, 2, 2, '2025-12-15 09:30:00', 'PASS', "'Comprehensive annual mechanical check verified'"),
    (5, 9, 3, '2025-08-10 15:00:00', 'FAIL', "'Brake fluid leakage and severely cracked front windshield'"),
    (6, 10, 4, '2025-09-15 16:15:00', 'FAIL', "'Silencer catalytic converter removed; excessive decibel noise'")
]
w("INSERT INTO vehicle_inspections (inspection_id, vehicle_id, inspector_employee_id, inspected_at, result, remarks) VALUES")
for i, insp in enumerate(inspections):
    comma = "," if i < len(inspections) - 1 else ";"
    w(f"  ({insp[0]}, {insp[1]}, {insp[2]}, '{insp[3]}', '{insp[4]}', {insp[5]}){comma}")
w("")

# Fitness Certificates:
w("INSERT INTO fitness_certificates (certificate_id, vehicle_id, inspection_id, issue_date, expiry_date, status) VALUES")
# vehicle 6: expiry 2026-10-26 (4 days away -> 7-day filter)
# vehicle 7: expiry 2026-11-05 (14 days away -> 15-day filter)
# vehicle 1: expiry 2026-11-18 (27 days away -> 30-day filter)
# vehicle 2: expiry 2026-12-15 (54 days away -> 60-day filter)
# vehicle 3: expiry 2027-01-15 (85 days away -> 90-day filter)
# vehicle 9: expired 2026-08-10
# vehicle 12: expired 2025-04-12
w("  (1, 6, 1, '2025-10-26', '2026-10-26', 'ACTIVE'),")
w("  (2, 7, 2, '2025-11-05', '2026-11-05', 'ACTIVE'),")
w("  (3, 1, 3, '2025-11-18', '2026-11-18', 'ACTIVE'),")
w("  (4, 2, 4, '2025-12-15', '2026-12-15', 'ACTIVE'),")
w("  (5, 3, NULL, '2026-01-15', '2027-01-15', 'ACTIVE'),")
w("  (6, 9, 5, '2025-08-10', '2026-08-10', 'EXPIRED'),")
w("  (7, 12, NULL, '2024-04-12', '2025-04-12', 'EXPIRED'),")
w("  (8, 4, NULL, '2026-06-01', '2027-06-01', 'REVOKED');  -- revoked while still within validity")
w("")

# Pollution Certificates (PUC):
w("INSERT INTO pollution_certificates (puc_id, vehicle_id, issue_date, expiry_date, status) VALUES")
# vehicle 1: expiry 2026-10-27 (5 days away -> 7-day filter)
# vehicle 3: expiry 2026-11-02 (11 days away -> 15-day filter)
# vehicle 5: expiry 2026-11-15 (24 days away -> 30-day filter)
# vehicle 6: expiry 2026-12-20 (59 days away -> 60-day filter)
# vehicle 8: expiry 2027-01-18 (88 days away -> 90-day filter)
# vehicle 2: expired 2026-09-15
# vehicle 7: expired 2026-07-20
w("  (1, 1, '2026-04-27', '2026-10-27', 'ACTIVE'),")
w("  (2, 3, '2026-05-02', '2026-11-02', 'ACTIVE'),")
w("  (3, 5, '2026-05-15', '2026-11-15', 'ACTIVE'),")
w("  (4, 6, '2026-06-20', '2026-12-20', 'ACTIVE'),")
w("  (5, 8, '2026-07-18', '2027-01-18', 'ACTIVE'),")
w("  (6, 2, '2026-03-15', '2026-09-15', 'EXPIRED'),")
w("  (7, 7, '2026-01-20', '2026-07-20', 'EXPIRED');")
w("")

# Insurance Policies:
w("INSERT INTO insurance_policies (policy_id, vehicle_id, provider_name, policy_number, start_date, end_date) VALUES")
# vehicle 1: expiry 2026-10-28 (6 days away -> 7-day filter)
# vehicle 3: expiry 2026-11-04 (13 days away -> 15-day filter)
# vehicle 6: expiry 2026-11-19 (28 days away -> 30-day filter)
# vehicle 2: expiry 2026-12-25 (64 days away)
# vehicle 5: expiry 2027-01-20 (90 days away)
# vehicle 4: expired 2026-06-30
# vehicle 7: expired 2026-08-15
w("  (1, 1, 'New India Assurance', 'NIA-KA-2025-00192', '2025-10-28', '2026-10-28'),")
w("  (2, 3, 'HDFC ERGO General Insurance', 'HDF-KA-2025-00441', '2025-11-04', '2026-11-04'),")
w("  (3, 6, 'ICICI Lombard General Insurance', 'ICI-KA-2025-00812', '2025-11-19', '2026-11-19'),")
w("  (4, 2, 'Bajaj Allianz General Insurance', 'BAJ-KA-2025-00334', '2025-12-25', '2026-12-25'),")
w("  (5, 5, 'Tata AIG General Insurance', 'AIG-KA-2026-00911', '2026-01-20', '2027-01-20'),")
w("  (6, 4, 'Oriental Insurance Company', 'ORI-KA-2025-00045', '2025-06-30', '2026-06-30'),")
w("  (7, 7, 'National Insurance Company', 'NIC-KA-2025-00789', '2025-08-15', '2026-08-15');")
w("")

# Road Tax Records:
w("INSERT INTO road_tax_records (tax_record_id, vehicle_id, assessment_year, amount_due, due_date, status) VALUES")
w("  (1, 1, 2026, 4500.00, '2026-03-31', 'PAID'),")
w("  (2, 2, 2026, 5200.00, '2026-03-31', 'PAID'),")
w("  (3, 3, 2026, 1200.00, '2026-03-31', 'PAID'),")
w("  (4, 4, 2026, 800.00, '2026-03-31', 'PAID'),")
w("  (5, 5, 2026, 5500.00, '2026-11-30', 'DUE'),")
w("  (6, 6, 2026, 8200.00, '2026-11-15', 'DUE'),")
w("  (7, 7, 2026, 12500.00, '2026-08-31', 'OVERDUE'),")
w("  (8, 8, 2026, 1500.00, '2026-09-30', 'OVERDUE'),")
w("  (9, 9, 2026, 6500.00, '2026-07-31', 'OVERDUE');")
w("")

# 17. Routes, Segments & Commercial Permits
w("-- 17. Routes, Segments & Commercial Permits (ACTIVE, EXPIRED, SUSPENDED, CANCELLED)")
w("INSERT INTO routes (route_id, route_name, origin, destination) VALUES")
w("  (1, 'Bengaluru – Mysuru Expressway Route', 'Bengaluru', 'Mysuru'),")
w("  (2, 'Bengaluru – Chennai Industrial Corridor', 'Bengaluru', 'Chennai'),")
w("  (3, 'Ahmedabad – Surat Superfast Highway', 'Ahmedabad', 'Surat'),")
w("  (4, 'Bengaluru – Mangaluru Coastal Route', 'Bengaluru', 'Mangaluru');")
w("")

w("INSERT INTO route_segments (segment_id, route_id, sequence_no, segment_name) VALUES")
w("  (1, 1, 1, 'Bengaluru Kengeri'), (2, 1, 2, 'Bidadi Industrial Area'), (3, 1, 3, 'Ramanagara'), (4, 1, 4, 'Mandya'), (5, 1, 5, 'Mysuru Ring Road'),")
w("  (6, 2, 1, 'Bengaluru Electronic City'), (7, 2, 2, 'Hosur'), (8, 2, 3, 'Krishnagiri'), (9, 2, 4, 'Vellore'), (10, 2, 5, 'Chennai Sriperumbudur'),")
w("  (11, 3, 1, 'Ahmedabad Subhash Bridge'), (12, 3, 2, 'Nadiad'), (13, 3, 3, 'Anand'), (14, 3, 4, 'Vadodara Bypass'), (15, 3, 5, 'Surat Ring Road');")
w("")

permits = [
    # id, perm_num, vehicle_id, citizen_id, type_id, route_id, app_id, issue_date, expiry_date, status
    (1, 'PERM-KA01-2026-001', 6, 6, 1, 1, 8, '2026-01-15', '2031-01-15', 'ACTIVE'),
    (2, 'PERM-KA01-2026-002', 7, 7, 2, 2, 8, '2025-11-10', '2030-11-10', 'ACTIVE'),
    (3, 'PERM-KA02-2026-003', 1, 1, 3, 1, 8, '2025-11-05', '2026-11-05', 'ACTIVE'), # expiring within 30 days!
    (4, 'PERM-GJ01-2025-004', 8, 8, 4, 3, 8, '2025-04-01', '2030-04-01', 'ACTIVE'),
    # Expired:
    (5, 'PERM-KA01-2021-005', 6, 6, 5, 1, 8, '2026-08-01', '2026-09-01', 'EXPIRED'),
    (6, 'PERM-KA02-2021-006', 7, 7, 5, 2, 8, '2026-07-15', '2026-08-15', 'EXPIRED'),
    # Suspended:
    (7, 'PERM-KA01-2024-007', 6, 6, 1, 1, 8, '2024-05-10', '2029-05-10', 'SUSPENDED'),
    # Cancelled:
    (8, 'PERM-KA03-2023-008', 7, 7, 2, 4, 8, '2023-02-14', '2028-02-14', 'CANCELLED')
]
w("INSERT INTO permits (permit_id, permit_number, vehicle_id, citizen_id, permit_type_id, route_id, application_id, issue_date, expiry_date, status) VALUES")
for i, pm in enumerate(permits):
    comma = "," if i < len(permits) - 1 else ";"
    w(f"  ({pm[0]}, '{pm[1]}', {pm[2]}, {pm[3]}, {pm[4]}, {pm[5]}, {pm[6]}, '{pm[7]}', '{pm[8]}', '{pm[9]}'){comma}")
w("")

w("INSERT INTO permit_status_history (history_id, permit_id, previous_status, new_status, changed_at, reason) VALUES")
w("  (1, 5, 'ACTIVE', 'EXPIRED', '2026-09-02 00:00:00', 'Validity period completed'),")
w("  (2, 6, 'ACTIVE', 'EXPIRED', '2026-08-16 00:00:00', 'Validity period completed'),")
w("  (3, 7, 'ACTIVE', 'SUSPENDED', '2026-09-12 11:30:00', 'Overloading by 4.8 tonnes flagged at weighbridge'),")
w("  (4, 8, 'ACTIVE', 'CANCELLED', '2026-07-20 15:45:00', 'Commercial vehicle operated on non-permitted diversion corridor');")
w("")

# 18. Violations & E-Challans (ISSUED, DISPUTED, PAID, CANCELLED)
w("-- 18. Violations & E-Challans (Covering ISSUED, DISPUTED, PAID, CANCELLED)")
violations = [
    # id, vehicle_id, driver_id, vio_type_id, officer_id, location, occurred_at
    (1, 1, 10, 1, 4, 'Hosur Road Electronic City Flyover', '2026-10-15 08:30:00'), # 1000
    (2, 1, 10, 3, 4, 'Madiwala St. Johns Signal', '2026-10-15 08:45:00'),         # 500
    (3, 2, 11, 5, 4, 'Outer Ring Road, Marathahalli', '2026-10-12 11:20:00'),      # 1500
    (4, 3, 12, 6, 6, '100ft Road Indiranagar Junction', '2026-10-10 17:15:00'),    # 1000
    (5, 5, 14, 4, 4, 'Koramangala 80ft Road, Sony Signal', '2026-10-08 22:30:00'), # 10000 (Drunk Driving)
    (6, 6, 15, 2, 6, 'Yeshwantpur Toll Gate', '2026-09-28 14:00:00'),              # 5000 (No Licence)
    (7, 7, 16, 7, 4, 'Mysuru Road Satellite Bus Stand', '2026-09-20 19:45:00'),    # 5000 (Dangerous)
    (8, 8, 17, 1, 8, 'Ahmedabad SG Highway Karnavati Club', '2026-10-05 10:10:00'),# 1000
    (9, 9, 18, 8, 4, 'MG Road Brigade Junction', '2026-09-18 16:00:00'),           # 2000 (No Insurance)
    (10, 10, 19, 7, 6, 'Rajajinagar 1st Block Navrang Circle', '2026-09-15 21:00:00') # 5000
]
w("INSERT INTO violations (violation_id, vehicle_id, driver_citizen_id, violation_type_id, officer_employee_id, location, occurred_at) VALUES")
for i, vl in enumerate(violations):
    comma = "," if i < len(violations) - 1 else ";"
    w(f"  ({vl[0]}, {vl[1]}, {vl[2]}, {vl[3]}, {vl[4]}, '{vl[5]}', '{vl[6]}'){comma}")
w("")

challans = [
    # id, challan_num, total, status, issued_at
    (1, 'CHAL-2026-000001', 1500.00, 'ISSUED', '2026-10-15 09:00:00'),    # Violations 1+2
    (2, 'CHAL-2026-000002', 1500.00, 'ISSUED', '2026-10-12 12:00:00'),    # Violation 3
    (3, 'CHAL-2026-000003', 10000.00, 'DISPUTED', '2026-10-08 23:00:00'), # Violation 5
    (4, 'CHAL-2026-000004', 1000.00, 'DISPUTED', '2026-10-10 18:00:00'),  # Violation 4
    (5, 'CHAL-2026-000005', 5000.00, 'PAID', '2026-09-28 15:00:00'),      # Violation 6
    (6, 'CHAL-2026-000006', 5000.00, 'PAID', '2026-09-20 20:30:00'),      # Violation 7
    (7, 'CHAL-2026-000007', 1000.00, 'PAID', '2026-10-05 11:00:00'),      # Violation 8
    (8, 'CHAL-2026-000008', 2000.00, 'CANCELLED', '2026-09-18 17:00:00'), # Violation 9
    (9, 'CHAL-2026-000009', 5000.00, 'CANCELLED', '2026-09-15 22:00:00')  # Violation 10
]
w("INSERT INTO challans (challan_id, challan_number, total_amount, status, issued_at) VALUES")
for i, ch in enumerate(challans):
    comma = "," if i < len(challans) - 1 else ";"
    w(f"  ({ch[0]}, '{ch[1]}', {ch[2]}, '{ch[3]}', '{ch[4]}'){comma}")
w("")

w("INSERT INTO challan_violations (challan_id, violation_id) VALUES")
w("  (1, 1), (1, 2), (2, 3), (3, 5), (4, 4), (5, 6), (6, 7), (7, 8), (8, 9), (9, 10);")
w("")

w("INSERT INTO challan_status_history (history_id, challan_id, previous_status, new_status, changed_at) VALUES")
w("  (1, 1, 'NULL', 'ISSUED', '2026-10-15 09:00:00'),")
w("  (2, 3, 'ISSUED', 'DISPUTED', '2026-10-10 14:00:00'),")
w("  (3, 4, 'ISSUED', 'DISPUTED', '2026-10-12 11:30:00'),")
w("  (4, 5, 'ISSUED', 'PAID', '2026-09-29 10:15:00'),")
w("  (5, 6, 'ISSUED', 'PAID', '2026-09-22 16:45:00'),")
w("  (6, 7, 'ISSUED', 'PAID', '2026-10-06 09:30:00'),")
w("  (7, 8, 'ISSUED', 'CANCELLED', '2026-09-20 12:00:00'),")
w("  (8, 9, 'ISSUED', 'CANCELLED', '2026-09-18 10:00:00');")
w("")

# 19. Fee Structures, Payments, Payment Attempts & Refunds
w("-- 19. Fee Structures, Payments & Refunds (SUCCESS, PENDING, FAILED, REFUNDED)")
w("INSERT INTO fee_structures (fee_id, service_type_id, component_name, amount, effective_from, effective_to) VALUES")
w("  (1, 1, 'DL Smart Card Fee', 200.00, '2024-01-01', NULL),")
w("  (2, 1, 'Automated Driving Test Fee', 300.00, '2024-01-01', NULL),")
w("  (3, 2, 'Licence Renewal Processing Charge', 300.00, '2024-01-01', NULL),")
w("  (4, 3, 'Vehicle Ownership Transfer Endorsement', 750.00, '2024-01-01', NULL),")
w("  (5, 4, 'Commercial Route Permit Issuance', 1000.00, '2024-01-01', NULL),")
w("  (6, 5, 'Vehicle Registration Smart Card & Number Plate', 800.00, '2024-01-01', NULL),")
w("  (7, 6, 'Learner Licence Computer Test Fee', 200.00, '2024-01-01', NULL);")
w("")

payments = [
    # id, receipt_num, payable_type_id, payable_id, amount, status, paid_at, created_at
    (1, 'RCT-2026-000001', 1, 11, 500.00, 'SUCCESS', "'2026-09-15 11:00:00'", '2026-09-15 10:55:00'),
    (2, 'RCT-2026-000002', 1, 10, 200.00, 'SUCCESS', "'2026-10-02 09:20:00'", '2026-10-02 09:18:00'),
    (3, 'RCT-2026-000003', 2, 5, 5000.00, 'SUCCESS', "'2026-09-29 10:15:00'", '2026-09-29 10:10:00'),
    (4, 'RCT-2026-000004', 2, 6, 5000.00, 'SUCCESS', "'2026-09-22 16:45:00'", '2026-09-22 16:40:00'),
    (5, 'RCT-2026-000005', 3, 1, 1000.00, 'SUCCESS', "'2026-01-15 11:00:00'", '2026-01-15 10:50:00'),
    (6, 'RCT-2026-000006', 4, 1, 4500.00, 'SUCCESS', "'2026-03-25 14:30:00'", '2026-03-25 14:20:00'),
    (7, 'RCT-2026-000007', 4, 2, 5200.00, 'SUCCESS', "'2026-03-28 16:00:00'", '2026-03-28 15:50:00'),
    (8, 'RCT-2026-000008', 1, 5, 800.00, 'PENDING', 'NULL', '2026-10-15 15:35:00'),
    (9, 'RCT-2026-000009', 2, 1, 1500.00, 'PENDING', 'NULL', '2026-10-16 11:00:00'),
    (10, 'RCT-2026-000010', 1, 17, 300.00, 'FAILED', 'NULL', '2026-10-17 14:45:00'),
    (11, 'RCT-2026-000011', 1, 1, 500.00, 'FAILED', 'NULL', '2026-10-21 09:35:00'),
    (12, 'RCT-2026-000012', 1, 9, 400.00, 'REFUNDED', "'2026-10-01 12:15:00'", '2026-10-01 12:05:00'),
    (13, 'RCT-2026-000013', 2, 8, 2000.00, 'REFUNDED', "'2026-09-18 18:00:00'", '2026-09-18 17:50:00')
]
w("INSERT INTO payments (payment_id, receipt_number, payable_type_id, payable_id, amount, status, paid_at, created_at) VALUES")
for i, pm in enumerate(payments):
    comma = "," if i < len(payments) - 1 else ";"
    w(f"  ({pm[0]}, '{pm[1]}', {pm[2]}, {pm[3]}, {pm[4]}, '{pm[5]}', {pm[6]}, '{pm[7]}'){comma}")
w("")

payment_attempts = [
    (1, 1, 1, 'GW-SBIPAY-88001', 'SUCCESS', '2026-09-15 11:00:00'),
    (2, 2, 1, 'GW-HDFCPG-88002', 'SUCCESS', '2026-10-02 09:20:00'),
    (3, 3, 1, 'GW-PAYTM-88003', 'SUCCESS', '2026-09-29 10:15:00'),
    (4, 4, 1, 'GW-RAZOR-88004', 'SUCCESS', '2026-09-22 16:45:00'),
    (5, 8, 1, 'GW-SBIPAY-88005', 'TIMEOUT', '2026-10-15 15:36:00'),
    (6, 10, 1, 'GW-ICICI-88006', 'FAILED', '2026-10-17 14:46:00'),
    (7, 11, 1, 'GW-SBIPAY-88007', 'FAILED', '2026-10-21 09:36:00'),
    (8, 12, 1, 'GW-HDFCPG-88008', 'SUCCESS', '2026-10-01 12:15:00'),
    (9, 13, 1, 'GW-RAZOR-88009', 'SUCCESS', '2026-09-18 18:00:00')
]
w("INSERT INTO payment_attempts (attempt_id, payment_id, attempt_number, gateway_reference, outcome, attempted_at) VALUES")
for i, pa in enumerate(payment_attempts):
    comma = "," if i < len(payment_attempts) - 1 else ";"
    w(f"  ({pa[0]}, {pa[1]}, {pa[2]}, '{pa[3]}', '{pa[4]}', '{pa[5]}'){comma}")
w("")

# Refunds (INITIATED, COMPLETED, FAILED)
w("INSERT INTO refunds (refund_id, payment_id, amount, reason, status, processed_at) VALUES")
w("  (1, 12, 400.00, 'Duplicate Application Cancelled by citizen within grace period', 'COMPLETED', '2026-10-03 14:00:00'),")
w("  (2, 13, 2000.00, 'Challan quashed by Appellate Tribunal order #AT-2026-08', 'COMPLETED', '2026-09-25 16:30:00'),")
w("  (3, 3, 5000.00, 'Double deduction during UPI server glitch', 'INITIATED', NULL),")
w("  (4, 1, 500.00, 'Citizen requested withdrawal of DL application', 'INITIATED', NULL),")
w("  (5, 4, 1500.00, 'Bank account IFSC code mismatch during NEFT reversal', 'FAILED', '2026-09-24 11:20:00');")
w("")

# 20. Citizen Complaints (OPEN, IN_PROGRESS, RESOLVED, CLOSED)
w("-- 20. Citizen Complaints & Grievance Tickets")
complaints = [
    (1, 1, 1, 'High wait time at Koramangala counter C-02', 'Waited for 2.5 hours past scheduled token time. Only one officer was processing vehicle registration paperwork.', 'OPEN', '2026-10-21 14:30:00'),
    (2, 2, 2, 'Driving Licence Smart Card dispatch delayed', 'Passed driving test 4 weeks ago; smart card tracking ID shows not dispatched from Centralized Print Facility.', 'OPEN', '2026-10-20 11:15:00'),
    (3, 3, 1, 'Excessive user fee collected for duplicate NOC', 'Charged additional 200 rupees convenience fee without printed government receipt.', 'IN_PROGRESS', '2026-10-18 16:00:00'),
    (4, 4, 3, 'Incorrect spelling of father name on RC Book', 'Application Form 20 had correct spelling "SURYAKANT", but printed RC shows "SURAYKANT". Need correction.', 'IN_PROGRESS', '2026-10-16 10:45:00'),
    (5, 5, 1, 'Automated Test Track sensor malfunction', 'Track sensor flagged boundary foul during reverse S manoeuvre even though vehicle tyres were 8 inches inside yellow marker.', 'RESOLVED', '2026-10-10 09:30:00'),
    (6, 6, 2, 'Online slot booking calendar freezing at 10 AM', 'System throws Gateway Timeout 504 when attempting to book morning inspection slot for heavy vehicles.', 'RESOLVED', '2026-10-05 10:15:00'),
    (7, 7, 5, 'RTO Ahmedabad counter closed during official public hours', 'Arrived at 12:45 PM for fitness certificate collection, counter shutter was down before designated lunch break.', 'CLOSED', '2026-09-25 15:00:00'),
    (8, 8, 1, 'Duplicate payment deducted for Learner Licence slot', 'UPI payment debited twice for appointment token. Need refund credited back to Kotak bank account.', 'CLOSED', '2026-09-18 12:00:00')
]
w("INSERT INTO complaints (complaint_id, citizen_id, office_id, subject, description, status, filed_at) VALUES")
for i, cm in enumerate(complaints):
    comma = "," if i < len(complaints) - 1 else ";"
    w(f"  ({cm[0]}, {cm[1]}, {cm[2]}, '{cm[3]}', '{cm[4]}', '{cm[5]}', '{cm[6]}'){comma}")
w("")

# 21. Legal & Statutory Appeals (FILED, UNDER_REVIEW, UPHELD, DISMISSED)
w("-- 21. Legal & Statutory Appeals (CHALLAN, APPLICATION_REJECTION, LICENCE_SUSPENSION)")
appeals = [
    # id, citizen_id, against_type, against_id, grounds, status, filed_at, resolved_at
    (1, 10, 'CHALLAN', 3, 'Challan issued for alleged drunk driving at 11 PM, but government hospital blood test report shows 0.00 BAC. Request quashing of 10000 penalty.', 'FILED', '2026-10-12 11:00:00', 'NULL'),
    (2, 11, 'APPLICATION_REJECTION', 7, 'Driving test rejected claiming parallel bay kerb touch, but Dashcam footage proves vehicle remained within designated parking bay. Appeal for re-evaluation.', 'UNDER_REVIEW', '2026-10-14 14:30:00', 'NULL'),
    (3, 12, 'LICENCE_SUSPENSION', 6, 'Licence suspended following 3 red light cameras, but vehicle was stolen on 2026-08-10 with police FIR #312 registered at Madiwala station.', 'UPHELD', '2026-08-20 10:00:00', "'2026-09-05 16:00:00'"),
    (4, 13, 'CHALLAN', 4, 'Speed radar captured adjacent overtaking BMW, not my vehicle which was cruising at 52 km/h. Optical radar calibration certificate is expired.', 'UPHELD', '2026-10-11 15:00:00', "'2026-10-18 11:30:00'"),
    (5, 18, 'LICENCE_SUSPENSION', 7, 'Appellant claims emergency medical transit during rash driving incident, but failed to provide hospital admission record or ambulance escort slip.', 'DISMISSED', '2026-09-15 09:30:00', "'2026-09-28 14:00:00'"),
    (6, 19, 'APPLICATION_REJECTION', 19, 'Rejection of commercial permit grounds upheld as vehicle chassis length exceeds maximum permissible length for intra-city school bus transport.', 'DISMISSED', '2026-09-30 11:00:00', "'2026-10-08 17:00:00'")
]
w("INSERT INTO appeals (appeal_id, citizen_id, against_type, against_id, grounds, status, filed_at, resolved_at) VALUES")
for i, ap in enumerate(appeals):
    comma = "," if i < len(appeals) - 1 else ";"
    w(f"  ({ap[0]}, {ap[1]}, '{ap[2]}', {ap[3]}, '{ap[4]}', '{ap[5]}', '{ap[6]}', {ap[7]}){comma}")
w("")

# 22. Notifications & Audit Logs
w("-- 22. Digital Notifications (SMS & EMAIL) & System Audit Logs")
notifications = [
    (1, 10, 'SMS', 'NULL', 'Your DL application APP-2026-00001 has been successfully submitted. Tracking Token: APP-00001.', 'SENT', "'2026-10-21 09:32:00'"),
    (2, 11, 'EMAIL', 'Document Re-upload Required', 'Medical Certificate Form 1A for APP-2026-00002 was rejected due to missing doctor stamp. Please re-upload.', 'SENT', "'2026-10-20 11:32:00'"),
    (3, 12, 'SMS', 'NULL', 'E-Challan CHAL-2026-000001 of Rs. 1500 issued for vehicle KA01AB1234. Pay online via Parivahan/RTO portal.', 'SENT', "'2026-10-15 09:05:00'"),
    (4, 13, 'EMAIL', 'Driving Licence Approved', 'Congratulations! Your Permanent Driving Licence KA01-2026000001 has been APPROVED. Smart card queued for dispatch.', 'SENT', "'2026-09-25 15:46:00'"),
    (5, 14, 'SMS', 'NULL', 'Appointment scheduled for driving test on 2026-10-24 10:00 at Koramangala Test Track. Token: TKN-KA02-001.', 'QUEUED', 'NULL'),
    (6, 15, 'EMAIL', 'Commercial Permit Renewal Notice', 'Your permit PERM-KA02-2026-003 is expiring in 14 days. Please apply for extension to avoid enforcement penalties.', 'QUEUED', 'NULL'),
    (7, 16, 'SMS', 'NULL', 'Payment gateway alert: Transaction timeout for receipt RCT-2026-000008. Amount will be refunded within 3 days if debited.', 'FAILED', "'2026-10-15 15:40:00'")
]
w("INSERT INTO notifications (notification_id, person_id, channel, subject, message, status, sent_at) VALUES")
for i, n in enumerate(notifications):
    comma = "," if i < len(notifications) - 1 else ";"
    sub = f"'{n[3]}'" if n[3] != 'NULL' else 'NULL'
    w(f"  ({n[0]}, {n[1]}, '{n[2]}', {sub}, '{n[4]}', '{n[5]}', {n[6]}){comma}")
w("")

audit_entries = [
    (1, 'applications', 1, 'INSERT', 'NULL', '{"application_number":"APP-2026-00001","status":"SUBMITTED"}', 1, "'2026-10-21 09:30:00'"),
    (2, 'applications', 2, 'UPDATE', '{"status":"SUBMITTED"}', '{"status":"DOCS_PENDING","reason":"Missing doctor stamp"}', 2, "'2026-10-20 11:30:00'"),
    (3, 'vehicles', 1, 'INSERT', 'NULL', '{"registration_number":"KA01AB1234","status":"ACTIVE"}', 1, "'2022-03-15 10:00:00'"),
    (4, 'driving_licences', 1, 'INSERT', 'NULL', '{"licence_number":"KA01-2026000001","status":"ACTIVE"}', 2, "'2026-09-25 15:45:00'"),
    (5, 'driving_licences', 6, 'UPDATE', '{"status":"ACTIVE"}', '{"status":"SUSPENDED","reason":"Section 19 suspension"}', 1, "'2026-08-15 10:00:00'"),
    (6, 'challans', 1, 'INSERT', 'NULL', '{"challan_number":"CHAL-2026-000001","total_amount":1500.00,"status":"ISSUED"}', 4, "'2026-10-15 09:00:00'"),
    (7, 'challans', 5, 'UPDATE', '{"status":"ISSUED"}', '{"status":"PAID","receipt_number":"RCT-2026-000003"}', 2, "'2026-09-29 10:15:00'"),
    (8, 'complaints', 1, 'INSERT', 'NULL', '{"citizen_id":1,"office_id":1,"status":"OPEN"}', 6, "'2026-10-21 14:30:00'"),
    (9, 'permits', 1, 'INSERT', 'NULL', '{"permit_number":"PERM-KA01-2026-001","status":"ACTIVE"}', 2, "'2026-01-15 11:00:00'"),
    (10, 'appeals', 3, 'UPDATE', '{"status":"UNDER_REVIEW"}', '{"status":"UPHELD","resolution":"FIR confirmed vehicle theft"}', 1, "'2026-09-05 16:00:00'"),
    (11, 'documents', 12, 'DELETE', '{"verification_status":"REJECTED","file_path":"storage/app14/water_bill.pdf"}', 'NULL', 1, "'2026-10-20 09:30:00'")
]
# A longer trail so the audit-log page has more than one page of history to paginate and filter.
_tables = ['applications', 'vehicles', 'payments', 'citizens', 'challans', 'documents']
for _n in range(12, 62):
    _t = _tables[_n % len(_tables)]
    _day = 1 + (_n * 2) % 28
    audit_entries.append((_n, _t, 1 + _n % 9, 'UPDATE',
                          '{"status":"PENDING"}', '{"status":"REVIEWED","batch":' + str(_n) + '}',
                          1 + _n % 5, f"'2026-09-{_day:02d} {8 + _n % 10:02d}:{(_n * 7) % 60:02d}:00'"))
w("INSERT INTO audit_logs (audit_id, table_name, record_id, action, old_values, new_values, changed_by_user_id, changed_at) VALUES")
for i, al in enumerate(audit_entries):
    comma = "," if i < len(audit_entries) - 1 else ";"
    old_val = f"'{al[4]}'" if al[4] != 'NULL' else 'NULL'
    new_val = f"'{al[5]}'" if al[5] != 'NULL' else 'NULL'
    w(f"  ({al[0]}, '{al[1]}', {al[2]}, '{al[3]}', {old_val}, {new_val}, {al[6]}, {al[7]}){comma}")
w("")

w("SET FOREIGN_KEY_CHECKS = 1;")
w("")
w("-- End of seed script")

with open(sql_path, "w", encoding="utf-8") as f:
    f.write("\n".join(sql))

print(f"Generated seed file at {sql_path} with {len(sql)} lines.")
