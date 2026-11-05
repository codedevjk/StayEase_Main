package com.stayease.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stayease.review.client.ReservationServiceClient;
import com.stayease.review.client.UserServiceClient;
import com.stayease.review.dto.CreateReviewRequest;
import com.stayease.review.dto.RemoveReviewRequest;
import com.stayease.review.dto.ReviewEligibilityResponse;
import com.stayease.review.entity.Review;
import com.stayease.review.enums.ReviewStatus;
import com.stayease.review.exception.ServiceUnavailableException;
import com.stayease.review.repository.ReviewAuditEventRepository;
import com.stayease.review.repository.ReviewRepository;
import com.stayease.review.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewControllerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ReviewAuditEventRepository auditEventRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ReservationServiceClient reservationServiceClient;

    @MockBean
    private UserServiceClient userServiceClient;

    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        when(userServiceClient.getTokenVersion(anyLong())).thenReturn(Optional.of(1L));

        auditEventRepository.deleteAll();
        reviewRepository.deleteAll();

        userToken = "Bearer " + jwtTokenProvider.generateToken(101L, "jane.doe@example.com", "ROLE_USER", 1L);
        adminToken = "Bearer " + jwtTokenProvider.generateToken(1L, "admin@stayease.com", "ROLE_ADMIN", 1L);
    }

    @Test
    @DisplayName("POST /api/reviews succeeds for eligible checked-out reservation")
    void testCreateReviewSuccess() throws Exception {
        ReviewEligibilityResponse eligibility = ReviewEligibilityResponse.builder()
                .eligible(true)
                .reservationId(5001L)
                .userId(101L)
                .hotelId(1L)
                .guestName("Jane D.")
                .reason("Eligible for review")
                .build();

        when(reservationServiceClient.checkReviewEligibility(eq(5001L), eq(101L))).thenReturn(eligibility);

        CreateReviewRequest request = CreateReviewRequest.builder()
                .reservationId(5001L)
                .rating(5)
                .comment("Exceptional stay! The ocean balcony and morning breakfast were wonderful.")
                .build();

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", userToken)
                        .header("X-Correlation-ID", "corr-rev-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.reservationId").value(5001))
                .andExpect(jsonPath("$.hotelId").value(1))
                .andExpect(jsonPath("$.guestName").value("Jane D."))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        assertEquals(1, reviewRepository.count());
        assertEquals(1, auditEventRepository.count());
    }

    @Test
    @DisplayName("POST /api/reviews unauthenticated returns 401")
    void testCreateReviewUnauthenticatedReturns401() throws Exception {
        CreateReviewRequest request = CreateReviewRequest.builder()
                .reservationId(5001L)
                .rating(5)
                .comment("Exceptional stay!")
                .build();

        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("POST /api/reviews with invalid rating (< 1) returns 422")
    void testCreateReviewInvalidRatingLowReturns422() throws Exception {
        CreateReviewRequest request = CreateReviewRequest.builder()
                .reservationId(5001L)
                .rating(0)
                .comment("Terrible experience that needs ten characters.")
                .build();

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("rating"));
    }

    @Test
    @DisplayName("POST /api/reviews with invalid rating (> 5) returns 422")
    void testCreateReviewInvalidRatingHighReturns422() throws Exception {
        CreateReviewRequest request = CreateReviewRequest.builder()
                .reservationId(5001L)
                .rating(6)
                .comment("Super great stay exceeding expectations!")
                .build();

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("rating"));
    }

    @Test
    @DisplayName("POST /api/reviews with comment too short (< 10 chars) returns 422")
    void testCreateReviewShortCommentReturns422() throws Exception {
        CreateReviewRequest request = CreateReviewRequest.builder()
                .reservationId(5001L)
                .rating(4)
                .comment("Bad stay")
                .build();

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("comment"));
    }

    @Test
    @DisplayName("POST /api/reviews duplicate review returns 409 Conflict")
    void testCreateReviewDuplicateReturns409() throws Exception {
        Review existing = Review.builder()
                .reservationId(5001L)
                .userId(101L)
                .hotelId(1L)
                .guestName("Jane D.")
                .rating(4)
                .comment("First review for this stay.")
                .status(ReviewStatus.PUBLISHED)
                .createdAt(Instant.now())
                .build();
        reviewRepository.save(existing);

        CreateReviewRequest request = CreateReviewRequest.builder()
                .reservationId(5001L)
                .rating(5)
                .comment("Second attempt to review same reservation.")
                .build();

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REVIEW"));
    }

    @Test
    @DisplayName("POST /api/reviews ineligible reservation (not checked out) returns 403 Forbidden")
    void testCreateReviewNotCheckedOutReturns403() throws Exception {
        ReviewEligibilityResponse eligibility = ReviewEligibilityResponse.builder()
                .eligible(false)
                .reservationId(5001L)
                .userId(101L)
                .hotelId(1L)
                .reason("Reservation is not in CHECKED_OUT status")
                .build();

        when(reservationServiceClient.checkReviewEligibility(eq(5001L), eq(101L))).thenReturn(eligibility);

        CreateReviewRequest request = CreateReviewRequest.builder()
                .reservationId(5001L)
                .rating(4)
                .comment("Trying to review before checkout.")
                .build();

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REVIEW_INELIGIBLE"));
    }

    @Test
    @DisplayName("POST /api/reviews ineligible reservation (> 30 days) returns 403 Forbidden")
    void testCreateReviewWindowExpiredReturns403() throws Exception {
        ReviewEligibilityResponse eligibility = ReviewEligibilityResponse.builder()
                .eligible(false)
                .reservationId(5001L)
                .userId(101L)
                .hotelId(1L)
                .reason("Review submission window expired (> 30 days since checkout)")
                .build();

        when(reservationServiceClient.checkReviewEligibility(eq(5001L), eq(101L))).thenReturn(eligibility);

        CreateReviewRequest request = CreateReviewRequest.builder()
                .reservationId(5001L)
                .rating(4)
                .comment("Reviewing stay after 45 days.")
                .build();

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REVIEW_INELIGIBLE"));
    }

    @Test
    @DisplayName("POST /api/reviews non-existent reservation returns 404 Not Found")
    void testCreateReviewReservationNotFoundReturns404() throws Exception {
        ReviewEligibilityResponse eligibility = ReviewEligibilityResponse.builder()
                .eligible(false)
                .reservationId(9999L)
                .userId(101L)
                .reason("Reservation not found")
                .build();

        when(reservationServiceClient.checkReviewEligibility(eq(9999L), eq(101L))).thenReturn(eligibility);

        CreateReviewRequest request = CreateReviewRequest.builder()
                .reservationId(9999L)
                .rating(5)
                .comment("Reviewing non-existent reservation.")
                .build();

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/reviews fails closed when Reservation Service is unavailable (503)")
    void testCreateReviewDownstreamDownReturns503() throws Exception {
        when(reservationServiceClient.checkReviewEligibility(anyLong(), anyLong()))
                .thenThrow(new ServiceUnavailableException("Reservation Service is currently unavailable"));

        CreateReviewRequest request = CreateReviewRequest.builder()
                .reservationId(5001L)
                .rating(5)
                .comment("Exceptional stay! Breakfast was great.")
                .build();

        mockMvc.perform(post("/api/reviews")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    @DisplayName("GET /api/hotels/{hotelId}/reviews is publicly accessible and excludes REMOVED reviews")
    void testGetHotelReviewsPublicAccess() throws Exception {
        Review r1 = Review.builder()
                .reservationId(5001L).userId(101L).hotelId(1L).guestName("Jane D.").rating(5)
                .comment("Great stay").status(ReviewStatus.PUBLISHED).createdAt(Instant.now()).build();
        Review r2 = Review.builder()
                .reservationId(5002L).userId(102L).hotelId(1L).guestName("John D.").rating(4)
                .comment("Nice place").status(ReviewStatus.PUBLISHED).createdAt(Instant.now()).build();
        Review removed = Review.builder()
                .reservationId(5003L).userId(103L).hotelId(1L).guestName("Abusive").rating(1)
                .comment("Spam content").status(ReviewStatus.REMOVED).createdAt(Instant.now()).build();

        reviewRepository.save(r1);
        reviewRepository.save(r2);
        reviewRepository.save(removed);

        mockMvc.perform(get("/api/hotels/1/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));
    }

    @Test
    @DisplayName("GET /api/reviews/{id} retrieves published review by ID publicly")
    void testGetReviewByIdPublicAccess() throws Exception {
        Review r = Review.builder()
                .reservationId(5001L).userId(101L).hotelId(1L).guestName("Jane D.").rating(5)
                .comment("Great stay").status(ReviewStatus.PUBLISHED).createdAt(Instant.now()).build();
        Review saved = reviewRepository.save(r);

        mockMvc.perform(get("/api/reviews/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.rating").value(5));
    }

    @Test
    @DisplayName("GET /api/reviews/{id} returns 404 for non-existent review")
    void testGetReviewByIdNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/reviews/9999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/admin/reviews/{id}/remove as admin removes review and records audit")
    void testAdminRemoveReviewSuccess() throws Exception {
        Review r = Review.builder()
                .reservationId(5001L).userId(101L).hotelId(1L).guestName("Jane D.").rating(1)
                .comment("Inappropriate comment").status(ReviewStatus.PUBLISHED).createdAt(Instant.now()).build();
        Review saved = reviewRepository.save(r);

        RemoveReviewRequest request = RemoveReviewRequest.builder()
                .reason("Violates community content guidelines")
                .build();

        mockMvc.perform(post("/api/admin/reviews/" + saved.getId() + "/remove")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(saved.getId()))
                .andExpect(jsonPath("$.status").value("REMOVED"));

        Review updated = reviewRepository.findById(saved.getId()).orElseThrow();
        assertEquals(ReviewStatus.REMOVED, updated.getStatus());
        assertEquals("Violates community content guidelines", updated.getRemovalReason());
        assertEquals(1L, updated.getRemovedBy());
        assertEquals(1, auditEventRepository.count());
    }

    @Test
    @DisplayName("POST /api/admin/reviews/{id}/remove with USER role returns 403 Forbidden")
    void testAdminRemoveReviewForbiddenForUser() throws Exception {
        RemoveReviewRequest request = RemoveReviewRequest.builder()
                .reason("Violates guidelines")
                .build();

        mockMvc.perform(post("/api/admin/reviews/1/remove")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/admin/reviews/{id}/remove with blank reason returns 422")
    void testAdminRemoveReviewBlankReasonReturns422() throws Exception {
        RemoveReviewRequest request = RemoveReviewRequest.builder()
                .reason("")
                .build();

        mockMvc.perform(post("/api/admin/reviews/1/remove")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("GET /api/admin/audit with ADMIN role succeeds")
    void testAdminGetAuditTrailSuccess() throws Exception {
        mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("GET /api/admin/audit with USER role returns 403 Forbidden")
    void testAdminGetAuditTrailForbiddenForUser() throws Exception {
        mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/hotels/{hotelId}/reviews/summary returns rating aggregate")
    void testGetHotelRatingSummaryPublicAccess() throws Exception {
        Review r1 = Review.builder()
                .reservationId(5001L).userId(101L).hotelId(1L).guestName("Jane D.").rating(5)
                .comment("Excellent stay").status(ReviewStatus.PUBLISHED).createdAt(Instant.now()).build();
        Review r2 = Review.builder()
                .reservationId(5002L).userId(102L).hotelId(1L).guestName("John D.").rating(4)
                .comment("Very good").status(ReviewStatus.PUBLISHED).createdAt(Instant.now()).build();

        reviewRepository.save(r1);
        reviewRepository.save(r2);

        mockMvc.perform(get("/api/hotels/1/reviews/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hotelId").value(1))
                .andExpect(jsonPath("$.averageRating").value(4.5))
                .andExpect(jsonPath("$.reviewCount").value(2));
    }
}
