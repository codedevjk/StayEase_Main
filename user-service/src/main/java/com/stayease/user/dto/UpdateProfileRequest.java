package com.stayease.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for updating user profile fields (PUT /api/users/me).
 */
public record UpdateProfileRequest(

        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
        String fullName,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[0-9]{10,15}$", message = "Phone must be between 10 and 15 digits")
        String phone,

        @Size(max = 255, message = "Address must not exceed 255 characters")
        String address
) {}
