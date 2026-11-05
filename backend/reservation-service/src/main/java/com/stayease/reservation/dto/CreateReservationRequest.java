package com.stayease.reservation.dto;

import com.stayease.reservation.enums.PaymentPolicy;
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
public class CreateReservationRequest {

    @NotNull(message = "{NotNull.createReservationRequest.hotelId}")
    private Long hotelId;

    @NotNull(message = "{NotNull.createReservationRequest.roomTypeId}")
    private Long roomTypeId;

    @NotNull(message = "{NotNull.createReservationRequest.checkInDate}")
    private LocalDate checkInDate;

    @NotNull(message = "{NotNull.createReservationRequest.checkOutDate}")
    private LocalDate checkOutDate;

    @NotNull(message = "{NotNull.createReservationRequest.guestCount}")
    @Min(value = 1, message = "{Min.createReservationRequest.guestCount}")
    private Integer guestCount;

    @Min(value = 1, message = "At least 1 adult is required to make a booking. Child-only bookings are not allowed.")
    private Integer adultCount;

    @Min(value = 0, message = "Child count cannot be negative")
    private Integer childCount;

    @NotNull(message = "{NotNull.createReservationRequest.paymentPolicy}")
    private PaymentPolicy paymentPolicy;
}
