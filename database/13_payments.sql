-- ============================================================
-- RTO Management System
-- 13_payments.sql
-- Fee structures and the generic payment engine (4 tables).
-- payments is polymorphic: one payment engine serves applications,
-- challans, permits and road tax via payable_type_id + payable_id.
-- Money is DECIMAL(10,2) everywhere — never FLOAT/DOUBLE.
-- Depends on: 02 (service_types, payable_types).
-- ============================================================

USE rto_management;

CREATE TABLE fee_structures (
    fee_id       BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    service_type_id BIGINT UNSIGNED NOT NULL,
    component_name VARCHAR(80) NOT NULL,              -- e.g. "Processing Fee", "Smart Card Fee"
    amount       DECIMAL(10,2) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to   DATE NULL,
    CONSTRAINT fk_fee_service FOREIGN KEY (service_type_id)
        REFERENCES service_types(service_type_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE payments (
    payment_id     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    receipt_number VARCHAR(30) NOT NULL,
    payable_type_id BIGINT UNSIGNED NOT NULL,
    payable_id     BIGINT UNSIGNED NOT NULL,           -- polymorphic FK, resolved at app layer per payable_type
    amount         DECIMAL(10,2) NOT NULL,
    status         ENUM('PENDING','SUCCESS','FAILED','REFUNDED') NOT NULL DEFAULT 'PENDING',
    paid_at        DATETIME NULL,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_receipt_number (receipt_number),
    KEY idx_payment_payable (payable_type_id, payable_id),
    KEY idx_payment_status (status),
    CONSTRAINT fk_payment_payable_type FOREIGN KEY (payable_type_id)
        REFERENCES payable_types(payable_type_id) ON DELETE RESTRICT
) ENGINE=InnoDB COMMENT='payable_id is not a DB-level FK — it is polymorphic across application/challan/permit/tax; integrity enforced at app layer + audit';

CREATE TABLE payment_attempts (
    attempt_id     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    payment_id     BIGINT UNSIGNED NOT NULL,
    attempt_number SMALLINT UNSIGNED NOT NULL,
    gateway_reference VARCHAR(60) NULL,
    outcome        ENUM('SUCCESS','FAILED','TIMEOUT') NOT NULL,
    attempted_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_payment_attempt (payment_id, attempt_number),
    CONSTRAINT fk_attempt_payment FOREIGN KEY (payment_id)
        REFERENCES payments(payment_id) ON DELETE CASCADE
) ENGINE=InnoDB COMMENT='Idempotency: gateway_reference is deduplicated at app layer before insert';

CREATE TABLE refunds (
    refund_id     BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    payment_id    BIGINT UNSIGNED NOT NULL,
    amount        DECIMAL(10,2) NOT NULL,
    reason        VARCHAR(255) NOT NULL,
    status        ENUM('INITIATED','COMPLETED','FAILED') NOT NULL DEFAULT 'INITIATED',
    processed_at  DATETIME NULL,
    CONSTRAINT fk_refund_payment FOREIGN KEY (payment_id)
        REFERENCES payments(payment_id) ON DELETE RESTRICT
) ENGINE=InnoDB;
