package com.stayease.user.dto;

/**
 * Response payload returned upon admin-assisted password reset.
 *
 * <p>Discloses the generated temporary password exactly once to the admin.
 * Plaintext temporary credentials must never be written to logs or audit records.
 */
public record AdminPasswordResetResponse(
        Long userId,
        String temporaryPassword,
        String message
) {}
