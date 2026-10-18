package com.stayease.common.enums;

/**
 * Operational status of a physical room.
 *
 * <p>IMPORTANT: Only rooms with {@code operationalStatus == OUT_OF_SERVICE}
 * reduce the room type's <em>sellable capacity</em> reported by Property Service.
 * OPERATIONAL rooms in any housekeeping state do not reduce future capacity.
 */
public enum OperationalStatus {

    /** Room is in normal service and can be assigned to guests. */
    OPERATIONAL,

    /**
     * Room is taken offline (maintenance, renovation, etc.).
     * Out-of-service rooms are excluded from sellable capacity counts.
     * Capacity reduction must be immediately reflected in Property Service's
     * internal capacity endpoint.
     */
    OUT_OF_SERVICE
}
