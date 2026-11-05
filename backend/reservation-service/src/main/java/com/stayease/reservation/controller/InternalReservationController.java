package com.stayease.reservation.controller;

import com.stayease.reservation.dto.ReviewEligibilityResponse;
import com.stayease.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal inter-service endpoints queried by other microservices (e.g. Review Service).
 * Not exposed through API Gateway to public clients.
 */
@RestController
@RequestMapping("/internal/reservations")
@RequiredArgsConstructor
public class InternalReservationController {

    private final ReservationService reservationService;

    /**
     * Verifies that a reservation is eligible for review submission:
     * - Must exist and belong to userId
     * - Must be in CHECKED_OUT status
     * - Must be within 30 days of checkout
     */
    @GetMapping("/{reservationId}/review-eligibility")
    public ResponseEntity<ReviewEligibilityResponse> getReviewEligibility(
            @PathVariable Long reservationId,
            @RequestParam Long userId) {

        ReviewEligibilityResponse response = reservationService.getReviewEligibility(reservationId, userId);
        return ResponseEntity.ok(response);
    }
}
