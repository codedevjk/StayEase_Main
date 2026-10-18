package com.stayease.common.enums;

/**
 * Housekeeping/cleanliness status of a physical room.
 *
 * <p>Transition rules:
 * <ul>
 *   <li>CLEAN  → eligible for guest check-in assignment</li>
 *   <li>DIRTY  → blocked from immediate check-in; does not remove future sellable capacity</li>
 *   <li>INSPECTING → temporarily blocked pending inspection; does not affect future capacity</li>
 * </ul>
 *
 * <p>For a physical room to be eligible for check-in assignment it must have
 * {@code housekeepingStatus == CLEAN} AND {@code operationalStatus == OPERATIONAL}.
 */
public enum HousekeepingStatus {

    /** Room has been cleaned and is ready for guest occupancy. */
    CLEAN,

    /** Room has been vacated and requires cleaning (set automatically at checkout). */
    DIRTY,

    /** Room is being inspected before being returned to CLEAN status. */
    INSPECTING
}
