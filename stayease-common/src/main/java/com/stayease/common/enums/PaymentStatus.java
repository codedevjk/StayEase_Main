package com.stayease.common.enums;

/**
 * Payment lifecycle states attached to a reservation.
 *
 * <pre>
 * PENDING    ──▶ PROCESSING  (payment attempt submitted)
 * PROCESSING ──▶ PAID        (payment confirmed and persisted)
 * PROCESSING ──▶ FAILED      (payment definitively declined)
 * PAID       ──▶ REFUNDED    (cancellation or amendment refund processed)
 * </pre>
 *
 * <p>A PROCESSING state left unresolved (due to payment simulator timeout)
 * corresponds to ReservationStatus.RECONCILIATION_REQUIRED.
 * Inventory must not be released while payment status is PROCESSING.
 */
public enum PaymentStatus {

    /** No payment attempt yet (default for PAY_LATER reservations). */
    PENDING,

    /** A payment attempt is in flight or awaiting reconciliation. */
    PROCESSING,

    /** Payment successfully completed and persisted. */
    PAID,

    /** Payment definitively declined or definitively failed. */
    FAILED,

    /** Full or partial refund has been applied. */
    REFUNDED
}
