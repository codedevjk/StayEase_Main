package com.stayease.common.enums;

/**
 * Reservation lifecycle states.
 *
 * <p>State transition rules are documented in {@code docs/decisions.md §4.1}.
 * No state transition should bypass the service-layer validation methods.
 *
 * <pre>
 * PENDING_PAYMENT ──▶ CONFIRMED              (prepaid: payment succeeds)
 * PENDING_PAYMENT ──▶ PAYMENT_FAILED         (prepaid: payment declined or hold expired)
 * PENDING_PAYMENT ──▶ RECONCILIATION_REQUIRED (prepaid: payment simulator timeout)
 *
 * RECONCILIATION_REQUIRED ──▶ CONFIRMED      (reconciled as PAID)
 * RECONCILIATION_REQUIRED ──▶ PAYMENT_FAILED (reconciled as FAILED)
 *
 * CONFIRMED ──▶ CANCELLED                    (guest/admin cancel or pay-later deadline)
 * CONFIRMED ──▶ CHECKED_IN                   (self check-in: paid + clean room)
 * CONFIRMED ──▶ NO_SHOW                      (admin marks no-show)
 *
 * CHECKED_IN ──▶ CHECKED_OUT                 (self check-out)
 *
 * Terminal states: CHECKED_OUT, CANCELLED, NO_SHOW, PAYMENT_FAILED
 * </pre>
 */
public enum ReservationStatus {

    /** Prepaid hold created; awaiting successful payment within 10 minutes. */
    PENDING_PAYMENT,

    /** Inventory secured; reservation is active. */
    CONFIRMED,

    /** Guest has checked in; physical room assigned. */
    CHECKED_IN,

    /** Guest has checked out; physical room marked DIRTY. */
    CHECKED_OUT,

    /** Cancelled by guest, admin, or system (PAYMENT_TIMEOUT). */
    CANCELLED,

    /** Admin-marked after check-in date passed without guest arrival. */
    NO_SHOW,

    /** Prepaid payment was declined or hold expired without payment. */
    PAYMENT_FAILED,

    /**
     * Payment simulator returned an ambiguous result (timeout/network error).
     * Inventory hold is protected until reconciliation resolves the state.
     * A second payment attempt with a new idempotency key must never be
     * initiated automatically while in this state.
     */
    RECONCILIATION_REQUIRED
}
