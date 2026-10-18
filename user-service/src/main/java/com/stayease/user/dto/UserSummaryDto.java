package com.stayease.user.dto;

/**
 * Summary DTO of a user returned inside AuthResponse.
 */
public record UserSummaryDto(
        Long id,
        String fullName,
        String email,
        String phone,
        String role
) {}
