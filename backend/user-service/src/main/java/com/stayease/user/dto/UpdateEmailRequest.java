package com.stayease.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for changing account email (PATCH /api/users/me/email).
 *
 * <p>Requires current password verification. Upon success, tokenVersion is
 * incremented, revoking existing sessions.
 */
public record UpdateEmailRequest(

        @NotBlank(message = "Current password is required")
        String currentPassword,

        @NotBlank(message = "New email is required")
        @Email(message = "New email must be a valid email address")
        @Size(max = 150, message = "Email must not exceed 150 characters")
        String newEmail
) {}
