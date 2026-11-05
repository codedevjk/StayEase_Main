package com.stayease.reservation.dto;

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
public class RoomTypeAvailabilityDto {
    private Long roomTypeId;
    private String name;
    private Integer maxGuests;
    private Integer availableRooms;
    private List<NightlyRateItemDto> nightlyRates;
    private BigDecimal lodgingSubtotal;
    private BigDecimal taxPercentage;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
}
