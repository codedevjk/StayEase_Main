package com.stayease.review.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewSummaryDto {

    private Long hotelId;
    private Double averageRating;
    private Long reviewCount;
}
