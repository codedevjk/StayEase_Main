package com.stayease.user.exception;

/**
 * Thrown when credentials fail verification during login or password check.
 * Uses a generic error message to prevent account enumeration.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
