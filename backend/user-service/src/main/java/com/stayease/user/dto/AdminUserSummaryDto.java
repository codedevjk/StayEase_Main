package com.stayease.user.dto;

import java.time.Instant;

/**
 * Summary DTO of a user returned to administrators (GET /api/admin/users).
 */
public record AdminUserSummaryDto(
        Long id,
        String fullName,
        String email,
        String phone,
        String role,
        Long tokenVersion,
        Instant createdAt
) {}
