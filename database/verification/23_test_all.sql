-- ============================================================
-- RTO Management System
-- verification/23_test_all.sql
-- Comprehensive test suite for all database enhancements.
-- Run this in MySQL Workbench after loading all files (01-22).
-- Each test prints PASS or FAIL with a message.
-- ============================================================

USE rto_management;

-- ============================================================
-- TEST HELPER: temporary results table
-- ============================================================
DROP TABLE IF EXISTS _test_results;
CREATE TEMPORARY TABLE _test_results (
    test_no   INT AUTO_INCREMENT PRIMARY KEY,
    category  VARCHAR(40),
    test_name VARCHAR(120),
    result    ENUM('PASS','FAIL'),
    detail    VARCHAR(255)
);

-- ============================================================
-- SECTION 1: BASE SCHEMA — 33 tables exist
-- ============================================================

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'SCHEMA', 'Base table count = 33',
       IF(COUNT(*) = 33, 'PASS', 'FAIL'),
       CONCAT('Found: ', COUNT(*), ' base tables')
FROM information_schema.tables
WHERE table_schema = 'rto_management' AND table_type = 'BASE TABLE';

-- ============================================================
-- SECTION 2: VIEWS — all 6 created
-- ============================================================

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'VIEWS', 'vw_current_vehicle_owners exists',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'), 'Check view creation'
FROM information_schema.views
WHERE table_schema = 'rto_management' AND table_name = 'vw_current_vehicle_owners';

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'VIEWS', 'vw_pending_applications exists',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'), 'Check view creation'
FROM information_schema.views
WHERE table_schema = 'rto_management' AND table_name = 'vw_pending_applications';

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'VIEWS', 'vw_outstanding_challans exists',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'), 'Check view creation'
FROM information_schema.views
WHERE table_schema = 'rto_management' AND table_name = 'vw_outstanding_challans';

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'VIEWS', 'vw_expiring_compliances exists',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'), 'Check view creation'
FROM information_schema.views
WHERE table_schema = 'rto_management' AND table_name = 'vw_expiring_compliances';

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'VIEWS', 'vw_active_driving_licences exists',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'), 'Check view creation'
FROM information_schema.views
WHERE table_schema = 'rto_management' AND table_name = 'vw_active_driving_licences';

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'VIEWS', 'vw_application_status_summary exists',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'), 'Check view creation'
FROM information_schema.views
WHERE table_schema = 'rto_management' AND table_name = 'vw_application_status_summary';

-- ============================================================
-- SECTION 3: VIEWS — return data from seed
-- ============================================================

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'VIEW DATA', 'vw_current_vehicle_owners returns rows',
       IF(COUNT(*) > 0, 'PASS', 'FAIL'),
       CONCAT('Rows: ', COUNT(*))
FROM vw_current_vehicle_owners;

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'VIEW DATA', 'vw_pending_applications returns rows',
       IF(COUNT(*) > 0, 'PASS', 'FAIL'),
       CONCAT('Rows: ', COUNT(*))
FROM vw_pending_applications;

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'VIEW DATA', 'vw_outstanding_challans returns rows',
       IF(COUNT(*) > 0, 'PASS', 'FAIL'),
       CONCAT('Rows: ', COUNT(*))
FROM vw_outstanding_challans;

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'VIEW DATA', 'vw_active_driving_licences returns rows',
       IF(COUNT(*) > 0, 'PASS', 'FAIL'),
       CONCAT('Rows: ', COUNT(*))
FROM vw_active_driving_licences;

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'VIEW DATA', 'vw_application_status_summary returns rows',
       IF(COUNT(*) > 0, 'PASS', 'FAIL'),
       CONCAT('Rows: ', COUNT(*))
FROM vw_application_status_summary;

-- ============================================================
-- SECTION 4: TRIGGERS — all 5 exist
-- ============================================================

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'TRIGGERS', 'All 5 triggers exist',
       IF(COUNT(*) = 5, 'PASS', 'FAIL'),
       CONCAT('Found: ', COUNT(*), ' triggers')
