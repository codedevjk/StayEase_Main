package com.stayease.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewEligibilityResponse {
    private boolean eligible;
    private Long reservationId;
    private Long userId;
    private Long hotelId;
    private String guestName;
    private String reason;
}
