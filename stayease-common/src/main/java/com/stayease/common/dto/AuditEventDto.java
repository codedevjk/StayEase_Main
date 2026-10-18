package com.stayease.common.dto;

import java.time.Instant;

/**
 * Shared DTO for audit event records returned by each service's
 * {@code GET /api/admin/audit} endpoint.
 *
 * <p>The Angular admin audit view queries all four service audit endpoints
 * in parallel and merges the results on the client side, sorted by
 * {@code timestamp} descending.
 *
 * <p>Each service's audit controller maps its internal audit entity to this
 * shared DTO and returns it within a {@link PageResponse}.
 *
 * <p>Sensitive data must NEVER appear in audit records:
 * no passwords, JWTs, or reset credentials.
 */
public record AuditEventDto(

        /** Internal audit record ID (unique within the originating service). */
        Long id,

        /**
         * Service name prefix allowing the UI to identify the origin.
         * Values: USER_SERVICE, PROPERTY_SERVICE, RESERVATION_SERVICE, REVIEW_SERVICE.
         */
        String sourceService,

        /**
         * ID of the actor who triggered the event.
         * Use 0 (or null) for SYSTEM-initiated events (schedulers, expiry tasks).
         */
        Long actorId,

        /** Role of the actor: USER, ADMIN, or SYSTEM. */
        String actorRole,

        /**
         * Broad event category for filtering.
         * Examples: AUTH, RESERVATION, PAYMENT, HOUSEKEEPING, ROOM_OPERATION,
         *           REVIEW_MODERATION, ACCOUNT.
         */
        String eventCategory,

        /**
         * Fine-grained action name within the category.
         * Examples: USER_LOGIN, RESERVATION_CONFIRMED, CHECK_IN, ROOM_STATUS_CHANGE.
         */
        String action,

        /** Primary entity identifier (e.g. reservation ID, hotel ID, user ID). */
        String entityId,

        /** Human-readable description of the event (max 255 chars). */
        String description,

        /** UTC instant when the event occurred. */
        Instant timestamp,

        /**
         * JSON snapshot of the entity's state BEFORE the change.
         * Null for creation events.
         */
        String beforeJson,

        /**
         * JSON snapshot of the entity's state AFTER the change.
         * Null for deletion events.
         */
        String afterJson
) {}
