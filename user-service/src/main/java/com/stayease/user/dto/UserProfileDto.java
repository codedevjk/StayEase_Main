package com.stayease.user.dto;

import java.time.Instant;

/**
 * Full profile representation of a user (GET /api/users/me, PUT /api/users/me, PATCH /api/users/me/email).
 *
 * <p>Never exposes passwords or sensitive credentials.
 */
public record UserProfileDto(
        Long id,
        String fullName,
        String email,
        String phone,
        String address,
        String role,
        Long tokenVersion,
        Instant createdAt
) {}
