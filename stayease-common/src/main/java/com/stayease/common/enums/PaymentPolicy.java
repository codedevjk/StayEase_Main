package com.stayease.common.enums;

/**
 * Payment policy selected at the time of booking.
 * This value is immutable once the reservation is confirmed.
 */
public enum PaymentPolicy {

    /**
     * Guest must pay within a 10-minute inventory hold window.
     * If payment is not confirmed within 10 minutes, the hold
     * expires and the reservation transitions to PAYMENT_FAILED.
     */
    PREPAID,

    /**
     * Inventory is reserved immediately at confirmation.
     * Payment must be received before paymentDueAt, which is
     * the earlier of (createdAt + 24 hours) and (checkInDate at 14:00 local time).
     */
    PAY_LATER
}
