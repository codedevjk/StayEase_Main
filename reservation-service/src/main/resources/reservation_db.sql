CREATE DATABASE IF NOT EXISTS stayease_reservation_db;
USE stayease_reservation_db;

-- ═══════════════════════════════════════════════════════════════════════════
-- stayease_reservation_db / schema.sql
-- Reservation Service — DDL for all tables
--
-- MANUAL EXECUTION:
--   mysql -u root -p stayease_reservation_db < database/stayease_reservation_db/schema.sql
-- ═══════════════════════════════════════════════════════════════════════════

SET NAMES utf8mb4;
SET time_zone = '+00:00';
SET foreign_key_checks = 0;

-- ── room_type_inventory ───────────────────────────────────────────────────
-- Nightly inventory ledger.
-- LOCKING NOTE: All capacity check and hold/reserve operations must lock
-- rows in this table using SELECT ... FOR UPDATE ORDER BY stay_date ASC
-- within a single @Transactional boundary to prevent overbooking.
CREATE TABLE IF NOT EXISTS room_type_inventory (
    room_type_id    BIGINT NOT NULL COMMENT 'Cross-service ID referencing property_service.room_types.id',
    stay_date       DATE   NOT NULL COMMENT 'The individual night this record governs',
    total_capacity  INT    NOT NULL COMMENT 'Sellable capacity as reported by Property Service at last refresh',
    reserved_count  INT    NOT NULL DEFAULT 0 COMMENT 'Confirmed CONFIRMED reservations occupying this night',
    hold_count      INT    NOT NULL DEFAULT 0 COMMENT 'Active unexpired PENDING_PAYMENT holds occupying this night',
    version         BIGINT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version (secondary; pessimistic lock is primary)',

    PRIMARY KEY (room_type_id, stay_date),
    INDEX idx_inv_date (stay_date),
    CONSTRAINT chk_inv_counts CHECK (reserved_count >= 0 AND hold_count >= 0 AND total_capacity >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Per-night inventory ledger; authoritative source of room-type availability';

-- ── reservations ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS reservations (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    public_reference    VARCHAR(30)  NOT NULL COMMENT 'Human-readable reference e.g. SE-2026-5001',
    user_id             BIGINT       NOT NULL COMMENT 'Cross-service ID from user_service.users.id',
    hotel_id            BIGINT       NOT NULL COMMENT 'Cross-service ID from property_service.hotels.id',
    room_type_id        BIGINT       NOT NULL COMMENT 'Cross-service ID from property_service.room_types.id',
    check_in_date       DATE         NOT NULL,
    check_out_date      DATE         NOT NULL,
    nights              INT          NOT NULL COMMENT 'Computed: DATEDIFF(check_out_date, check_in_date)',
    guest_count         INT          NOT NULL,
    reservation_status  VARCHAR(30)  NOT NULL
                        COMMENT 'PENDING_PAYMENT|CONFIRMED|CHECKED_IN|CHECKED_OUT|CANCELLED|NO_SHOW|PAYMENT_FAILED|RECONCILIATION_REQUIRED',
    payment_policy      VARCHAR(20)  NOT NULL COMMENT 'PREPAID | PAY_LATER',
    payment_status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                        COMMENT 'PENDING|PROCESSING|PAID|FAILED|REFUNDED',
    hold_expires_at     TIMESTAMP        NULL COMMENT 'Set for PREPAID holds; null for PAY_LATER',
    payment_due_at      TIMESTAMP        NULL COMMENT 'PAY_LATER deadline; null for PREPAID',
    lodging_subtotal    DECIMAL(10,2) NOT NULL COMMENT 'Sum of nightly effective rates; INR',
    tax_percentage      DECIMAL(5,2)  NOT NULL COMMENT 'Tax rate in effect at booking time',
    tax_amount          DECIMAL(10,2) NOT NULL COMMENT 'Calculated as lodging_subtotal * tax_percentage / 100',
    total_amount        DECIMAL(10,2) NOT NULL COMMENT 'lodging_subtotal + tax_amount',
    price_snapshot_json JSON          NOT NULL COMMENT 'Immutable nightly rate breakdown; used for invoices/refunds',
    penalty_amount      DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT 'Late-cancellation penalty amount (INR)',
    refund_amount       DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT 'Refund amount issued on cancellation (INR)',
    cancellation_reason VARCHAR(50)       NULL COMMENT 'GUEST_FREE|GUEST_PENALTY|PAYMENT_TIMEOUT|ADMIN_INITIATED',
    cancellation_notes  VARCHAR(255)      NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at        TIMESTAMP        NULL,
    cancelled_at        TIMESTAMP        NULL,
    checked_in_at       TIMESTAMP        NULL,
    checked_out_at      TIMESTAMP        NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uq_reservation_reference (public_reference),
    INDEX idx_res_user   (user_id),
    INDEX idx_res_hotel  (hotel_id),
    INDEX idx_res_status (reservation_status),
    INDEX idx_res_dates  (check_in_date, check_out_date),
    INDEX idx_res_hold_expiry (hold_expires_at, reservation_status),
    INDEX idx_res_pay_due     (payment_due_at, reservation_status),
    CONSTRAINT chk_res_dates CHECK (check_out_date > check_in_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Core reservation records; status transitions are validated in service layer';

-- ── payment_transactions ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS payment_transactions (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    reservation_id      BIGINT        NOT NULL,
    idempotency_key     VARCHAR(100)  NOT NULL COMMENT 'Client-supplied UUID; unique constraint enforces idempotency',
    transaction_reference VARCHAR(100) NOT NULL COMMENT 'Generated internal transaction reference e.g. TXN-xxxxxxxx',
    payment_method      VARCHAR(30)   NOT NULL COMMENT 'CARD | UPI | NETBANKING',
    amount              DECIMAL(10,2) NOT NULL,
    payment_status      VARCHAR(20)   NOT NULL COMMENT 'PROCESSING|PAID|FAILED|REFUNDED',
    simulated_outcome   VARCHAR(30)   NOT NULL COMMENT 'SUCCESS|FAILURE|TIMEOUT — as submitted to the simulator',
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at        TIMESTAMP         NULL COMMENT 'Null until the outcome is definitively resolved',

    PRIMARY KEY (id),
    UNIQUE KEY uq_idempotency_key (idempotency_key),
    UNIQUE KEY uq_transaction_ref (transaction_reference),
    FOREIGN KEY fk_pt_reservation (reservation_id) REFERENCES reservations(id) ON DELETE CASCADE,
    INDEX idx_pt_reservation (reservation_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Payment simulation records with idempotency enforcement';

-- ── room_assignments ──────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS room_assignments (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    reservation_id      BIGINT        NOT NULL,
    physical_room_id    BIGINT        NOT NULL COMMENT 'Cross-service ID from property_service.physical_rooms.id',
    physical_room_number VARCHAR(20)  NOT NULL COMMENT 'Snapshot of room number at assignment time',
    assigned_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    checked_in_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    checked_out_at      TIMESTAMP         NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uq_ra_reservation (reservation_id)  COMMENT 'One assignment per reservation',
    FOREIGN KEY fk_ra_reservation (reservation_id) REFERENCES reservations(id) ON DELETE CASCADE,
    INDEX idx_ra_room (physical_room_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Physical room assignment records; populated at check-in (not at booking)';

-- ── reservation_audit_events ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS reservation_audit_events (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    actor_id        BIGINT       NOT NULL,
    actor_role      VARCHAR(20)  NOT NULL,
    event_category  VARCHAR(50)  NOT NULL COMMENT 'RESERVATION|PAYMENT|CHECK_IN|CHECK_OUT|CANCELLATION|AMENDMENT',
    action          VARCHAR(100) NOT NULL,
    entity_id       VARCHAR(100) NOT NULL,
    description     VARCHAR(255) NOT NULL,
    before_json     JSON             NULL,
    after_json      JSON             NULL,
    timestamp       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_res_audit_timestamp (timestamp),
    INDEX idx_res_audit_actor (actor_id),
    INDEX idx_res_audit_category (event_category)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Reservation lifecycle audit trail owned by Reservation Service';

SET foreign_key_checks = 1;

-- ═══════════════════════════════════════════════════════════════════════════
-- stayease_reservation_db / data.sql
-- Reservation Service — Reference seed data
--
-- MANUAL EXECUTION (after schema.sql):
--   mysql -u root -p stayease_reservation_db < database/stayease_reservation_db/data.sql
-- ═══════════════════════════════════════════════════════════════════════════

SET NAMES utf8mb4;

-- The room_type_inventory table is populated lazily by Reservation Service
-- the first time availability is queried or a booking is placed.
-- No seed data is needed in the inventory ledger.

-- Reservation lifecycle records are created at runtime through the API.
-- No sample reservations are seeded to keep the reference database clean
-- for verified end-to-end test scenarios.

SELECT 'stayease_reservation_db seed data applied (no rows — inventory and reservations created at runtime).' AS status;

