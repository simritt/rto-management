-- ============================================================
-- RTO Management System
-- views/17_views.sql
-- Operational MySQL VIEWs for the RTO domain.
--
-- All views are read-only (SELECT only).
-- They do not interfere with JPA / Hibernate — the backend does not
-- map these views to entities. They serve:
--   • Reporting / dashboard queries
--   • Admin SQL clients
--   • Advanced-query demonstrations (file 21)
--
-- Every column reference has been verified against the actual base
-- schema (files 02–14). No assumed column names.
--
-- Depends on: 01–14 (all base tables must exist).
-- ============================================================

USE rto_management;

-- ------------------------------------------------------------
-- 1. vw_current_vehicle_owners
--    Who currently owns each vehicle.
--    Joins vehicle_ownerships (effective_to IS NULL) → vehicles →
--    citizens → persons.
--    Used by: compliance checks, ownership-transfer dashboards.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW vw_current_vehicle_owners AS
SELECT
    v.vehicle_id,
    v.registration_number,
    v.chassis_number,
    v.engine_number,
    v.status                        AS vehicle_status,
    v.manufacture_year,
    v.color,
    v.registration_date,
    vm.name                         AS manufacturer_name,
    vmo.model_name,
    vt.type_name                    AS vehicle_type,
    ft.fuel_name                    AS fuel_type,
    o.vehicle_id                    AS ownership_vehicle_id,   -- sanity cross-check
    o.ownership_id,
    o.citizen_id                    AS owner_citizen_id,
    o.effective_from                AS owner_since,
    c.citizen_code,
    p.person_id                     AS owner_person_id,
    p.first_name,
    p.last_name,
    p.phone_primary,
    p.email,
    off.office_code                 AS registering_office_code,
    off.office_name                 AS registering_office_name
FROM vehicle_ownerships o
JOIN vehicles           v   ON v.vehicle_id       = o.vehicle_id
JOIN vehicle_manufacturers vm ON vm.manufacturer_id = v.manufacturer_id
JOIN vehicle_models     vmo ON vmo.model_id        = v.model_id
JOIN vehicle_types      vt  ON vt.vehicle_type_id  = v.vehicle_type_id
JOIN fuel_types         ft  ON ft.fuel_type_id     = v.fuel_type_id
JOIN citizens           c   ON c.citizen_id        = o.citizen_id
JOIN persons            p   ON p.person_id         = c.person_id
JOIN rto_offices        off ON off.office_id       = v.registering_office_id
WHERE o.effective_to IS NULL;

-- ------------------------------------------------------------
-- 2. vw_pending_applications
--    All applications not yet approved/rejected/completed.
--    Includes citizen name, service name, office, and SLA breach flag.
--    Used by: officer dashboards, SLA monitoring.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW vw_pending_applications AS
SELECT
    a.application_id,
    a.application_number,
    a.current_status,
    a.submitted_at,
    a.completed_at,
    a.remarks,
    -- SLA breach: submitted_at + sla_days < NOW()
    CASE
        WHEN a.submitted_at + INTERVAL st.sla_days DAY < NOW()
             AND a.current_status NOT IN ('APPROVED','COMPLETED','REJECTED','CANCELLED')
        THEN TRUE
        ELSE FALSE
    END                                         AS sla_breached,
    DATEDIFF(NOW(), a.submitted_at)             AS days_elapsed,
    st.sla_days,
    st.service_name,
    st.service_code,
    off.office_id,
    off.office_code,
    off.office_name,
    ap.applicant_id,
    c.citizen_id,
    c.citizen_code,
    p.first_name,
    p.last_name,
    p.phone_primary,
    p.email,
    a.assigned_officer_id
FROM applications a
JOIN service_types  st  ON st.service_type_id  = a.service_type_id
JOIN rto_offices    off ON off.office_id        = a.office_id
JOIN applicants     ap  ON ap.applicant_id      = a.applicant_id
JOIN citizens       c   ON c.citizen_id         = ap.citizen_id
JOIN persons        p   ON p.person_id          = c.person_id
WHERE a.current_status NOT IN ('APPROVED', 'COMPLETED', 'REJECTED', 'CANCELLED');

