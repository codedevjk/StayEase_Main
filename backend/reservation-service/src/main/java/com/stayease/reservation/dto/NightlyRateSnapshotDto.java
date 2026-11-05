package com.stayease.reservation.dto;

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
public class NightlyRateSnapshotDto {
    private LocalDate date;
    private BigDecimal rate;
    private String rateType;
}
