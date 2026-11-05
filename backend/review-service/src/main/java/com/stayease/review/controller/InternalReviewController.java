package com.stayease.review.controller;

import com.stayease.review.dto.ReviewSummaryDto;
import com.stayease.review.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal inter-service endpoints queried by other microservices.
 * Not exposed through API Gateway to public clients.
 */
@RestController
@RequestMapping("/internal/hotels")
@RequiredArgsConstructor
public class InternalReviewController {

    private final ReviewService reviewService;

    @GetMapping("/{hotelId}/rating-summary")
    public ResponseEntity<ReviewSummaryDto> getHotelRatingSummary(@PathVariable Long hotelId) {
        ReviewSummaryDto summary = reviewService.getHotelRatingSummary(hotelId);
        return ResponseEntity.ok(summary);
    }
}
