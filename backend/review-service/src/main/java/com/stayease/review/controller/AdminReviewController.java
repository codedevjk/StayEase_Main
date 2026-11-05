package com.stayease.review.controller;

import com.stayease.review.dto.AuditEventDto;
import com.stayease.review.dto.PageResponse;
import com.stayease.review.dto.RemoveReviewRequest;
import com.stayease.review.dto.ReviewDto;
import com.stayease.review.security.UserPrincipal;
import com.stayease.review.service.ReviewAuditService;
import com.stayease.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminReviewController {

    private final ReviewService reviewService;
    private final ReviewAuditService auditService;

    /**
     * Admin moderation endpoint to remove inappropriate reviews with a documented reason.
     */
    @PostMapping("/reviews/{reviewId}/remove")
    public ResponseEntity<ReviewDto> removeReview(
            @PathVariable Long reviewId,
            @Valid @RequestBody RemoveReviewRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        ReviewDto response = reviewService.removeReview(reviewId, request, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * Queries Review Service audit trail.
     */
    @GetMapping("/audit")
    public ResponseEntity<PageResponse<AuditEventDto>> getAuditTrail(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String entityId,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {

        PageResponse<AuditEventDto> response = auditService.queryAuditTrail(from, to, category, actorId, entityId, pageable);
        return ResponseEntity.ok(response);
    }
}
