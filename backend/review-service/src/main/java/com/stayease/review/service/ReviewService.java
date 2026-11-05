package com.stayease.review.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stayease.review.client.ReservationServiceClient;
import com.stayease.review.dto.*;
import com.stayease.review.entity.Review;
import com.stayease.review.enums.ReviewStatus;
import com.stayease.review.exception.DuplicateReviewException;
import com.stayease.review.exception.ResourceNotFoundException;
import com.stayease.review.exception.ReviewIneligibleException;
import com.stayease.review.repository.ReviewRepository;
import com.stayease.review.security.UserPrincipal;
import com.stayease.review.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReservationServiceClient reservationServiceClient;
    private final ReviewAuditService auditService;
    private final MessageSource messageSource;
    private final ObjectMapper objectMapper;

    private String resolveMessage(String code, String defaultMessage) {
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(code, null, defaultMessage, locale);
    }

    /**
     * Creates a verified review for a CHECKED_OUT reservation within 30 days of stay.
     * Enforces single review per reservation both logically and at database constraint level.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReviewDto createReview(CreateReviewRequest request, UserPrincipal currentUser) {
        Long reservationId = request.getReservationId();

        // 1. Check if review already exists for this reservation
        if (reviewRepository.existsByReservationId(reservationId)) {
            String msg = resolveMessage("review.duplicate", "Guest has already reviewed this reservation");
            log.warn("Duplicate review rejected for reservation {}: already reviewed", reservationId);
            throw new DuplicateReviewException(msg);
        }

        // 2. Query Reservation Service to verify eligibility (fail-closed)
        ReviewEligibilityResponse eligibility = reservationServiceClient.checkReviewEligibility(reservationId, currentUser.getId());

        if (!eligibility.isEligible()) {
            String reason = eligibility.getReason();
            log.warn("Review rejected for reservation {}: reason={}", reservationId, reason);

            if (reason != null && reason.contains("not found")) {
                throw new ResourceNotFoundException("Reservation with ID " + reservationId + " not found");
            }
            if (reason != null && (reason.contains("user") || reason.contains("not belong"))) {
                String msg = resolveMessage("review.not.owner", "You can only review reservations that you booked");
                throw new ReviewIneligibleException(msg);
            }
            if (reason != null && (reason.contains("CHECKED_OUT") || reason.contains("status"))) {
                String msg = resolveMessage("review.not.checked.out", "Reservation must be in CHECKED_OUT status to submit a review");
                throw new ReviewIneligibleException(msg);
            }
            if (reason != null && (reason.contains("30 days") || reason.contains("expired"))) {
                String msg = resolveMessage("review.window.expired", "Review submission window has expired (must be within 30 days of checkout)");
                throw new ReviewIneligibleException(msg);
            }

            String msg = resolveMessage("review.ineligible", "Guest has not completed a verified stay at this property");
            throw new ReviewIneligibleException(reason != null ? reason : msg);
        }

        String guestName = eligibility.getGuestName();
        if (guestName == null || guestName.isBlank()) {
            guestName = "Guest " + currentUser.getId();
        }

        Review review = Review.builder()
                .reservationId(reservationId)
                .userId(currentUser.getId())
                .hotelId(eligibility.getHotelId())
                .guestName(guestName)
                .rating(request.getRating())
                .comment(request.getComment())
                .status(ReviewStatus.PUBLISHED)
                .createdAt(Instant.now())
                .build();

        try {
            Review saved = reviewRepository.save(review);

            auditService.recordEvent(
                    currentUser.getId(),
                    currentUser.getRole(),
                    "REVIEW_CREATION",
                    "REVIEW_PUBLISHED",
                    String.valueOf(saved.getId()),
                    "Guest published verified review for reservation " + reservationId,
                    null,
                    "{\"reviewId\":" + saved.getId() + ",\"reservationId\":" + reservationId + ",\"rating\":" + saved.getRating() + "}"
            );

            log.info("Published review {} for reservation {} and hotel {}", saved.getId(), reservationId, saved.getHotelId());
            return mapToDto(saved);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent duplicate review detected for reservation {}: {}", reservationId, ex.getMessage());
            String msg = resolveMessage("review.duplicate", "Guest has already reviewed this reservation");
            throw new DuplicateReviewException(msg);
        }
    }

    /**
     * Lists published reviews for a given hotel with pagination.
     */
    @Transactional(readOnly = true)
    public PageResponse<ReviewDto> getReviewsForHotel(Long hotelId, Pageable pageable) {
        Page<Review> page = reviewRepository.findByHotelIdAndStatus(hotelId, ReviewStatus.PUBLISHED, pageable);
        Page<ReviewDto> dtoPage = page.map(this::mapToDto);
        return PageUtils.toPageResponse(dtoPage);
    }

    /**
     * Lists reviews submitted by a specific user.
     */
    @Transactional(readOnly = true)
    public PageResponse<ReviewDto> getReviewsByUserId(Long userId, Pageable pageable) {
        Page<Review> page = reviewRepository.findByUserId(userId, pageable);
        Page<ReviewDto> dtoPage = page.map(this::mapToDto);
        return PageUtils.toPageResponse(dtoPage);
    }

    /**
     * Retrieves review for a specific reservation (if exists).
     */
    @Transactional(readOnly = true)
    public ReviewDto getReviewByReservationId(Long reservationId) {
        return reviewRepository.findByReservationId(reservationId)
                .map(this::mapToDto)
                .orElse(null);
    }

    /**
     * Retrieves a single review by ID.
     */
    @Transactional(readOnly = true)
    public ReviewDto getReviewById(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review with ID " + reviewId + " not found"));
        return mapToDto(review);
    }

    /**
     * Admin moderation: removes a review with a mandatory reason and records an audit event.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReviewDto removeReview(Long reviewId, RemoveReviewRequest request, UserPrincipal currentUser) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review with ID " + reviewId + " not found"));

        if (review.getStatus() == ReviewStatus.REMOVED) {
            log.info("Review {} is already in REMOVED status; returning idempotent result", reviewId);
            return mapToDto(review);
        }

        String beforeJson = "{\"id\":" + review.getId() + ",\"status\":\"" + review.getStatus() + "\"}";

        review.setStatus(ReviewStatus.REMOVED);
        review.setRemovedBy(currentUser.getId());
        review.setRemovedAt(Instant.now());
        review.setRemovalReason(request.getReason());

        Review saved = reviewRepository.save(review);

        String afterJson = "{\"id\":" + saved.getId() + ",\"status\":\"REMOVED\",\"removalReason\":\"" + request.getReason() + "\"}";

        auditService.recordEvent(
                currentUser.getId(),
                currentUser.getRole(),
                "REVIEW_MODERATION",
                "REVIEW_REMOVED",
                String.valueOf(saved.getId()),
                "Admin removed review: " + request.getReason(),
                beforeJson,
                afterJson
        );

        log.info("Admin {} removed review {}: reason={}", currentUser.getId(), reviewId, request.getReason());
        return mapToDto(saved);
    }

    /**
     * Calculates published review statistics (average rating, review count) for a hotel.
     */
    @Transactional(readOnly = true)
    public ReviewSummaryDto getHotelRatingSummary(Long hotelId) {
        Double avgRating = reviewRepository.calculateAverageRating(hotelId, ReviewStatus.PUBLISHED);
        Long count = reviewRepository.countByHotelIdAndStatus(hotelId, ReviewStatus.PUBLISHED);

        double roundedAvg = 0.0;
        if (avgRating != null) {
            roundedAvg = BigDecimal.valueOf(avgRating)
                    .setScale(2, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        return ReviewSummaryDto.builder()
                .hotelId(hotelId)
                .averageRating(roundedAvg)
                .reviewCount(count != null ? count : 0L)
                .build();
    }

    private ReviewDto mapToDto(Review review) {
        return ReviewDto.builder()
                .id(review.getId())
                .reservationId(review.getReservationId())
                .hotelId(review.getHotelId())
                .guestName(review.getGuestName())
                .rating(review.getRating())
                .comment(review.getComment())
                .status(review.getStatus())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
