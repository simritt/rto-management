-- ============================================================
-- RTO Management System
-- procedures/20_procedures.sql
-- Stored procedures for core RTO operations.
--
-- These procedures are administrative / DBA tools intended for:
--   • Direct database maintenance without the backend running
--   • Batch operations (road tax overdue update)
--   • Emergency recovery / data migration
--
-- They are NOT called by the Spring Boot backend, which performs
-- the same operations via @Transactional service methods.
-- Running these while the backend is active is safe because they
-- operate on the same data model and enforce the same constraints.
--
-- ┌─────────────────────────────────────────────────────────┐
-- │ PROCEDURES                                              │
-- │   1. sp_transfer_vehicle_ownership                      │
-- │   2. sp_process_challan_payment                         │
-- │   3. sp_update_overdue_road_tax                         │
-- └─────────────────────────────────────────────────────────┘
--
-- Depends on: 07, 09, 12, 13, and seed data for testing.
-- ============================================================

USE rto_management;

DELIMITER $$

-- ============================================================
-- PROCEDURE 1: sp_transfer_vehicle_ownership
-- ============================================================
-- Approves a PENDING ownership transfer:
--   1. Validates transfer exists and is PENDING
--   2. Validates vehicle is ACTIVE
--   3. Validates current ownership row exists
--   4. Validates the from-citizen is still the current owner
--   5. Closes the current ownership (effective_to = p_transfer_date)
--   6. Opens a new ownership for the to-citizen
--   7. Marks the transfer APPROVED
--   8. All steps in one atomic transaction; ROLLBACK on any error
--
-- Parameters:
--   p_transfer_id    BIGINT UNSIGNED  — ownership_transfers.transfer_id
--   p_transfer_date  DATE             — effective date of transfer
--                                       (NULL → CURDATE())
--
-- Output: SELECT message indicating success or the specific failure.
-- ============================================================
DROP PROCEDURE IF EXISTS sp_transfer_vehicle_ownership$$

CREATE PROCEDURE sp_transfer_vehicle_ownership(
    IN  p_transfer_id   BIGINT UNSIGNED,
    IN  p_transfer_date DATE
)
BEGIN
    DECLARE v_vehicle_id        BIGINT UNSIGNED;
    DECLARE v_from_citizen_id   BIGINT UNSIGNED;
    DECLARE v_to_citizen_id     BIGINT UNSIGNED;
    DECLARE v_transfer_status   VARCHAR(20);
    DECLARE v_vehicle_status    VARCHAR(20);
    DECLARE v_ownership_id      BIGINT UNSIGNED;
    DECLARE v_current_owner_id  BIGINT UNSIGNED;
    DECLARE v_effective_from    DATE;
    DECLARE v_transfer_date     DATE;
    DECLARE v_new_ownership_id  BIGINT UNSIGNED;
    DECLARE v_err_msg           VARCHAR(255);

    -- Error handler: roll back the whole transaction on any SQL error.
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;    -- re-raise the signal so the caller sees the error
    END;

    -- Default transfer date to today if not supplied.
    SET v_transfer_date = IFNULL(p_transfer_date, CURDATE());

    START TRANSACTION;

    -- ── Step 1: Read and lock the transfer row ──────────────
    SELECT vehicle_id, from_citizen_id, to_citizen_id, status
    INTO   v_vehicle_id, v_from_citizen_id, v_to_citizen_id, v_transfer_status
    FROM   ownership_transfers
    WHERE  transfer_id = p_transfer_id
    FOR UPDATE;

    IF v_vehicle_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Transfer not found.';
    END IF;

    IF v_transfer_status <> 'PENDING' THEN
        SET v_err_msg = CONCAT('Transfer is ', v_transfer_status, '; only PENDING transfers can be approved.');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_err_msg;
    END IF;

    -- ── Step 2: Lock and validate the vehicle ───────────────
    SELECT status
    INTO   v_vehicle_status
    FROM   vehicles
    WHERE  vehicle_id = v_vehicle_id
    FOR UPDATE;

    IF v_vehicle_status <> 'ACTIVE' THEN
        SET v_err_msg = CONCAT('Vehicle is ', v_vehicle_status, '; only ACTIVE vehicles can be transferred.');
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_err_msg;
    END IF;

    -- ── Step 3: Lock the current ownership row ──────────────
    SELECT ownership_id, citizen_id, effective_from
    INTO   v_ownership_id, v_current_owner_id, v_effective_from
    FROM   vehicle_ownerships
    WHERE  vehicle_id  = v_vehicle_id
      AND  effective_to IS NULL
    FOR UPDATE;

    IF v_ownership_id IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'No current owner found for the vehicle.';
    END IF;

    -- ── Step 4: Confirm the from-citizen is still the owner ─
    IF v_current_owner_id <> v_from_citizen_id THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'The from-citizen is no longer the vehicle''s current owner.';
    END IF;

    -- ── Step 5: Validate transfer date chronology ───────────
    IF v_transfer_date > CURDATE() THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'transfer_date cannot be in the future.';
    END IF;

    IF v_transfer_date < v_effective_from THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'transfer_date precedes the current ownership start date.';
    END IF;

    -- ── Step 6: Close the current ownership ─────────────────
    -- The generated column current_flag (IF(effective_to IS NULL, 1, NULL)) will
    -- become NULL after this UPDATE, releasing the uq_vehicle_current_owner slot.
    UPDATE vehicle_ownerships
    SET    effective_to = v_transfer_date
    WHERE  ownership_id = v_ownership_id;

    -- ── Step 7: Open new ownership for the transferee ───────
    INSERT INTO vehicle_ownerships (vehicle_id, citizen_id, effective_from)
    VALUES (v_vehicle_id, v_to_citizen_id, v_transfer_date);

    SET v_new_ownership_id = LAST_INSERT_ID();

    -- ── Step 8: Mark transfer APPROVED ──────────────────────
    UPDATE ownership_transfers
    SET    status      = 'APPROVED',
           approved_at = NOW()
    WHERE  transfer_id = p_transfer_id;

    COMMIT;

    -- Return a success summary.
    SELECT
        p_transfer_id                   AS transfer_id,
        v_vehicle_id                    AS vehicle_id,
        v_from_citizen_id               AS from_citizen_id,
        v_to_citizen_id                 AS to_citizen_id,
        v_ownership_id                  AS closed_ownership_id,
        v_new_ownership_id              AS new_ownership_id,
        v_transfer_date                 AS effective_date,
        'APPROVED'                      AS result;
