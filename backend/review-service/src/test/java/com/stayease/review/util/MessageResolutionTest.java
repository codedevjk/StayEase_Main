package com.stayease.review.util;

import com.stayease.review.client.ReservationServiceClient;
import com.stayease.review.client.UserServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class MessageResolutionTest {

    @MockBean
    private UserServiceClient userServiceClient;

    @MockBean
    private ReservationServiceClient reservationServiceClient;

    @Autowired
    private MessageSource messageSource;

    @Test
    @DisplayName("Should resolve validation messages for review requests")
    void shouldResolveReviewValidationMessages() {
        String resIdReq = messageSource.getMessage("NotNull.createReviewRequest.reservationId", null, Locale.getDefault());
        assertEquals("Reservation ID is required", resIdReq);

        String ratingReq = messageSource.getMessage("NotNull.createReviewRequest.rating", null, Locale.getDefault());
        assertEquals("Rating is required", ratingReq);

        String ratingMin = messageSource.getMessage("Min.createReviewRequest.rating", null, Locale.getDefault());
        assertEquals("Rating must be between 1 and 5", ratingMin);

        String ratingMax = messageSource.getMessage("Max.createReviewRequest.rating", null, Locale.getDefault());
        assertEquals("Rating must be between 1 and 5", ratingMax);

        String commentReq = messageSource.getMessage("NotBlank.createReviewRequest.comment", null, Locale.getDefault());
        assertEquals("Review comment is required", commentReq);

        String commentSize = messageSource.getMessage("Size.createReviewRequest.comment", null, Locale.getDefault());
        assertEquals("Review comment must be between 10 and 1000 characters", commentSize);

        String reasonReq = messageSource.getMessage("NotBlank.removeReviewRequest.reason", null, Locale.getDefault());
        assertEquals("Removal reason is required", reasonReq);
    }

    @Test
    @DisplayName("Should resolve business error messages for review operations")
    void shouldResolveBusinessErrorMessages() {
        String notFound = messageSource.getMessage("review.not.found", null, Locale.getDefault());
        assertEquals("Review not found", notFound);

        String ineligible = messageSource.getMessage("review.ineligible", null, Locale.getDefault());
        assertEquals("Guest has not completed a verified stay at this property", ineligible);

        String duplicate = messageSource.getMessage("review.duplicate", null, Locale.getDefault());
        assertEquals("Guest has already reviewed this reservation", duplicate);

        String ratingInvalid = messageSource.getMessage("review.rating.invalid", null, Locale.getDefault());
        assertEquals("Rating must be between 1 and 5", ratingInvalid);

        String windowExpired = messageSource.getMessage("review.window.expired", null, Locale.getDefault());
        assertEquals("Review submission window has expired (must be within 30 days of checkout)", windowExpired);

        String notCheckedOut = messageSource.getMessage("review.not.checked.out", null, Locale.getDefault());
        assertEquals("Reservation must be in CHECKED_OUT status to submit a review", notCheckedOut);

        String notOwner = messageSource.getMessage("review.not.owner", null, Locale.getDefault());
        assertEquals("You can only review reservations that you booked", notOwner);
    }

    @Test
    @DisplayName("Should handle missing keys safely with fallback default message")
    void shouldHandleMissingKeysWithFallback() {
        String defaultMsg = messageSource.getMessage("unknown.review.code", null, "Default Review Msg", Locale.getDefault());
        assertEquals("Default Review Msg", defaultMsg);

        assertThrows(NoSuchMessageException.class, () ->
                messageSource.getMessage("non.existent.review.key", null, Locale.getDefault())
        );
    }
}