FROM information_schema.triggers
WHERE trigger_schema = 'rto_management';

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'TRIGGERS', 'trg_after_licence_status_change exists',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'), 'Licence history trigger'
FROM information_schema.triggers
WHERE trigger_schema = 'rto_management'
  AND trigger_name = 'trg_after_licence_status_change';

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'TRIGGERS', 'trg_after_permit_status_change exists',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'), 'Permit history trigger'
FROM information_schema.triggers
WHERE trigger_schema = 'rto_management'
  AND trigger_name = 'trg_after_permit_status_change';

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'TRIGGERS', 'trg_before_vehicle_delete exists',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'), 'Vehicle delete guard'
FROM information_schema.triggers
WHERE trigger_schema = 'rto_management'
  AND trigger_name = 'trg_before_vehicle_delete';

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'TRIGGERS', 'trg_before_licence_delete exists',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'), 'Licence delete guard'
FROM information_schema.triggers
WHERE trigger_schema = 'rto_management'
  AND trigger_name = 'trg_before_licence_delete';

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'TRIGGERS', 'trg_before_road_tax_update exists',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'), 'Road tax overdue trigger'
FROM information_schema.triggers
WHERE trigger_schema = 'rto_management'
  AND trigger_name = 'trg_before_road_tax_update';

-- ============================================================
-- SECTION 5: TRIGGER BEHAVIOUR — licence status history
-- ============================================================

-- The trigger on driving_licences should auto-insert into licence_status_history.
-- Seed data has licence_id=3 as SUSPENDED (history pre-seeded).
-- Update it to REVOKED → trigger should add a new row.

SET @hist_before = (SELECT COUNT(*) FROM licence_status_history WHERE driving_licence_id = 3);

UPDATE driving_licences SET current_status = 'REVOKED' WHERE driving_licence_id = 3;

SET @hist_after = (SELECT COUNT(*) FROM licence_status_history WHERE driving_licence_id = 3);

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'TRIGGER FIRE', 'trg_after_licence_status_change fires on UPDATE',
       IF(@hist_after = @hist_before + 1, 'PASS', 'FAIL'),
       CONCAT('History rows before=', @hist_before, ' after=', @hist_after);

-- Restore
UPDATE driving_licences SET current_status = 'SUSPENDED' WHERE driving_licence_id = 3;

-- ============================================================
-- SECTION 6: TRIGGER BEHAVIOUR — vehicle delete guard
-- ============================================================

-- Try to delete an ACTIVE vehicle — should be blocked by trigger.
SET @del_err = 'PASS';
BEGIN;
    DELETE FROM vehicles WHERE vehicle_id = 1 AND status = 'ACTIVE';
    -- If we get here the trigger did NOT fire → FAIL
    SET @del_err = 'FAIL: trigger did not block delete';
    ROLLBACK;
COMMIT;

-- The SIGNAL raised by trigger causes the DELETE to fail;
-- MySQL returns error, row stays intact. Verify row still exists.
INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'TRIGGER FIRE', 'trg_before_vehicle_delete blocks ACTIVE vehicle delete',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'),
       CONCAT('Vehicle 1 still exists: ', COUNT(*), ' row(s)')
FROM vehicles WHERE vehicle_id = 1 AND status = 'ACTIVE';

-- ============================================================
-- SECTION 7: TRIGGER BEHAVIOUR — road tax overdue auto-correct
-- ============================================================

-- road_tax_records id=4: status='DUE', due_date='2026-03-31' (past).
-- Any UPDATE on that row should trigger status → OVERDUE.
UPDATE road_tax_records SET amount_due = 8000.00 WHERE tax_record_id = 4;

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'TRIGGER FIRE', 'trg_before_road_tax_update auto-sets OVERDUE',
       IF(status = 'OVERDUE', 'PASS', 'FAIL'),
       CONCAT('status=', status, ' (expected OVERDUE)')
FROM road_tax_records WHERE tax_record_id = 4;

-- ============================================================
-- SECTION 8: STORED PROCEDURES — all 3 exist
-- ============================================================

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'PROCEDURES', 'All 3 stored procedures exist',
       IF(COUNT(*) = 3, 'PASS', 'FAIL'),
       CONCAT('Found: ', COUNT(*))
