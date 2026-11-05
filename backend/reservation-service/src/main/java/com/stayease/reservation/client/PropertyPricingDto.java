package com.stayease.reservation.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyPricingDto {
    private Long roomTypeId;
    private BigDecimal baseNightlyRate;
    private List<PropertyNightlyRateDto> nightlyRates;
}
