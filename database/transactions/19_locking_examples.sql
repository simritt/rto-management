-- ============================================================
-- RTO Management System
-- transactions/19_locking_examples.sql
-- Annotated transaction + locking demonstration scripts.
--
-- PURPOSE
-- -------
-- These scripts demonstrate MySQL transaction patterns that mirror
-- what the Spring Boot backend does internally with @Transactional
-- and SELECT ... FOR UPDATE (via LockModeType.PESSIMISTIC_WRITE).
-- They are intended for:
--   • DBA testing and verification
--   • Administrative overrides when the backend is unavailable
--   • Educational demonstration of ACID properties in the RTO context
--
-- Run each block individually from a MySQL client.
-- Seed data (file 22) must be loaded first.
--
-- ⚠  DO NOT run these inside an active backend session — they acquire
--    row-level locks that will block concurrent backend requests for
--    the duration of the explicit transaction.
-- ============================================================

USE rto_management;

-- ============================================================
-- EXAMPLE A: APPOINTMENT SLOT BOOKING
-- ============================================================
-- Scenario: A citizen books an appointment slot.
-- Race condition to prevent: two concurrent sessions both read
-- booked_count = 4 (< capacity 5) and both try to book.
-- Solution: SELECT ... FOR UPDATE locks the slot row so the
-- second session must wait until the first commits.
--
-- Mirrors: AppointmentService.book() in the backend.
-- ============================================================

-- -- Session 1
-- START TRANSACTION;
--
-- -- Step 1: Lock the target slot row.
-- SELECT slot_id, capacity, booked_count
-- FROM appointment_slots
-- WHERE slot_id = 1                -- replace with actual slot_id from seed data
-- FOR UPDATE;
--
-- -- Step 2 (application checks booked_count < capacity, then):
-- UPDATE appointment_slots
-- SET    booked_count = booked_count + 1
-- WHERE  slot_id = 1
--   AND  booked_count < capacity;   -- atomic guard
--
-- -- If no rows updated → slot is full; application raises conflict error.
-- -- If 1 row updated  → proceed to insert the appointment.
-- INSERT INTO appointments
--     (application_id, slot_id, token_number, status, booked_at)
-- VALUES
--     (1, 1, 'TKN-0001', 'BOOKED', NOW());
--
-- COMMIT;  -- releases the FOR UPDATE lock

-- ============================================================
-- EXAMPLE B: VEHICLE OWNERSHIP TRANSFER
-- ============================================================
-- Scenario: An RTO officer approves a pending ownership transfer.
-- Critical invariant: the vehicle must have exactly ONE current owner
-- at all times. The uq_vehicle_current_owner unique constraint on the
-- generated column enforces this at the DB level, but the transaction
-- ensures atomicity — no partial update leaves the vehicle without
-- an owner even for a millisecond.
--
-- Steps (must be atomic):
--   1. Lock transfer row (verify PENDING)
--   2. Lock vehicle row (verify ACTIVE)
--   3. Lock current ownership row (SELECT FOR UPDATE)
--   4. Close the current ownership (effective_to = transfer_date)
--   5. Flush (critical: the unique constraint requires the old row
--      to be closed BEFORE the new open row is inserted)
--   6. Insert new ownership (effective_to = NULL)
--   7. Mark transfer as APPROVED
--   8. COMMIT
--
-- Mirrors: OwnershipService.approve() — shown here as direct SQL.
-- The stored procedure sp_transfer_vehicle_ownership (file 20) is
-- the encapsulated version of this transaction.
-- ============================================================
START TRANSACTION;

-- Step 1: Lock the transfer row.
SELECT transfer_id, vehicle_id, from_citizen_id, to_citizen_id, status
FROM   ownership_transfers
WHERE  transfer_id = 1              -- replace with actual transfer_id
FOR UPDATE;

-- (Application verifies status = 'PENDING'; if not, ROLLBACK.)

-- Step 2: Lock the vehicle row.
SELECT vehicle_id, status
FROM   vehicles
WHERE  vehicle_id = (SELECT vehicle_id FROM ownership_transfers WHERE transfer_id = 1)
FOR UPDATE;

-- (Application verifies status = 'ACTIVE'; if not, ROLLBACK.)

-- Step 3: Lock the current ownership row.
SELECT ownership_id, citizen_id, effective_from
FROM   vehicle_ownerships
WHERE  vehicle_id  = (SELECT vehicle_id FROM ownership_transfers WHERE transfer_id = 1)
  AND  effective_to IS NULL
FOR UPDATE;

-- Step 4: Close the current ownership.
UPDATE vehicle_ownerships
SET    effective_to = CURDATE()
WHERE  vehicle_id   = (SELECT vehicle_id FROM ownership_transfers WHERE transfer_id = 1)
  AND  effective_to IS NULL;

-- Step 5: Implicit flush — the UPDATE above takes effect within the transaction.
--         The uq_vehicle_current_owner index now has no open row for this vehicle.

