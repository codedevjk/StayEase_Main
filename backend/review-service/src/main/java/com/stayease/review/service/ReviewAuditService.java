package com.stayease.review.service;

import com.stayease.review.dto.AuditEventDto;
import com.stayease.review.dto.PageResponse;
import com.stayease.review.entity.ReviewAuditEvent;
import com.stayease.review.repository.ReviewAuditEventRepository;
import com.stayease.review.util.PageUtils;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewAuditService {

    private final ReviewAuditEventRepository auditEventRepository;

    @Transactional
    public void recordEvent(Long actorId,
                            String actorRole,
                            String eventCategory,
                            String action,
                            String entityId,
                            String description,
                            String beforeJson,
                            String afterJson) {
        try {
            ReviewAuditEvent event = ReviewAuditEvent.builder()
                    .actorId(actorId != null ? actorId : 0L)
                    .actorRole(actorRole != null ? actorRole : "SYSTEM")
                    .eventCategory(eventCategory)
                    .action(action)
                    .entityId(entityId)
                    .description(description)
                    .beforeJson(beforeJson)
                    .afterJson(afterJson)
                    .timestamp(Instant.now())
                    .build();

            auditEventRepository.save(event);
            log.debug("Recorded review audit event: action={}, entityId={}", action, entityId);
        } catch (Exception ex) {
            log.error("Failed to persist review audit event: {}", ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventDto> queryAuditTrail(
            Instant from,
            Instant to,
            String category,
            Long actorId,
            String entityId,
            Pageable pageable) {

        Specification<ReviewAuditEvent> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), to));
            }
            if (StringUtils.hasText(category)) {
                predicates.add(cb.equal(root.get("eventCategory"), category));
            }
            if (actorId != null) {
                predicates.add(cb.equal(root.get("actorId"), actorId));
            }
            if (StringUtils.hasText(entityId)) {
                predicates.add(cb.equal(root.get("entityId"), entityId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<ReviewAuditEvent> page = auditEventRepository.findAll(spec, pageable);
        Page<AuditEventDto> dtoPage = page.map(e -> AuditEventDto.builder()
                .id(e.getId())
                .sourceService("REVIEW_SERVICE")
                .actorId(e.getActorId())
                .actorRole(e.getActorRole())
                .eventCategory(e.getEventCategory())
                .action(e.getAction())
                .entityId(e.getEntityId())
                .description(e.getDescription())
                .beforeJson(e.getBeforeJson())
                .afterJson(e.getAfterJson())
                .timestamp(e.getTimestamp())
                .build());

        return PageUtils.toPageResponse(dtoPage);
    }
}
