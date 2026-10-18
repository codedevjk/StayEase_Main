package com.stayease.user.dto;

/**
 * Internal response payload for service-to-service token version verification.
 * (GET /internal/users/{userId}/token-version).
 */
public record TokenVersionResponse(
        Long userId,
        Long tokenVersion
) {}
