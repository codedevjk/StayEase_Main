package com.stayease.reservation.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyRoomTypeDto {
    private Long id;
    private String name;
    private String description;
    private Integer maxGuests;
    private BigDecimal baseNightlyRate;
    private Integer totalRooms;
}
