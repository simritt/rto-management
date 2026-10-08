-- ============================================================
-- RTO Management System
-- triggers/18_triggers.sql
-- Database-side triggers for automatic audit and protection.
--
-- ╔══════════════════════════════════════════════════════════╗
-- ║  CRITICAL BACKEND COMPATIBILITY ANALYSIS                 ║
-- ║                                                          ║
-- ║  Before each trigger, the backend code is explicitly     ║
-- ║  documented to confirm there is NO double-write risk.    ║
-- ╚══════════════════════════════════════════════════════════╝
--
-- ✗ DELIBERATELY NOT IMPLEMENTED (would cause double-writes):
--   • application_status_history trigger
--     → ApplicationService.applyStatus() already inserts into
--       application_status_history directly (Java, line ~100).
--   • challan_status_history trigger
--     → ViolationService.applyStatus() inserts into
--       challan_status_history directly in Java.
--   • audit_logs trigger
--     → Audit.record() in every service writes to audit_logs.
--
-- ✓ SAFE TO IMPLEMENT:
--   1. trg_after_licence_status_change
--      → LicenceService.java does NOT write to licence_status_history.
--        The table exists in the schema but the backend never populates it.
--   2. trg_after_permit_status_change
--      → PermitService.java does NOT write to permit_status_history.
--        Same situation — table exists, backend skips it.
--   3. trg_before_vehicle_delete
--      → Hard guard: prevents deletion of ACTIVE vehicles from any
--        MySQL client. Backend uses db.lock/get, not direct DELETE.
--   4. trg_before_licence_delete
--      → Hard guard: prevents deletion of ACTIVE driving licences.
--   5. trg_before_road_tax_update
--      → Auto-upgrades DUE → OVERDUE when due_date has passed,
--        catching any UPDATE that would leave status incorrectly as DUE.
--
-- Depends on: 08 (driving_licences, licence_status_history),
--             09 (vehicles),
--             10 (road_tax_records),
--             11 (permits, permit_status_history).
-- ============================================================

USE rto_management;

-- MySQL requires DELIMITER change for multi-statement trigger bodies.
DELIMITER $$

-- ============================================================
-- TRIGGER 1: trg_after_licence_status_change
-- Fires: AFTER UPDATE on driving_licences
-- Purpose: Automatically append a row to licence_status_history
--          whenever current_status changes. This is the DB-enforced
--          audit trail for licence status transitions.
-- Compatibility: LicenceService.java inspects / updates
--   driving_licences.current_status but does NOT write to
--   licence_status_history. Zero double-write risk.
-- ============================================================
CREATE TRIGGER trg_after_licence_status_change
AFTER UPDATE ON driving_licences
FOR EACH ROW
BEGIN
    -- Only record when the status actually changed.
    IF NEW.current_status <> OLD.current_status THEN
        INSERT INTO licence_status_history
            (driving_licence_id, previous_status, new_status, changed_at, reason)
        VALUES
            (NEW.driving_licence_id, OLD.current_status, NEW.current_status,
             CURRENT_TIMESTAMP,
             CONCAT('Status changed from ', OLD.current_status,
                    ' to ', NEW.current_status, ' (db trigger)'));
    END IF;
END$$

-- ============================================================
-- TRIGGER 2: trg_after_permit_status_change
-- Fires: AFTER UPDATE on permits
-- Purpose: Automatically append a row to permit_status_history
--          whenever status changes.
-- Compatibility: PermitService.java updates permits.status but does
--   NOT write to permit_status_history. Zero double-write risk.
-- ============================================================
CREATE TRIGGER trg_after_permit_status_change
AFTER UPDATE ON permits
FOR EACH ROW
BEGIN
    IF NEW.status <> OLD.status THEN
        INSERT INTO permit_status_history
            (permit_id, previous_status, new_status, changed_at, reason)
        VALUES
            (NEW.permit_id, OLD.status, NEW.status,
             CURRENT_TIMESTAMP,
             CONCAT('Status changed from ', OLD.status,
                    ' to ', NEW.status, ' (db trigger)'));
    END IF;
END$$

-- ============================================================
-- TRIGGER 3: trg_before_vehicle_delete
-- Fires: BEFORE DELETE on vehicles
-- Purpose: Hard-prevent deletion of an ACTIVE vehicle from any SQL
--          client, migration script, or administrative tool.
--          The backend itself never hard-deletes vehicles, but this
--          trigger acts as a last-resort safety net.
-- Note: Blacklisted, Scrapped, and Deregistered vehicles CAN be
--       deleted by a privileged admin.
-- ============================================================
CREATE TRIGGER trg_before_vehicle_delete
BEFORE DELETE ON vehicles
FOR EACH ROW
BEGIN
    IF OLD.status = 'ACTIVE' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Cannot delete an ACTIVE vehicle. '
                               'Change status to SCRAPPED or DEREGISTERED first.';
    END IF;
END$$

-- ============================================================
-- TRIGGER 4: trg_before_licence_delete
-- Fires: BEFORE DELETE on driving_licences
-- Purpose: Prevent deletion of ACTIVE driving licences.
--          Licences should be REVOKED or EXPIRED, never deleted.
-- ============================================================
CREATE TRIGGER trg_before_licence_delete
BEFORE DELETE ON driving_licences
FOR EACH ROW
BEGIN
    IF OLD.current_status = 'ACTIVE' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Cannot delete an ACTIVE driving licence. '
                               'Revoke or expire it first.';
    END IF;
END$$

-- ============================================================
-- TRIGGER 5: trg_before_road_tax_update
-- Fires: BEFORE UPDATE on road_tax_records
-- Purpose: Auto-correct status → OVERDUE when:
--            OLD.status = 'DUE' AND NEW.due_date has passed.
--          This catches both:
--            a) Direct updates that forget to flip the status.
--            b) The sp_update_overdue_road_tax() batch
--               (redundant but harmless — SP already sets OVERDUE).
--          The backend PaymentService flips status → PAID on success;
--          this trigger only intercepts the DUE → anything path,
--          so there is no conflict with the payment flow.
-- ============================================================
CREATE TRIGGER trg_before_road_tax_update
BEFORE UPDATE ON road_tax_records
FOR EACH ROW
BEGIN
    -- If the record is still DUE but its due_date is already past,
    -- silently correct it to OVERDUE before the row is written.
    IF NEW.status = 'DUE' AND NEW.due_date < CURDATE() THEN
        SET NEW.status = 'OVERDUE';
    END IF;
END$$

DELIMITER ;

-- ============================================================
-- Verification: list all triggers in this schema.
-- ============================================================
-- SELECT trigger_name, event_manipulation, event_object_table,
--        action_timing, action_statement
-- FROM information_schema.triggers
-- WHERE trigger_schema = 'rto_management'
-- ORDER BY event_object_table, action_timing, event_manipulation;
