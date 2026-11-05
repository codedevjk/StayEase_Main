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
public class RateOverrideDto {
    private Long id;
    private Long roomTypeId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String overrideType;
    private BigDecimal nightlyRate;
}
