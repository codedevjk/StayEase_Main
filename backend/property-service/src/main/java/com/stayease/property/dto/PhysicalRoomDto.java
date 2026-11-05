package com.stayease.property.dto;

import com.stayease.property.enums.HousekeepingStatus;
import com.stayease.property.enums.OperationalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhysicalRoomDto {
    private Long id;
    private Long hotelId;
    private Long roomTypeId;
    private String roomNumber;
    private HousekeepingStatus housekeepingStatus;
    private OperationalStatus operationalStatus;
    private Instant createdAt;
    private Instant updatedAt;
}
