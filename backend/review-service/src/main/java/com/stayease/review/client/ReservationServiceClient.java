package com.stayease.review.client;

import com.stayease.review.dto.ReviewEligibilityResponse;
import com.stayease.review.exception.ResourceNotFoundException;
import com.stayease.review.exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;

/**
 * Inter-service WebClient calling Reservation Service to verify review eligibility.
 * Enforces fail-closed semantics: if Reservation Service is unavailable or times out,
 * review operations fail closed with HTTP 503 Service Unavailable.
 */
@Slf4j
@Component
public class ReservationServiceClient {

    private final WebClient webClient;

    @Autowired
    public ReservationServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${stayease.internal.reservation-service-url:http://stayease-reservation-service}") String reservationServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(reservationServiceUrl).build();
    }

    public ReviewEligibilityResponse checkReviewEligibility(Long reservationId, Long userId) {
        try {
            ReviewEligibilityResponse response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/reservations/{reservationId}/review-eligibility")
                            .queryParam("userId", userId)
                            .build(reservationId))
                    .retrieve()
                    .bodyToMono(ReviewEligibilityResponse.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();

            if (response == null) {
                log.error("Reservation Service returned null response for reservationId: {}", reservationId);
                throw new ServiceUnavailableException("Reservation Service returned empty response");
            }
            return response;
        } catch (WebClientResponseException.NotFound ex) {
            log.warn("Reservation {} not found in Reservation Service", reservationId);
            throw new ResourceNotFoundException("Reservation with ID " + reservationId + " not found");
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error communicating with Reservation Service for reservationId {}: {}", reservationId, ex.getMessage());
            throw new ServiceUnavailableException("Reservation Service is currently unavailable");
        }
    }
}
