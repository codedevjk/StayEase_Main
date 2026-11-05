package com.stayease.property.dto;

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
public class NightlyRateDto {
    private LocalDate date;
    private BigDecimal effectiveRate;
    private String rateType; // BASE, WEEKEND, SEASONAL
}
