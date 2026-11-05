package com.stayease.user.exception;

/**
 * Thrown when an email registration or email update conflicts with an existing user.
 */
public class DuplicateEmailException extends RuntimeException {

    private final String email;

    public DuplicateEmailException(String email) {
        super("An account with email '" + email + "' already exists");
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