-- ------------------------------------------------------------
-- 3. vw_outstanding_challans
--    Challans in ISSUED status with the associated vehicle and
--    violation details. Used by: enforcement dashboards, challan payments.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW vw_outstanding_challans AS
SELECT
    ch.challan_id,
    ch.challan_number,
    ch.total_amount,
    ch.status                       AS challan_status,
    ch.issued_at,
    v.violation_id,
    v.location                      AS violation_location,
    v.occurred_at                   AS violation_occurred_at,
    vt.description                  AS violation_description,
    vt.base_fine_amount,
    vt.is_cognizable,
    veh.vehicle_id,
    veh.registration_number,
    veh.status                      AS vehicle_status,
    -- driver citizen (may be NULL if driver was unknown at citation time)
    v.driver_citizen_id,
    dc.citizen_code                 AS driver_citizen_code,
    dp.first_name                   AS driver_first_name,
    dp.last_name                    AS driver_last_name,
    dp.phone_primary                AS driver_phone,
    -- issuing officer
    e.employee_id                   AS officer_employee_id,
    e.employee_code                 AS officer_code,
    op.first_name                   AS officer_first_name,
    op.last_name                    AS officer_last_name
FROM challans ch
JOIN challan_violations cv  ON cv.challan_id        = ch.challan_id
JOIN violations         v   ON v.violation_id       = cv.violation_id
JOIN violation_types    vt  ON vt.violation_type_id = v.violation_type_id
JOIN vehicles           veh ON veh.vehicle_id       = v.vehicle_id
JOIN employees          e   ON e.employee_id        = v.officer_employee_id
JOIN persons            op  ON op.person_id         = e.person_id
LEFT JOIN citizens      dc  ON dc.citizen_id        = v.driver_citizen_id
LEFT JOIN persons       dp  ON dp.person_id         = dc.person_id
WHERE ch.status = 'ISSUED';

-- ------------------------------------------------------------
-- 4. vw_expiring_compliances
--    PUC certificates, fitness certificates, and insurance policies
--    expiring within the next 30 days (configurable in the query).
--    Used by: compliance alerts, batch notifications.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW vw_expiring_compliances AS
-- PUC certificates expiring within 30 days
SELECT
    'PUC'                           AS compliance_type,
    pc.puc_id                       AS record_id,
    pc.vehicle_id,
    veh.registration_number,
    pc.issue_date,
    pc.expiry_date,
    pc.status,
    DATEDIFF(pc.expiry_date, CURDATE()) AS days_to_expiry,
    own.citizen_id                  AS owner_citizen_id,
    cit.citizen_code                AS owner_citizen_code,
    per.first_name                  AS owner_first_name,
    per.last_name                   AS owner_last_name,
    per.phone_primary               AS owner_phone,
    per.email                       AS owner_email
FROM pollution_certificates pc
JOIN vehicles               veh ON veh.vehicle_id = pc.vehicle_id
LEFT JOIN vehicle_ownerships own ON own.vehicle_id = veh.vehicle_id AND own.effective_to IS NULL
LEFT JOIN citizens          cit ON cit.citizen_id  = own.citizen_id
LEFT JOIN persons           per ON per.person_id   = cit.person_id
WHERE pc.status = 'ACTIVE'
  AND pc.expiry_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY)

UNION ALL

-- Fitness certificates expiring within 30 days
SELECT
    'FITNESS'                       AS compliance_type,
    fc.certificate_id               AS record_id,
    fc.vehicle_id,
    veh.registration_number,
    fc.issue_date,
    fc.expiry_date,
    fc.status,
    DATEDIFF(fc.expiry_date, CURDATE()) AS days_to_expiry,
    own.citizen_id,
    cit.citizen_code,
    per.first_name,
    per.last_name,
    per.phone_primary,
    per.email
FROM fitness_certificates fc
JOIN vehicles              veh ON veh.vehicle_id = fc.vehicle_id
LEFT JOIN vehicle_ownerships own ON own.vehicle_id = veh.vehicle_id AND own.effective_to IS NULL
LEFT JOIN citizens         cit ON cit.citizen_id  = own.citizen_id
LEFT JOIN persons          per ON per.person_id   = cit.person_id
WHERE fc.status = 'ACTIVE'
  AND fc.expiry_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY)

