package com.stayease.review.dto;

import lombok.*;

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
