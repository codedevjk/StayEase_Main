package com.stayease.review.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stayease.review.client.ReservationServiceClient;
import com.stayease.review.dto.*;
import com.stayease.review.entity.Review;
import com.stayease.review.enums.ReviewStatus;
import com.stayease.review.exception.DuplicateReviewException;
import com.stayease.review.exception.ResourceNotFoundException;
import com.stayease.review.exception.ReviewIneligibleException;
import com.stayease.review.exception.ServiceUnavailableException;
import com.stayease.review.repository.ReviewRepository;
import com.stayease.review.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReservationServiceClient reservationServiceClient;

    @Mock
    private ReviewAuditService auditService;

    @Mock
    private MessageSource messageSource;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ReviewService reviewService;

    private UserPrincipal guestUser;
    private UserPrincipal adminUser;
    private CreateReviewRequest createRequest;

    @BeforeEach
    void setUp() {
        guestUser = UserPrincipal.create(101L, "jane.doe@example.com", "ROLE_USER", 1L);
        adminUser = UserPrincipal.create(1L, "admin@stayease.com", "ROLE_ADMIN", 1L);

        createRequest = CreateReviewRequest.builder()
                .reservationId(5001L)
                .rating(5)
                .comment("Exceptional stay! Beautiful view and excellent breakfast.")
                .build();
    }

    @Test
    @DisplayName("Create review succeeds for eligible checked-out reservation")
    void testCreateReviewSuccess() {
        ReviewEligibilityResponse eligibility = ReviewEligibilityResponse.builder()
                .eligible(true)
                .reservationId(5001L)
                .userId(101L)
                .hotelId(1L)
                .guestName("Jane D.")
                .reason("Eligible for review")
                .build();

        Review mockSaved = Review.builder()
                .id(901L)
                .reservationId(5001L)
                .userId(101L)
                .hotelId(1L)
                .guestName("Jane D.")
                .rating(5)
                .comment(createRequest.getComment())
                .status(ReviewStatus.PUBLISHED)
                .createdAt(Instant.now())
                .build();

        when(reviewRepository.existsByReservationId(5001L)).thenReturn(false);
        when(reservationServiceClient.checkReviewEligibility(5001L, 101L)).thenReturn(eligibility);
        when(reviewRepository.save(any(Review.class))).thenReturn(mockSaved);

        ReviewDto result = reviewService.createReview(createRequest, guestUser);

        assertNotNull(result);
        assertEquals(901L, result.getId());
        assertEquals(5001L, result.getReservationId());
        assertEquals(1L, result.getHotelId());
        assertEquals("Jane D.", result.getGuestName());
        assertEquals(5, result.getRating());
        assertEquals(ReviewStatus.PUBLISHED, result.getStatus());

        verify(reviewRepository).save(any(Review.class));
        verify(auditService).recordEvent(eq(101L), anyString(), eq("REVIEW_CREATION"), eq("REVIEW_PUBLISHED"), eq("901"), anyString(), isNull(), anyString());
    }

    @Test
    @DisplayName("Create review rejected when reservation was already reviewed")
    void testCreateReviewDuplicateThrowsConflict() {
        when(reviewRepository.existsByReservationId(5001L)).thenReturn(true);
        when(messageSource.getMessage(eq("review.duplicate"), isNull(), anyString(), any()))
                .thenReturn("Guest has already reviewed this reservation");

        DuplicateReviewException ex = assertThrows(DuplicateReviewException.class, () ->
                reviewService.createReview(createRequest, guestUser));

        assertTrue(ex.getMessage().contains("already reviewed"));
        verify(reservationServiceClient, never()).checkReviewEligibility(anyLong(), anyLong());
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("Create review rejected when reservation not found in Reservation Service")
    void testCreateReviewReservationNotFoundThrowsResourceNotFound() {
        ReviewEligibilityResponse eligibility = ReviewEligibilityResponse.builder()
                .eligible(false)
                .reservationId(5001L)
                .userId(101L)
                .reason("Reservation not found")
                .build();

        when(reviewRepository.existsByReservationId(5001L)).thenReturn(false);
        when(reservationServiceClient.checkReviewEligibility(5001L, 101L)).thenReturn(eligibility);

        assertThrows(ResourceNotFoundException.class, () ->
                reviewService.createReview(createRequest, guestUser));

        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("Create review rejected when reservation does not belong to authenticated guest")
    void testCreateReviewNotOwnerThrowsIneligible() {
        ReviewEligibilityResponse eligibility = ReviewEligibilityResponse.builder()
                .eligible(false)
                .reservationId(5001L)
                .userId(101L)
                .reason("Reservation does not belong to specified user")
                .build();

        when(reviewRepository.existsByReservationId(5001L)).thenReturn(false);
        when(reservationServiceClient.checkReviewEligibility(5001L, 101L)).thenReturn(eligibility);
        when(messageSource.getMessage(eq("review.not.owner"), isNull(), anyString(), any()))
                .thenReturn("You can only review reservations that you booked");

        ReviewIneligibleException ex = assertThrows(ReviewIneligibleException.class, () ->
                reviewService.createReview(createRequest, guestUser));

        assertTrue(ex.getMessage().contains("only review reservations that you booked"));
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("Create review rejected when reservation is not CHECKED_OUT")
    void testCreateReviewNotCheckedOutThrowsIneligible() {
        ReviewEligibilityResponse eligibility = ReviewEligibilityResponse.builder()
                .eligible(false)
                .reservationId(5001L)
                .userId(101L)
                .reason("Reservation is not in CHECKED_OUT status")
                .build();

        when(reviewRepository.existsByReservationId(5001L)).thenReturn(false);
        when(reservationServiceClient.checkReviewEligibility(5001L, 101L)).thenReturn(eligibility);
        when(messageSource.getMessage(eq("review.not.checked.out"), isNull(), anyString(), any()))
                .thenReturn("Reservation must be in CHECKED_OUT status to submit a review");

        ReviewIneligibleException ex = assertThrows(ReviewIneligibleException.class, () ->
                reviewService.createReview(createRequest, guestUser));

        assertTrue(ex.getMessage().contains("CHECKED_OUT"));
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("Create review rejected when completed stay was more than 30 days ago")
    void testCreateReviewWindowExpiredThrowsIneligible() {
        ReviewEligibilityResponse eligibility = ReviewEligibilityResponse.builder()
                .eligible(false)
                .reservationId(5001L)
                .userId(101L)
                .reason("Review submission window expired (> 30 days since checkout)")
                .build();

        when(reviewRepository.existsByReservationId(5001L)).thenReturn(false);
        when(reservationServiceClient.checkReviewEligibility(5001L, 101L)).thenReturn(eligibility);
        when(messageSource.getMessage(eq("review.window.expired"), isNull(), anyString(), any()))
                .thenReturn("Review submission window has expired (must be within 30 days of checkout)");

        ReviewIneligibleException ex = assertThrows(ReviewIneligibleException.class, () ->
                reviewService.createReview(createRequest, guestUser));

        assertTrue(ex.getMessage().contains("30 days"));
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("Create review fails closed when Reservation Service is unavailable")
    void testCreateReviewDownstreamFailureFailsClosed() {
        when(reviewRepository.existsByReservationId(5001L)).thenReturn(false);
        when(reservationServiceClient.checkReviewEligibility(5001L, 101L))
                .thenThrow(new ServiceUnavailableException("Reservation Service is currently unavailable"));

        assertThrows(ServiceUnavailableException.class, () ->
                reviewService.createReview(createRequest, guestUser));

        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    @DisplayName("Create review translates concurrent database constraint violation to DuplicateReviewException")
    void testCreateReviewDataIntegrityViolationTranslatesToDuplicate() {
        ReviewEligibilityResponse eligibility = ReviewEligibilityResponse.builder()
                .eligible(true)
                .reservationId(5001L)
                .userId(101L)
                .hotelId(1L)
                .guestName("Jane D.")
                .reason("Eligible")
                .build();

        when(reviewRepository.existsByReservationId(5001L)).thenReturn(false);
        when(reservationServiceClient.checkReviewEligibility(5001L, 101L)).thenReturn(eligibility);
        when(reviewRepository.save(any(Review.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for uq_review_reservation"));
        when(messageSource.getMessage(eq("review.duplicate"), isNull(), anyString(), any()))
                .thenReturn("Guest has already reviewed this reservation");

        assertThrows(DuplicateReviewException.class, () ->
                reviewService.createReview(createRequest, guestUser));
    }

    @Test
    @DisplayName("Admin removes inappropriate review with documented reason and audit tracking")
    void testRemoveReviewSuccess() {
        Review existing = Review.builder()
                .id(901L)
                .reservationId(5001L)
                .userId(101L)
                .hotelId(1L)
                .guestName("Jane D.")
                .rating(1)
                .comment("Inappropriate abusive content")
                .status(ReviewStatus.PUBLISHED)
                .createdAt(Instant.now().minusSeconds(3600))
                .build();

        RemoveReviewRequest removeReq = RemoveReviewRequest.builder()
                .reason("Violates community guidelines")
                .build();

        when(reviewRepository.findById(901L)).thenReturn(Optional.of(existing));
        when(reviewRepository.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        ReviewDto result = reviewService.removeReview(901L, removeReq, adminUser);

        assertNotNull(result);
        assertEquals(ReviewStatus.REMOVED, result.getStatus());
        assertEquals(ReviewStatus.REMOVED, existing.getStatus());
        assertEquals("Violates community guidelines", existing.getRemovalReason());
        assertEquals(1L, existing.getRemovedBy());
        assertNotNull(existing.getRemovedAt());

        verify(auditService).recordEvent(
                eq(1L),
                eq("ROLE_ADMIN"),
                eq("REVIEW_MODERATION"),
                eq("REVIEW_REMOVED"),
                eq("901"),
                contains("Violates community guidelines"),
                contains("PUBLISHED"),
                contains("REMOVED")
        );
    }

    @Test
    @DisplayName("Admin removing an already removed review is idempotent")
    void testRemoveReviewAlreadyRemovedIsIdempotent() {
        Review existing = Review.builder()
                .id(901L)
                .reservationId(5001L)
                .userId(101L)
                .hotelId(1L)
                .status(ReviewStatus.REMOVED)
                .removalReason("Already removed")
                .build();

        RemoveReviewRequest removeReq = RemoveReviewRequest.builder()
                .reason("Duplicate removal attempt")
                .build();

        when(reviewRepository.findById(901L)).thenReturn(Optional.of(existing));

        ReviewDto result = reviewService.removeReview(901L, removeReq, adminUser);

        assertNotNull(result);
        assertEquals(ReviewStatus.REMOVED, result.getStatus());
        verify(reviewRepository, never()).save(any(Review.class));
        verify(auditService, never()).recordEvent(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("Admin removing non-existent review throws ResourceNotFoundException")
    void testRemoveReviewNotFoundThrowsResourceNotFound() {
        when(reviewRepository.findById(999L)).thenReturn(Optional.empty());

        RemoveReviewRequest removeReq = RemoveReviewRequest.builder()
                .reason("Test")
                .build();

        assertThrows(ResourceNotFoundException.class, () ->
                reviewService.removeReview(999L, removeReq, adminUser));
    }

    @Test
    @DisplayName("Hotel rating summary aggregates average rating and published count")
    void testGetHotelRatingSummary() {
        when(reviewRepository.calculateAverageRating(1L, ReviewStatus.PUBLISHED)).thenReturn(4.6666);
        when(reviewRepository.countByHotelIdAndStatus(1L, ReviewStatus.PUBLISHED)).thenReturn(12L);

        ReviewSummaryDto summary = reviewService.getHotelRatingSummary(1L);

        assertNotNull(summary);
        assertEquals(1L, summary.getHotelId());
        assertEquals(4.67, summary.getAverageRating());
        assertEquals(12L, summary.getReviewCount());
    }
}
