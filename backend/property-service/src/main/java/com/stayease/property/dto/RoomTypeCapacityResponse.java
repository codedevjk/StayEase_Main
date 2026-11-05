package com.stayease.property.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomTypeCapacityResponse {
    private Long roomTypeId;
    private Integer sellableCapacity;
    private Integer totalPhysicalRooms;
    private Integer outOfServiceRooms;
}
