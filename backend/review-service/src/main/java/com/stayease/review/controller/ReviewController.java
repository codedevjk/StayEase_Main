package com.stayease.review.controller;

import com.stayease.review.dto.CreateReviewRequest;
import com.stayease.review.dto.PageResponse;
import com.stayease.review.dto.ReviewDto;
import com.stayease.review.dto.ReviewSummaryDto;
import com.stayease.review.security.UserPrincipal;
import com.stayease.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    /**
     * Submits a verified guest review for an eligible checked-out reservation.
     */
    @PostMapping("/api/reviews")
    public ResponseEntity<ReviewDto> createReview(
            @Valid @RequestBody CreateReviewRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        ReviewDto review = reviewService.createReview(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(review);
    }

    /**
     * Retrieves reviews submitted by the authenticated user.
     */
    @GetMapping("/api/reviews/my")
    public ResponseEntity<PageResponse<ReviewDto>> getMyReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        int clampedSize = Math.max(1, Math.min(size, 100));
        PageRequest pageRequest = PageRequest.of(page, clampedSize, Sort.by("createdAt").descending());
        PageResponse<ReviewDto> response = reviewService.getReviewsByUserId(currentUser.getId(), pageRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * Checks if a reservation has already been reviewed.
     */
    @GetMapping("/api/reviews/reservation/{reservationId}")
    public ResponseEntity<ReviewDto> getReviewByReservation(@PathVariable Long reservationId) {
        ReviewDto review = reviewService.getReviewByReservationId(reservationId);
        if (review == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(review);
    }

    /**
     * Public endpoint to list published reviews for a hotel.
     */
    @GetMapping({"/api/hotels/{hotelId}/reviews", "/api/reviews/hotels/{hotelId}", "/api/reviews/hotel/{hotelId}"})
    public ResponseEntity<PageResponse<ReviewDto>> getHotelReviews(
            @PathVariable Long hotelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int clampedSize = Math.max(1, Math.min(size, 100));
        PageRequest pageRequest = PageRequest.of(page, clampedSize, Sort.by("createdAt").descending());
        PageResponse<ReviewDto> response = reviewService.getReviewsForHotel(hotelId, pageRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * Public endpoint to retrieve an individual review by ID.
     */
    @GetMapping("/api/reviews/{reviewId}")
    public ResponseEntity<ReviewDto> getReviewById(@PathVariable Long reviewId) {
        ReviewDto review = reviewService.getReviewById(reviewId);
        return ResponseEntity.ok(review);
    }

    /**
     * Public endpoint to get aggregated hotel review metrics (average rating, review count).
     */
    @GetMapping({"/api/hotels/{hotelId}/reviews/summary", "/api/reviews/hotels/{hotelId}/summary"})
    public ResponseEntity<ReviewSummaryDto> getHotelRatingSummary(@PathVariable Long hotelId) {
        ReviewSummaryDto summary = reviewService.getHotelRatingSummary(hotelId);
        return ResponseEntity.ok(summary);
    }
}
