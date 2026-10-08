-- ============================================================
-- RTO Management System
-- queries/21_advanced_queries.sql
-- Advanced SQL demonstrations using the actual RTO schema.
--
-- Every query uses real table/column names from files 02–14 and
-- is meaningful in the RTO operational context.
-- Run after loading seed data (file 22).
--
-- Contents
-- --------
--  Q1.  INNER JOIN  — Full application report
--  Q2.  LEFT JOIN   — Vehicles without current insurance
--  Q3.  SELF JOIN   — Employees at the same seniority level
--  Q4.  GROUP BY + HAVING — Offices with overloaded workload
--  Q5.  CTE (non-recursive) — SLA-breached applications
--  Q6.  CTE (multi-step)   — Citizen violation profile
--  Q7.  Window RANK()      — Offices ranked by completed applications
--  Q8.  Window ROW_NUMBER() — Latest driving test per citizen
--  Q9.  Window LAG()        — Time between application status changes
-- Q10.  Window RANK() on violations — Top offending vehicle types
-- ============================================================

USE rto_management;

-- ============================================================
-- Q1. INNER JOIN — Complete Application Report
-- ============================================================
-- Joins applications → applicants → citizens → persons →
-- service_types → rto_offices → employees (officer).
-- Produces a flat report row per application with all human-
-- readable labels. Used in management PDF exports.
-- ============================================================
SELECT
    a.application_id,
    a.application_number,
    a.current_status,
    a.submitted_at,
    a.completed_at,
    DATEDIFF(COALESCE(a.completed_at, NOW()), a.submitted_at) AS processing_days,
    st.service_name,
    st.service_code,
    st.sla_days,
    off.office_code,
    off.office_name,
    c.citizen_code,
    CONCAT(p.first_name, ' ', p.last_name)  AS citizen_name,
    p.phone_primary                         AS citizen_phone,
    p.email                                 AS citizen_email,
    CONCAT(ep.first_name, ' ', ep.last_name) AS officer_name,
    emp.employee_code                        AS officer_code
FROM applications        a
INNER JOIN service_types st  ON st.service_type_id  = a.service_type_id
INNER JOIN rto_offices   off ON off.office_id        = a.office_id
INNER JOIN applicants    ap  ON ap.applicant_id      = a.applicant_id
INNER JOIN citizens      c   ON c.citizen_id         = ap.citizen_id
INNER JOIN persons       p   ON p.person_id          = c.person_id
-- Officer may be NULL (application not yet assigned)
LEFT  JOIN employees     emp ON emp.employee_id      = a.assigned_officer_id
LEFT  JOIN persons       ep  ON ep.person_id         = emp.person_id
ORDER BY a.submitted_at DESC;


-- ============================================================
-- Q2. LEFT JOIN — Vehicles Without Current Insurance
-- ============================================================
-- All active vehicles LEFT JOINed to their latest insurance policy.
-- Rows where policy_id IS NULL represent uninsured vehicles —
-- a compliance enforcement priority.
-- ============================================================
SELECT
    v.vehicle_id,
    v.registration_number,
    v.status                AS vehicle_status,
    v.registration_date,
    vm.name                 AS manufacturer,
    vmo.model_name,
    CASE
        WHEN ip.policy_id IS NULL THEN 'UNINSURED'
        WHEN ip.end_date < CURDATE() THEN 'EXPIRED'
        ELSE 'INSURED'
    END                     AS insurance_status,
    ip.policy_number,
    ip.provider_name,
    ip.end_date             AS insurance_end_date,
    -- Current owner info
    own.citizen_id          AS owner_citizen_id,
    cit.citizen_code        AS owner_citizen_code,
    CONCAT(per.first_name, ' ', per.last_name) AS owner_name,
    per.phone_primary       AS owner_phone
