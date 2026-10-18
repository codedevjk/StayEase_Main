package com.stayease.common.enums;

/**
 * Publication state of a guest review.
 */
public enum ReviewStatus {

    /** Review is visible to the public on the hotel detail page. */
    PUBLISHED,

    /** Review has been removed by an administrator. Soft-deleted; retained for audit. */
    REMOVED
}
