package com.stayease.property.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Housekeeping/cleanliness status of a physical room.
 */
public enum HousekeepingStatus {
    CLEAN,
    DIRTY,
    INSPECTING;

    @JsonCreator
    public static HousekeepingStatus fromString(String value) {
        if (value == null) return null;
        String upper = value.trim().toUpperCase();
        if ("INSPECTED".equals(upper)) {
            return INSPECTING;
        }
        return HousekeepingStatus.valueOf(upper);
    }
}
