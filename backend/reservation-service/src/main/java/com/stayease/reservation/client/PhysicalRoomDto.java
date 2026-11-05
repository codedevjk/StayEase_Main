package com.stayease.reservation.client;

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
    private String housekeepingStatus;
    private String operationalStatus;
    private Instant createdAt;
    private Instant updatedAt;
}