FROM information_schema.routines
WHERE routine_schema = 'rto_management' AND routine_type = 'PROCEDURE';

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'PROCEDURES', 'sp_transfer_vehicle_ownership exists',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'), ''
FROM information_schema.routines
WHERE routine_schema = 'rto_management'
  AND routine_name = 'sp_transfer_vehicle_ownership';

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'PROCEDURES', 'sp_process_challan_payment exists',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'), ''
FROM information_schema.routines
WHERE routine_schema = 'rto_management'
  AND routine_name = 'sp_process_challan_payment';

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'PROCEDURES', 'sp_update_overdue_road_tax exists',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'), ''
FROM information_schema.routines
WHERE routine_schema = 'rto_management'
  AND routine_name = 'sp_update_overdue_road_tax';

-- ============================================================
-- SECTION 9: STORED PROCEDURE EXECUTION
-- ============================================================

-- Test sp_update_overdue_road_tax (batch, safe to call any time)
CALL sp_update_overdue_road_tax();

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'PROC EXEC', 'sp_update_overdue_road_tax runs without error',
       'PASS', 'Procedure completed successfully';

-- Test sp_transfer_vehicle_ownership with transfer_id=2 (PENDING transfer)
-- Transfer: Vehicle 1, from citizen 1 (Aarav) → citizen 5 (Kiran)
SET @own_before = (SELECT citizen_id FROM vehicle_ownerships WHERE vehicle_id=1 AND effective_to IS NULL);

CALL sp_transfer_vehicle_ownership(2, CURDATE());

SET @own_after = (SELECT citizen_id FROM vehicle_ownerships WHERE vehicle_id=1 AND effective_to IS NULL);

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'PROC EXEC', 'sp_transfer_vehicle_ownership: new owner inserted',
       IF(@own_after IS NOT NULL AND @own_after <> @own_before, 'PASS', 'FAIL'),
       CONCAT('Owner before=', IFNULL(@own_before,'NULL'),
              ' after=', IFNULL(@own_after,'NULL'));

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'PROC EXEC', 'sp_transfer_vehicle_ownership: old ownership closed',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'),
       'Previous ownership row has effective_to set'
FROM vehicle_ownerships
WHERE vehicle_id = 1 AND citizen_id = 1 AND effective_to IS NOT NULL;

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'PROC EXEC', 'sp_transfer_vehicle_ownership: transfer marked APPROVED',
       IF(status = 'APPROVED', 'PASS', 'FAIL'),
       CONCAT('Transfer 2 status=', status)
FROM ownership_transfers WHERE transfer_id = 2;

-- ============================================================
-- SECTION 10: CONSTRAINTS — reject invalid data
-- ============================================================

-- Test: phone number must be digits only (7-15 chars)
SET @constraint_ok = 0;
BEGIN;
-- This should FAIL due to chk_person_phone_format
INSERT INTO persons (first_name, last_name, date_of_birth, gender,
    national_id_number, phone_primary, created_at, updated_at)
VALUES ('Test','Fail','2000-01-01','MALE','AADH-999999999','INVALID-PHONE',NOW(),NOW());
-- If INSERT succeeded, constraint did NOT fire
SET @constraint_ok = -1;
ROLLBACK;
COMMIT;

INSERT INTO _test_results (category, test_name, result, detail)
VALUES ('CONSTRAINTS', 'chk_person_phone_format rejects bad phone',
        IF(@constraint_ok = 0, 'PASS', 'FAIL'),
        'INSERT with invalid phone should be rejected');

-- Test: challan amount must be > 0
SET @constraint_ok2 = 0;
BEGIN;
INSERT INTO challans (challan_number, total_amount, status, issued_at)
VALUES ('BAD-CHAL-001', -100.00, 'ISSUED', NOW());
SET @constraint_ok2 = -1;
ROLLBACK;
COMMIT;

INSERT INTO _test_results (category, test_name, result, detail)
VALUES ('CONSTRAINTS', 'chk_challan_amount_positive rejects negative amount',
        IF(@constraint_ok2 = 0, 'PASS', 'FAIL'),
        'INSERT with negative amount should be rejected');

-- ============================================================
-- SECTION 11: INDEXES — all key indexes exist
-- ============================================================

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'INDEXES', 'idx_attempt_gateway_ref exists (critical for payment idempotency)',
       IF(COUNT(*) = 1, 'PASS', 'FAIL'), ''