UNION ALL

-- Insurance policies expiring within 30 days
SELECT
    'INSURANCE'                     AS compliance_type,
    ip.policy_id                    AS record_id,
    ip.vehicle_id,
    veh.registration_number,
    ip.start_date                   AS issue_date,
    ip.end_date                     AS expiry_date,
    'ACTIVE'                        AS status,             -- insurance has no status column
    DATEDIFF(ip.end_date, CURDATE()) AS days_to_expiry,
    own.citizen_id,
    cit.citizen_code,
    per.first_name,
    per.last_name,
    per.phone_primary,
    per.email
FROM insurance_policies ip
JOIN vehicles              veh ON veh.vehicle_id = ip.vehicle_id
LEFT JOIN vehicle_ownerships own ON own.vehicle_id = veh.vehicle_id AND own.effective_to IS NULL
LEFT JOIN citizens         cit ON cit.citizen_id  = own.citizen_id
LEFT JOIN persons          per ON per.person_id   = cit.person_id
WHERE ip.end_date BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 30 DAY);

-- ------------------------------------------------------------
-- 5. vw_active_driving_licences
--    All ACTIVE driving licences with citizen and class information.
--    Used by: licence verification, renewal dashboards.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW vw_active_driving_licences AS
SELECT
    dl.driving_licence_id,
    dl.licence_number,
    dl.issue_date,
    dl.expiry_date,
    dl.current_status,
    DATEDIFF(dl.expiry_date, CURDATE())    AS days_to_expiry,
    CASE
        WHEN dl.expiry_date < CURDATE() THEN TRUE
        ELSE FALSE
    END                                    AS is_expired,
    dl.citizen_id,
    c.citizen_code,
    p.first_name,
    p.last_name,
    p.phone_primary,
    p.email,
    p.date_of_birth,
    -- comma-separated list of licence classes held
    GROUP_CONCAT(lc.class_code ORDER BY lc.class_code SEPARATOR ', ')
                                           AS licence_classes,
    dl.office_id,
    off.office_code,
    off.office_name
FROM driving_licences dl
JOIN citizens          c   ON c.citizen_id         = dl.citizen_id
JOIN persons           p   ON p.person_id          = c.person_id
JOIN rto_offices       off ON off.office_id        = dl.office_id
LEFT JOIN licence_class_assignments lca
                            ON lca.driving_licence_id = dl.driving_licence_id
LEFT JOIN licence_classes lc ON lc.licence_class_id   = lca.licence_class_id
WHERE dl.current_status = 'ACTIVE'
GROUP BY
    dl.driving_licence_id, dl.licence_number, dl.issue_date, dl.expiry_date,
    dl.current_status, dl.citizen_id, c.citizen_code,
    p.first_name, p.last_name, p.phone_primary, p.email, p.date_of_birth,
    dl.office_id, off.office_code, off.office_name;

-- ------------------------------------------------------------
-- 6. vw_application_status_summary
--    Count of applications per office × status.
--    Used by: management dashboard, KPI reports.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW vw_application_status_summary AS
SELECT
    off.office_id,
    off.office_code,
    off.office_name,
    a.current_status,
    COUNT(a.application_id)                AS application_count,
    MIN(a.submitted_at)                    AS oldest_submission,
    MAX(a.submitted_at)                    AS newest_submission,
    AVG(DATEDIFF(NOW(), a.submitted_at))   AS avg_age_days
FROM applications   a
JOIN rto_offices    off ON off.office_id = a.office_id
GROUP BY off.office_id, off.office_code, off.office_name, a.current_status;

-- ============================================================
-- Verification queries (run manually after loading seed data).
-- ============================================================
-- SELECT * FROM vw_current_vehicle_owners LIMIT 5;
-- SELECT * FROM vw_pending_applications   LIMIT 5;
-- SELECT * FROM vw_outstanding_challans   LIMIT 5;
-- SELECT * FROM vw_expiring_compliances   LIMIT 5;
-- SELECT * FROM vw_active_driving_licences LIMIT 5;
-- SELECT * FROM vw_application_status_summary;
