package com.stayease.property.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateRateOverrideRequest {

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    @NotBlank(message = "Override type is required")
    @Pattern(regexp = "^(WEEKEND|SEASONAL)$", message = "Override type must be either WEEKEND or SEASONAL")
    private String overrideType;

    @NotNull(message = "Nightly rate is required")
    @DecimalMin(value = "0.01", message = "Nightly rate must be greater than zero")
    private BigDecimal nightlyRate;
}
