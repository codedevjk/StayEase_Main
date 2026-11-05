package com.stayease.reservation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AmendReservationRequest {

    @NotNull(message = "New check-in date is required")
    private LocalDate newCheckInDate;

    @NotNull(message = "New check-out date is required")
    private LocalDate newCheckOutDate;

    private Long newRoomTypeId;

    @Min(value = 1, message = "Guest count must be at least 1")
    private Integer newGuestCount;
}