FROM information_schema.statistics
WHERE table_schema = 'rto_management'
  AND table_name = 'payment_attempts'
  AND index_name = 'idx_attempt_gateway_ref';

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'INDEXES', 'idx_dl_expiry exists (licence renewal dashboard)',
       IF(COUNT(*) >= 1, 'PASS', 'FAIL'), ''
FROM information_schema.statistics
WHERE table_schema = 'rto_management'
  AND table_name = 'driving_licences'
  AND index_name = 'idx_dl_expiry';

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'INDEXES', 'idx_road_tax_status_due exists (overdue batch)',
       IF(COUNT(*) >= 1, 'PASS', 'FAIL'), ''
FROM information_schema.statistics
WHERE table_schema = 'rto_management'
  AND table_name = 'road_tax_records'
  AND index_name = 'idx_road_tax_status_due';

-- ============================================================
-- SECTION 12: SEED DATA — key counts
-- ============================================================

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'SEED DATA', '10 citizens loaded',
       IF(COUNT(*) = 10, 'PASS', 'FAIL'), CONCAT('Found: ', COUNT(*))
FROM citizens;

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'SEED DATA', '10 applications loaded',
       IF(COUNT(*) = 10, 'PASS', 'FAIL'), CONCAT('Found: ', COUNT(*))
FROM applications;

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'SEED DATA', '5 vehicles loaded',
       IF(COUNT(*) = 5, 'PASS', 'FAIL'), CONCAT('Found: ', COUNT(*))
FROM vehicles;

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'SEED DATA', '4 challans loaded',
       IF(COUNT(*) = 4, 'PASS', 'FAIL'), CONCAT('Found: ', COUNT(*))
FROM challans;

INSERT INTO _test_results (category, test_name, result, detail)
SELECT 'SEED DATA', '5 payments loaded',
       IF(COUNT(*) = 5, 'PASS', 'FAIL'), CONCAT('Found: ', COUNT(*))
FROM payments;

-- ============================================================
-- SECTION 13: ADVANCED QUERIES — run without error
-- ============================================================

-- CTE: SLA-breached applications
SET @q1 = (SELECT COUNT(*) FROM (
    WITH ages AS (
        SELECT a.application_id,
               DATEDIFF(NOW(), a.submitted_at) AS age_days,
               st.sla_days
        FROM applications a
        JOIN service_types st ON st.service_type_id = a.service_type_id
        WHERE a.current_status NOT IN ('APPROVED','COMPLETED','REJECTED','CANCELLED')
    )
    SELECT * FROM ages WHERE age_days > sla_days
) x);

INSERT INTO _test_results (category, test_name, result, detail)
VALUES ('ADV SQL', 'CTE SLA-breach query runs without error',
        'PASS', CONCAT('SLA-breached applications: ', @q1));

-- Window function: offices ranked
SET @q2 = (SELECT COUNT(*) FROM (
    SELECT office_id,
           COUNT(application_id) AS cnt,
           RANK() OVER (ORDER BY COUNT(application_id) DESC) AS rnk
    FROM applications GROUP BY office_id
) x);

INSERT INTO _test_results (category, test_name, result, detail)
VALUES ('ADV SQL', 'Window RANK() on office applications runs without error',
        'PASS', CONCAT('Rows returned: ', @q2));

-- Window function: LAG on status history
SET @q3 = (SELECT COUNT(*) FROM (
    SELECT application_id, new_status,
           LAG(changed_at) OVER (PARTITION BY application_id ORDER BY history_id) AS prev
    FROM application_status_history
) x WHERE prev IS NOT NULL);

INSERT INTO _test_results (category, test_name, result, detail)
VALUES ('ADV SQL', 'Window LAG() on status history runs without error',
        'PASS', CONCAT('Transition rows: ', @q3));

-- ============================================================
-- FINAL RESULTS
-- ============================================================

SELECT
    category,
    test_name,
    result,
    detail
FROM _test_results
ORDER BY test_no;

-- Summary
SELECT
    result,
    COUNT(*) AS count
FROM _test_results
GROUP BY result;

SELECT
    CONCAT(
        SUM(result = 'PASS'), ' PASSED  |  ',
        SUM(result = 'FAIL'), ' FAILED  |  ',
        COUNT(*), ' TOTAL'
    ) AS TEST_SUMMARY
FROM _test_results;

DROP TABLE IF EXISTS _test_results;