FROM vehicles              v
JOIN vehicle_manufacturers vm  ON vm.manufacturer_id = v.manufacturer_id
JOIN vehicle_models        vmo ON vmo.model_id       = v.model_id
-- Latest insurance policy per vehicle (outer join — includes vehicles with no policy)
LEFT JOIN (
    SELECT ip1.*
    FROM insurance_policies ip1
    WHERE ip1.end_date = (
        SELECT MAX(ip2.end_date)
        FROM   insurance_policies ip2
        WHERE  ip2.vehicle_id = ip1.vehicle_id
    )
) ip ON ip.vehicle_id = v.vehicle_id
-- Current owner (outer join — vehicles may have no ownership row yet)
LEFT JOIN vehicle_ownerships own ON own.vehicle_id  = v.vehicle_id AND own.effective_to IS NULL
LEFT JOIN citizens           cit ON cit.citizen_id  = own.citizen_id
LEFT JOIN persons            per ON per.person_id   = cit.person_id
WHERE v.status = 'ACTIVE'
ORDER BY insurance_status, v.registration_number;


-- ============================================================
-- Q3. SELF JOIN — Employees at the Same Designation Level
-- ============================================================
-- Finds pairs of employees who share the same designation
-- (rank_level). Useful for staffing balance analysis.
-- Self-join on employees using designation_id as the join key.
-- ============================================================
SELECT
    e1.employee_id   AS employee1_id,
    e1.employee_code AS employee1_code,
    CONCAT(p1.first_name, ' ', p1.last_name) AS employee1_name,
    e2.employee_id   AS employee2_id,
    e2.employee_code AS employee2_code,
    CONCAT(p2.first_name, ' ', p2.last_name) AS employee2_name,
    d.title          AS shared_designation,
    d.rank_level
FROM employees  e1
JOIN employees  e2  ON  e2.designation_id = e1.designation_id
                    AND e2.employee_id     > e1.employee_id   -- avoid A-B and B-A duplicates
JOIN designations d ON d.designation_id   = e1.designation_id
JOIN persons     p1 ON p1.person_id       = e1.person_id
JOIN persons     p2 ON p2.person_id       = e2.person_id
WHERE e1.is_active = TRUE AND e2.is_active = TRUE
ORDER BY d.rank_level, e1.employee_id;


-- ============================================================
-- Q4. GROUP BY + HAVING — Offices With Overloaded Workload
-- ============================================================
-- Offices where the number of PENDING/UNDER_VERIFICATION applications
-- exceeds 3 (threshold easily adjustable).
-- Used by management to decide re-allocation of staff.
-- ============================================================
SELECT
    off.office_id,
    off.office_code,
    off.office_name,
    COUNT(a.application_id)                        AS open_applications,
    SUM(CASE WHEN a.current_status = 'SUBMITTED'          THEN 1 ELSE 0 END) AS submitted_count,
    SUM(CASE WHEN a.current_status = 'DOCS_PENDING'       THEN 1 ELSE 0 END) AS docs_pending_count,
    SUM(CASE WHEN a.current_status = 'UNDER_VERIFICATION' THEN 1 ELSE 0 END) AS verification_count,
    SUM(CASE WHEN a.current_status = 'AWAITING_PAYMENT'   THEN 1 ELSE 0 END) AS payment_pending_count,
    ROUND(AVG(DATEDIFF(NOW(), a.submitted_at)), 1)  AS avg_age_days
FROM applications   a
JOIN rto_offices    off ON off.office_id = a.office_id
WHERE a.current_status NOT IN ('APPROVED', 'COMPLETED', 'REJECTED', 'CANCELLED')
GROUP BY off.office_id, off.office_code, off.office_name
HAVING COUNT(a.application_id) > 3         -- overload threshold
ORDER BY open_applications DESC;


