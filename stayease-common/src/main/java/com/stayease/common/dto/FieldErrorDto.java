package com.stayease.common.dto;

/**
 * Represents a single field-level validation error within an {@link ErrorResponse}.
 * Used in HTTP 422 Unprocessable Entity responses.
 *
 * <p>Example JSON:
 * <pre>{@code
 * {
 *   "field": "checkOutDate",
 *   "rejectedValue": "2026-10-01",
 *   "message": "Check-out date must be after check-in date"
 * }
 * }</pre>
 */
public record FieldErrorDto(

        /** The name of the field that failed validation. */
        String field,

        /** The value that was rejected (as a String representation). */
        String rejectedValue,

        /** Human-readable description of the validation failure. */
        String message
) {}
