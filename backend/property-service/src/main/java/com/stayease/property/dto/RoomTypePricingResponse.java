package com.stayease.property.dto;

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
public class RoomTypePricingResponse {
    private Long roomTypeId;
    private BigDecimal baseNightlyRate;
    private List<NightlyRateDto> nightlyRates;
}
