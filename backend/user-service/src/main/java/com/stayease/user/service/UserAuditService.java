package com.stayease.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stayease.user.dto.AuditEventDto;
import com.stayease.user.entity.UserAuditEvent;
import com.stayease.user.repository.UserAuditEventRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service managing user audit events in stayease_user_db.
 *
 * <p>Ensures security-sensitive changes are recorded without logging passwords,
 * tokens, or reset credentials.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAuditService {

    private final UserAuditEventRepository auditRepository;
    private final ObjectMapper objectMapper;

    /**
     * Records an audit event.
     * Uses REQUIRED propagation so it participates in the active transaction,
     * ensuring atomicity with the business operation.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public UserAuditEvent recordEvent(Long actorId,
                                      String actorRole,
                                      String eventCategory,
                                      String action,
                                      String entityId,
                                      String description,
                                      Object beforeState,
                                      Object afterState) {

        String beforeJson = serializeSafely(beforeState);
        String afterJson = serializeSafely(afterState);

        UserAuditEvent event = UserAuditEvent.builder()
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

        return auditRepository.save(event);
    }

    /**
     * Searches and paginates audit events for the administrator audit trail.
     */
    @Transactional(readOnly = true)
    public Page<AuditEventDto> searchAuditEvents(Instant from,
                                                 Instant to,
                                                 String category,
                                                 Long actorId,
                                                 String entityId,
                                                 Pageable pageable) {

        Specification<UserAuditEvent> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), to));
            }
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("eventCategory"), category));
            }
            if (actorId != null) {
                predicates.add(cb.equal(root.get("actorId"), actorId));
            }
            if (entityId != null && !entityId.isBlank()) {
                predicates.add(cb.equal(root.get("entityId"), entityId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return auditRepository.findAll(spec, pageable).map(this::toDto);
    }

    public AuditEventDto toDto(UserAuditEvent event) {
        return new AuditEventDto(
                event.getId(),
                "USER_SERVICE",
                event.getActorId(),
                event.getActorRole(),
                event.getEventCategory(),
                event.getAction(),
                event.getEntityId(),
                event.getDescription(),
                event.getTimestamp(),
                event.getBeforeJson(),
                event.getAfterJson()
        );
    }

    private String serializeSafely(Object state) {
        if (state == null) {
            return null;
        }
        if (state instanceof String string) {
            return string;
        }
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit state to JSON: {}", e.getMessage());
            return null;
        }
    }
}
