package com.stayease.user.exception;

/**
 * Stable error message constants for User Service, aligned with CourseCart conventions.
 */
public final class ErrorMessages {

    public static final String USER_NOT_FOUND = "User not found";
    public static final String DUPLICATE_EMAIL = "Email is already registered";
    public static final String INVALID_CREDENTIALS = "Invalid email or password";
    public static final String CURRENT_PASSWORD_INCORRECT = "Current password does not match";
    public static final String ACCESS_DENIED = "Access is denied: insufficient role privileges";

    private ErrorMessages() {
        // Prevent instantiation
    }
}
