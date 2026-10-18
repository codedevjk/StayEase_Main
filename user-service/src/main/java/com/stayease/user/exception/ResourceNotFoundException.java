package com.stayease.user.exception;

/**
 * Thrown when a requested resource (e.g. User by ID) does not exist in the database.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
