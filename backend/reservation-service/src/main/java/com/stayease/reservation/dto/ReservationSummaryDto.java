package com.stayease.reservation.dto;

import com.stayease.reservation.enums.PaymentPolicy;
import com.stayease.reservation.enums.PaymentStatus;
import com.stayease.reservation.enums.ReservationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationSummaryDto {
    private Long id;
    private String publicReference;
    private Long userId;
    private Long hotelId;
    private Long roomTypeId;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private Integer nights;
    private Integer guestCount;
    private ReservationStatus reservationStatus;
    private PaymentPolicy paymentPolicy;
    private PaymentStatus paymentStatus;
    private BigDecimal totalAmount;
    private Instant createdAt;
}
