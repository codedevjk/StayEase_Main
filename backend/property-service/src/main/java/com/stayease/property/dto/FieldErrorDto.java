package com.stayease.property.dto;

/**
 * Represents a single field-level validation error within an {@link ErrorResponse}.
 */
public record FieldErrorDto(
        String field,
        String rejectedValue,
        String message
) {}
