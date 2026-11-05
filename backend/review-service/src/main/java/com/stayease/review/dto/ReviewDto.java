package com.stayease.review.dto;

import com.stayease.review.enums.ReviewStatus;
import lombok.*;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewDto {

    private Long id;
    private Long reservationId;
    private Long hotelId;
    private String guestName;
    private Integer rating;
    private String comment;
    private ReviewStatus status;
    private Instant createdAt;
}