-- ============================================================
-- Q5. CTE (non-recursive) — Applications That Have Breached SLA
-- ============================================================
-- Uses a CTE to first compute per-application age data, then
-- filters for those that exceed their service type's sla_days.
-- ============================================================
WITH application_ages AS (
    SELECT
        a.application_id,
        a.application_number,
        a.current_status,
        a.submitted_at,
        st.service_name,
        st.service_code,
        st.sla_days,
        DATEDIFF(NOW(), a.submitted_at) AS age_days,
        off.office_code,
        off.office_name,
        c.citizen_code,
        CONCAT(p.first_name, ' ', p.last_name) AS citizen_name
    FROM applications   a
    JOIN service_types  st  ON st.service_type_id = a.service_type_id
    JOIN rto_offices    off ON off.office_id       = a.office_id
    JOIN applicants     ap  ON ap.applicant_id     = a.applicant_id
    JOIN citizens       c   ON c.citizen_id        = ap.citizen_id
    JOIN persons        p   ON p.person_id         = c.person_id
    WHERE a.current_status NOT IN ('APPROVED','COMPLETED','REJECTED','CANCELLED')
)
SELECT
    application_id,
    application_number,
    current_status,
    submitted_at,
    service_name,
    service_code,
    sla_days,
    age_days,
    age_days - sla_days             AS overdue_by_days,
    office_code,
    office_name,
    citizen_code,
    citizen_name
FROM application_ages
WHERE age_days > sla_days
ORDER BY overdue_by_days DESC;


-- ============================================================
-- Q6. CTE (multi-step) — Citizen Violation Profile
-- ============================================================
-- Step 1: count total violations per citizen.
-- Step 2: sum outstanding challan amounts per citizen.
-- Step 3: join to produce a ranked violation profile.
-- ============================================================
WITH citizen_violations AS (
    SELECT
        v.driver_citizen_id             AS citizen_id,
        COUNT(v.violation_id)           AS total_violations,
        COUNT(DISTINCT v.violation_type_id) AS unique_violation_types
    FROM violations v
    WHERE v.driver_citizen_id IS NOT NULL
    GROUP BY v.driver_citizen_id
),
citizen_outstanding AS (
    SELECT
        v.driver_citizen_id             AS citizen_id,
        SUM(ch.total_amount)            AS outstanding_fine_total
    FROM violations         v
    JOIN challan_violations  cv ON cv.violation_id = v.violation_id
    JOIN challans            ch ON ch.challan_id   = cv.challan_id
    WHERE v.driver_citizen_id IS NOT NULL
      AND ch.status = 'ISSUED'
    GROUP BY v.driver_citizen_id
)
SELECT
    c.citizen_id,
    c.citizen_code,
    CONCAT(p.first_name, ' ', p.last_name) AS citizen_name,
    p.phone_primary,
    cv.total_violations,
    cv.unique_violation_types,
    COALESCE(co.outstanding_fine_total, 0.00) AS outstanding_fine_total,
    c.blacklisted
FROM citizen_violations cv
JOIN citizens           c   ON c.citizen_id  = cv.citizen_id
JOIN persons            p   ON p.person_id   = c.person_id
LEFT JOIN citizen_outstanding co ON co.citizen_id = cv.citizen_id
ORDER BY cv.total_violations DESC, co.outstanding_fine_total DESC;


-- ============================================================
-- Q7. Window RANK() — Offices Ranked by Completed Applications
-- ============================================================
-- RANK() assigns the same rank to ties, skipping the next rank.
-- Useful for performance league tables.
-- ============================================================
SELECT
    office_id,
    office_code,
    office_name,
    completed_count,
    RANK() OVER (ORDER BY completed_count DESC)         AS performance_rank,
    DENSE_RANK() OVER (ORDER BY completed_count DESC)   AS performance_dense_rank,
    total_applications,
    ROUND(100.0 * completed_count / NULLIF(total_applications, 0), 1) AS completion_pct
FROM (
    SELECT
        off.office_id,
        off.office_code,
        off.office_name,
        COUNT(a.application_id)                                             AS total_applications,
        SUM(CASE WHEN a.current_status = 'COMPLETED' THEN 1 ELSE 0 END)    AS completed_count
    FROM rto_offices    off
    LEFT JOIN applications a ON a.office_id = off.office_id
    WHERE off.is_active = TRUE
    GROUP BY off.office_id, off.office_code, off.office_name
) AS office_stats
ORDER BY performance_rank;


