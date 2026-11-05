package com.stayease.property.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Normalized error envelope returned by Property Service.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String correlationId,
        List<FieldErrorDto> fieldErrors
) {
    public ErrorResponse(Instant timestamp, int status, String error,
                         String code, String message, String path, String correlationId) {
        this(timestamp, status, error, code, message, path, correlationId, List.of());
    }
}
