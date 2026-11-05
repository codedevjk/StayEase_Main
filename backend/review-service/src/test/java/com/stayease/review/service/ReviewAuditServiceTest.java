package com.stayease.review.service;

import com.stayease.review.dto.AuditEventDto;
import com.stayease.review.dto.PageResponse;
import com.stayease.review.entity.ReviewAuditEvent;
import com.stayease.review.repository.ReviewAuditEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewAuditServiceTest {

    @Mock
    private ReviewAuditEventRepository auditEventRepository;

    @InjectMocks
    private ReviewAuditService auditService;

    @Test
    @DisplayName("Record event persists ReviewAuditEvent")
    void testRecordEventSuccess() {
        auditService.recordEvent(
                101L,
                "ROLE_USER",
                "REVIEW_CREATION",
                "REVIEW_PUBLISHED",
                "901",
                "Guest published review",
                null,
                "{\"status\":\"PUBLISHED\"}"
        );

        verify(auditEventRepository).save(any(ReviewAuditEvent.class));
    }

    @Test
    @DisplayName("Record event gracefully handles repository exception without throwing")
    void testRecordEventExceptionHandledGracefully() {
        doThrow(new RuntimeException("DB error")).when(auditEventRepository).save(any(ReviewAuditEvent.class));

        assertDoesNotThrow(() -> auditService.recordEvent(
                101L,
                "ROLE_USER",
                "REVIEW_CREATION",
                "REVIEW_PUBLISHED",
                "901",
                "Guest published review",
                null,
                null
        ));
    }

    @Test
    @DisplayName("Query audit trail maps results to PageResponse")
    void testQueryAuditTrail() {
        ReviewAuditEvent event = ReviewAuditEvent.builder()
                .id(1L)
                .actorId(1L)
                .actorRole("ADMIN")
                .eventCategory("REVIEW_MODERATION")
                .action("REVIEW_REMOVED")
                .entityId("901")
                .description("Admin removed review")
                .timestamp(Instant.now())
                .build();

        Page<ReviewAuditEvent> page = new PageImpl<>(List.of(event), PageRequest.of(0, 20), 1);
        when(auditEventRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        PageResponse<AuditEventDto> response = auditService.queryAuditTrail(
                Instant.now().minusSeconds(86400),
                Instant.now(),
                "REVIEW_MODERATION",
                1L,
                "901",
                PageRequest.of(0, 20)
        );

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals("REVIEW_SERVICE", response.getContent().get(0).getSourceService());
        assertEquals("REVIEW_MODERATION", response.getContent().get(0).getEventCategory());
    }
}
