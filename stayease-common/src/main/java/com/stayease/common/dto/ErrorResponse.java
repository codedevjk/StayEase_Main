package com.stayease.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Normalized error envelope returned by every StayEase microservice.
 *
 * <p>All {@code @RestControllerAdvice} implementations must map exceptions to
 * this DTO. The {@code correlationId} must be populated from the
 * {@code X-Correlation-ID} request header, which is injected by the Gateway
 * and propagated through inter-service calls via MDC.
 *
 * <p>Example JSON:
 * <pre>{@code
 * {
 *   "timestamp": "2026-10-18T16:45:00.123Z",
 *   "status": 409,
 *   "error": "Conflict",
 *   "code": "INSUFFICIENT_INVENTORY",
 *   "message": "Selected room type has no available capacity for requested dates",
 *   "path": "/api/reservations",
 *   "correlationId": "c4b9e28f-7c1a-4d2a-89a3-53d9e8b7c012",
 *   "fieldErrors": []
 * }
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(

        /** UTC timestamp of when the error was generated. */
        Instant timestamp,

        /** HTTP status code (e.g. 400, 401, 403, 404, 409, 422, 500, 503). */
        int status,

        /** HTTP status reason phrase (e.g. "Conflict"). */
        String error,

        /**
         * Application-specific error code for programmatic error handling.
         * Examples: INSUFFICIENT_INVENTORY, DUPLICATE_EMAIL, INVALID_TOKEN,
         *           RESERVATION_NOT_FOUND, SERVICE_UNAVAILABLE.
         */
        String code,

        /** Human-readable description of the error. */
        String message,

        /** Request path that produced the error. */
        String path,

        /**
         * Correlation ID propagated from the Gateway's {@code X-Correlation-ID} header.
         * Must appear in all error responses to enable distributed tracing.
         */
        String correlationId,

        /**
         * Field-level validation errors (populated for 422 Unprocessable Entity only).
         * Null or empty for all other error types.
         */
        List<FieldErrorDto> fieldErrors
) {

    /**
     * Convenience constructor for errors without field-level detail.
     */
    public ErrorResponse(Instant timestamp, int status, String error,
                         String code, String message, String path, String correlationId) {
        this(timestamp, status, error, code, message, path, correlationId, List.of());
    }
}
