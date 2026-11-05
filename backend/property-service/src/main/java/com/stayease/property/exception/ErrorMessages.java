package com.stayease.property.exception;

/**
 * Stable error message constants for Property Service, aligned with CourseCart conventions.
 */
public final class ErrorMessages {

    public static final String HOTEL_NOT_FOUND = "Hotel not found";
    public static final String ROOM_TYPE_NOT_FOUND = "Room type not found";
    public static final String PHYSICAL_ROOM_NOT_FOUND = "Physical room not found";
    public static final String DUPLICATE_ROOM_NUMBER = "Physical room number already exists in this hotel";
    public static final String NO_CLEAN_ROOM_AVAILABLE = "No clean operational room currently available for check-in";
    public static final String INVALID_RATE_DATE_RANGE = "Rate override start date must be before or equal to end date";
    public static final String ACCESS_DENIED = "Access is denied: insufficient role privileges";

    private ErrorMessages() {
        // Prevent instantiation
    }
}