END$$

-- ============================================================
-- PROCEDURE 2: sp_process_challan_payment
-- ============================================================
-- Records a successful challan payment directly in the DB.
-- Use only for administrative reconciliation when the backend
-- payment gateway is unavailable.
--
-- Steps:
--   1. Validate challan exists and is ISSUED
--   2. Create a payment record (status=SUCCESS)
--   3. Create a payment_attempt record (outcome=SUCCESS)
--   4. Mark challan status = PAID
--   5. Append to challan_status_history
--   6. All in one atomic transaction; ROLLBACK on error
--
-- Parameters:
--   p_challan_id       BIGINT UNSIGNED  — challans.challan_id
--   p_receipt_number   VARCHAR(30)      — unique receipt number
--   p_gateway_ref      VARCHAR(60)      — gateway reference (idempotency key)
--
-- ============================================================
DROP PROCEDURE IF EXISTS sp_process_challan_payment$$

CREATE PROCEDURE sp_process_challan_payment(
    IN p_challan_id     BIGINT UNSIGNED,
    IN p_receipt_number VARCHAR(30),
    IN p_gateway_ref    VARCHAR(60)
)
BEGIN
    DECLARE v_challan_status    VARCHAR(20);
    DECLARE v_challan_amount    DECIMAL(10,2);
    DECLARE v_payable_type_id   BIGINT UNSIGNED;
    DECLARE v_payment_id        BIGINT UNSIGNED;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    -- Step 1: Lock and validate the challan.
    SELECT status, total_amount
    INTO   v_challan_status, v_challan_amount
    FROM   challans
    WHERE  challan_id = p_challan_id
    FOR UPDATE;

    IF v_challan_status IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Challan not found.';
    END IF;
    IF v_challan_status <> 'ISSUED' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Challan is not in ISSUED status; cannot process payment.';
    END IF;

    -- Idempotency: if the gateway reference was already used, ROLLBACK.
    IF EXISTS (SELECT 1 FROM payment_attempts WHERE gateway_reference = p_gateway_ref) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Gateway reference already used; payment already recorded.';
    END IF;

    -- Resolve payable_type_id for 'CHALLAN'.
    SELECT payable_type_id INTO v_payable_type_id
    FROM   payable_types WHERE type_name = 'CHALLAN' LIMIT 1;

    IF v_payable_type_id IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Payable type CHALLAN not configured.';
    END IF;

    -- Step 2: Create the payment record.
    INSERT INTO payments
        (receipt_number, payable_type_id, payable_id, amount, status, paid_at, created_at)
    VALUES
        (p_receipt_number, v_payable_type_id, p_challan_id,
         v_challan_amount, 'SUCCESS', NOW(), NOW());

    SET v_payment_id = LAST_INSERT_ID();

    -- Step 3: Record the payment attempt.
    INSERT INTO payment_attempts
        (payment_id, attempt_number, gateway_reference, outcome, attempted_at)
    VALUES
        (v_payment_id, 1, p_gateway_ref, 'SUCCESS', NOW());

    -- Step 4: Mark challan as PAID.
    UPDATE challans
    SET    status = 'PAID'
    WHERE  challan_id = p_challan_id;

    -- Step 5: Append challan status history.
    INSERT INTO challan_status_history
        (challan_id, previous_status, new_status, changed_at)
    VALUES
        (p_challan_id, 'ISSUED', 'PAID', NOW());

    COMMIT;

    SELECT
        p_challan_id    AS challan_id,
        v_payment_id    AS payment_id,
        p_receipt_number AS receipt_number,
        v_challan_amount AS amount_paid,
        'SUCCESS'        AS result;
END$$

-- ============================================================
-- PROCEDURE 3: sp_update_overdue_road_tax
-- ============================================================
-- Batch job: marks all DUE road-tax records as OVERDUE when
-- their due_date has passed. Safe to run daily via an OS cron
-- job or MySQL Event Scheduler.
--
-- Returns a count of rows updated.
-- ============================================================
DROP PROCEDURE IF EXISTS sp_update_overdue_road_tax$$

CREATE PROCEDURE sp_update_overdue_road_tax()
BEGIN
    DECLARE v_rows_updated INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    UPDATE road_tax_records
    SET    status = 'OVERDUE'
    WHERE  status   = 'DUE'
      AND  due_date < CURDATE();

    SET v_rows_updated = ROW_COUNT();

    COMMIT;

    SELECT
        v_rows_updated                AS overdue_records_updated,
        NOW()                         AS executed_at;
END$$

DELIMITER ;

-- ============================================================
-- Quick-test calls (run after seed data is loaded)
-- ============================================================
-- CALL sp_update_overdue_road_tax();
-- CALL sp_transfer_vehicle_ownership(1, CURDATE());   -- transfer_id=1
-- CALL sp_process_challan_payment(3, 'RCT-ADMIN-001', 'GW-ADMIN-001');
