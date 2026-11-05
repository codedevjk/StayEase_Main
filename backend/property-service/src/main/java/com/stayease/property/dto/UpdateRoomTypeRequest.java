package com.stayease.property.dto;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateRoomTypeRequest {

    @NotBlank(message = "Room type name is required")
    @Size(min = 2, max = 100, message = "Room type name must be between 2 and 100 characters")
    private String name;

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    @NotNull(message = "Maximum guests is required")
    @Min(value = 1, message = "Maximum guests must be at least 1")
    @Max(value = 20, message = "Maximum guests cannot exceed 20")
    private Integer maxGuests;

    @NotNull(message = "Base nightly rate is required")
    @DecimalMin(value = "0.01", message = "Base nightly rate must be greater than zero")
    private BigDecimal baseNightlyRate;
}
