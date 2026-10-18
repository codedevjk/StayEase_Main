package com.stayease.common.enums;

/**
 * Cancellation reason codes recorded on a cancelled reservation.
 *
 * <p>Only PAYMENT_TIMEOUT cancellations are exempt from the guest-initiated
 * late-cancellation penalty (one night's charge).
 */
public enum CancellationReason {

    /** Guest requested cancellation >= 24 hours before scheduled check-in (free cancellation). */
    GUEST_FREE,

    /** Guest requested cancellation < 24 hours before scheduled check-in (penalty applies). */
    GUEST_PENALTY,

    /**
     * System automatically cancelled because the pay-later payment deadline
     * (paymentDueAt) passed without successful payment. No penalty applies.
     */
    PAYMENT_TIMEOUT,

    /** Administrator-initiated cancellation (e.g., hotel operational issue). */
    ADMIN_INITIATED
}
