package com.stayease.reservation.client;

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
public class PropertyNightlyRateDto {
    private LocalDate date;
    private BigDecimal effectiveRate;
    private String rateType;
}
