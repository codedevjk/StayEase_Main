package com.stayease.reservation.dto;

import com.stayease.reservation.enums.ReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckOutResponseDto {
    private Long reservationId;
    private ReservationStatus reservationStatus;
    private Instant checkedOutAt;
}
