package com.stayease.reservation.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignCleanRoomRequest {
    private Long hotelId;
    private Long roomTypeId;
    private Long reservationId;
}
