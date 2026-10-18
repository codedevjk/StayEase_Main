package com.stayease.user.dto;

/**
 * Envelope returned upon successful registration or login.
 */
public record AuthResponse(
        String token,
        UserSummaryDto user
) {}