-- Step 6: Insert new open ownership for the transferee.
INSERT INTO vehicle_ownerships (vehicle_id, citizen_id, effective_from)
SELECT vehicle_id, to_citizen_id, CURDATE()
FROM   ownership_transfers
WHERE  transfer_id = 1;

-- Step 7: Mark transfer as APPROVED.
UPDATE ownership_transfers
SET    status      = 'APPROVED',
       approved_at = NOW()
WHERE  transfer_id = 1;

COMMIT;

-- ============================================================
-- EXAMPLE C: PAYMENT PROCESSING WITH ROLLBACK ON FAILURE
-- ============================================================
-- Scenario: Record a payment attempt that fails due to gateway error.
-- Shows how a SAVEPOINT allows partial rollback while preserving the
-- payment record (so the client can retry on the same payment_id).
--
-- Mirrors: PaymentService.create() + recordAttempt().
-- ============================================================

-- Uncomment to run:
-- START TRANSACTION;
--
-- -- Step 1: Lock target (e.g., application row awaiting payment).
-- SELECT application_id, current_status
-- FROM   applications
-- WHERE  application_id = 1
-- FOR UPDATE;
--
-- -- (Verify current_status = 'AWAITING_PAYMENT'; ROLLBACK if not.)
--
-- -- Step 2: Create payment record.
-- INSERT INTO payments
--     (receipt_number, payable_type_id, payable_id, amount, status, created_at)
-- VALUES
--     ('RCT-DEMO-001',
--      (SELECT payable_type_id FROM payable_types WHERE type_name = 'APPLICATION'),
--      1, 500.00, 'PENDING', NOW());
--
-- SET @new_payment_id = LAST_INSERT_ID();
--
-- SAVEPOINT after_payment_created;
--
-- -- Step 3: Record first attempt (gateway TIMEOUT — no outcome yet).
-- INSERT INTO payment_attempts
--     (payment_id, attempt_number, gateway_reference, outcome, attempted_at)
-- VALUES
--     (@new_payment_id, 1, 'GW-REF-001', 'TIMEOUT', NOW());
--
-- -- TIMEOUT → status stays PENDING; proceed without changing payment status.
--
-- -- Step 4: Record second attempt (gateway FAILED).
-- INSERT INTO payment_attempts
--     (payment_id, attempt_number, gateway_reference, outcome, attempted_at)
-- VALUES
--     (@new_payment_id, 2, 'GW-REF-002', 'FAILED', NOW());
--
-- -- Update payment status to FAILED.
-- UPDATE payments SET status = 'FAILED' WHERE payment_id = @new_payment_id;
--
-- -- Simulated error during downstream notification: roll back ONLY the notification,
-- -- keeping the payment + attempts intact.
-- -- (In real flow, notifications are outside the payment transaction.)
-- ROLLBACK TO SAVEPOINT after_payment_created;
--
-- -- The payment and both attempts survive; re-commit.
-- COMMIT;
--
-- -- On the NEXT attempt (gateway SUCCESS):
-- START TRANSACTION;
--
-- SELECT payment_id, status FROM payments WHERE payment_id = @new_payment_id FOR UPDATE;
-- -- Verify status = 'FAILED' (retry is allowed) or 'PENDING'.
--
-- INSERT INTO payment_attempts
--     (payment_id, attempt_number, gateway_reference, outcome, attempted_at)
-- VALUES
--     (@new_payment_id, 3, 'GW-REF-003', 'SUCCESS', NOW());
--
-- UPDATE payments SET status = 'SUCCESS', paid_at = NOW()
-- WHERE  payment_id = @new_payment_id;
--
-- COMMIT;
-- -- At this point the backend would also advance the application status.

-- ============================================================
-- EXAMPLE D: CONCURRENT OWNERSHIP LOCK — CONFLICT DEMONSTRATION
-- ============================================================
-- Run in two separate MySQL sessions simultaneously to observe locking.
--
-- Session 1:
--   START TRANSACTION;
--   SELECT * FROM vehicle_ownerships WHERE vehicle_id = 1 AND effective_to IS NULL FOR UPDATE;
--   -- Don't commit yet.
--
-- Session 2 (opens immediately after Session 1):
--   START TRANSACTION;
--   SELECT * FROM vehicle_ownerships WHERE vehicle_id = 1 AND effective_to IS NULL FOR UPDATE;
--   -- Session 2 BLOCKS here until Session 1 commits or rolls back.
--
-- Session 1:
--   UPDATE vehicle_ownerships SET effective_to = CURDATE() WHERE vehicle_id = 1 AND effective_to IS NULL;
--   COMMIT;
--   -- Session 2 is now unblocked and sees the committed row.
--
-- This demonstrates that the uq_vehicle_current_owner constraint AND
-- the row-level lock together prevent concurrent double-transfer.