-- ============================================================
-- Q8. Window ROW_NUMBER() — Latest Driving Test Per Citizen
-- ============================================================
-- For each citizen, find the most recent driving test result.
-- ROW_NUMBER() partitioned by citizen, ordered by scheduled_at DESC.
-- The outer query keeps only rn = 1 (the most recent row).
-- ============================================================
SELECT
    citizen_id,
    citizen_code,
    citizen_name,
    test_id,
    scheduled_at,
    result          AS latest_test_result,
    test_centre_name
FROM (
    SELECT
        dt.test_id,
        dt.citizen_id,
        c.citizen_code,
        CONCAT(p.first_name, ' ', p.last_name)  AS citizen_name,
        dt.scheduled_at,
        dt.result,
        tc.centre_name                           AS test_centre_name,
        ROW_NUMBER() OVER (
            PARTITION BY dt.citizen_id
            ORDER BY dt.scheduled_at DESC
        )                                        AS rn
    FROM driving_tests  dt
    JOIN citizens       c   ON c.citizen_id        = dt.citizen_id
    JOIN persons        p   ON p.person_id         = c.person_id
    JOIN test_centres   tc  ON tc.test_centre_id   = dt.test_centre_id
) ranked_tests
WHERE rn = 1
ORDER BY citizen_id;


-- ============================================================
-- Q9. Window LAG() — Time Between Application Status Changes
-- ============================================================
-- Uses LAG() to compute the duration (in hours) each application
-- spent in each status before transitioning to the next one.
-- Reveals bottleneck statuses in the workflow.
-- ============================================================
SELECT
    application_id,
    application_number,
    new_status,
    changed_at,
    previous_changed_at,
    ROUND(
        TIMESTAMPDIFF(MINUTE, previous_changed_at, changed_at) / 60.0,
        2
    )                              AS hours_in_previous_status,
    ROUND(
        TIMESTAMPDIFF(MINUTE, previous_changed_at, changed_at) / 1440.0,
        2
    )                              AS days_in_previous_status
FROM (
    SELECT
        ash.application_id,
        a.application_number,
        ash.new_status,
        ash.changed_at,
        LAG(ash.changed_at) OVER (
            PARTITION BY ash.application_id
            ORDER BY ash.history_id
        )                          AS previous_changed_at
    FROM application_status_history ash
    JOIN applications               a ON a.application_id = ash.application_id
) lag_data
WHERE previous_changed_at IS NOT NULL          -- skip the first SUBMITTED row
ORDER BY application_id, changed_at;


-- ============================================================
-- Q10. Window RANK() — Top Offending Vehicle Types by Violations
-- ============================================================
-- Ranks vehicle types by the total number of violations issued.
-- Helps enforcement planning (e.g., focus on two-wheelers).
-- ============================================================
SELECT
    vt.vehicle_type_id,
    vt.type_name         AS vehicle_type,
    vt.is_commercial,
    violation_count,
    outstanding_fine_total,
    RANK()       OVER (ORDER BY violation_count DESC)         AS violation_rank,
    DENSE_RANK() OVER (ORDER BY outstanding_fine_total DESC)  AS fine_rank
FROM (
    SELECT
        v_veh.vehicle_type_id,
        COUNT(viol.violation_id)                           AS violation_count,
        COALESCE(SUM(ch.total_amount), 0)                  AS outstanding_fine_total
    FROM violations         viol
    JOIN vehicles           v_veh ON v_veh.vehicle_id    = viol.vehicle_id
    LEFT JOIN challan_violations cv ON cv.violation_id   = viol.violation_id
    LEFT JOIN challans          ch  ON ch.challan_id     = cv.challan_id
                                    AND ch.status = 'ISSUED'
    GROUP BY v_veh.vehicle_type_id
) agg
JOIN vehicle_types vt ON vt.vehicle_type_id = agg.vehicle_type_id
ORDER BY violation_rank;
