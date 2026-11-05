package com.stayease.user.dto;

import java.time.Instant;

/**
 * DTO for user audit event records returned by GET /api/admin/audit/users.
 */
public record AuditEventDto(
        Long id,
        String sourceService,
        Long actorId,
        String actorRole,
        String eventCategory,
        String action,
        String entityId,
        String description,
        Instant timestamp,
        String beforeJson,
        String afterJson
) {}
