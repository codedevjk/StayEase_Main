package com.stayease.property.service;

import com.stayease.property.entity.PropertyAuditEvent;
import com.stayease.property.repository.PropertyAuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service capturing immutable operational audit events in Property Service database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PropertyAuditService {

    private final PropertyAuditEventRepository auditEventRepository;

    @Transactional
    public void recordAudit(Long actorId, String actorRole, String eventCategory,
                            String action, String entityId, String description,
                            String beforeJson, String afterJson) {
        try {
            PropertyAuditEvent event = PropertyAuditEvent.builder()
                    .actorId(actorId != null ? actorId : 0L)
                    .actorRole(actorRole != null ? actorRole : "SYSTEM")
                    .eventCategory(eventCategory)
                    .action(action)
                    .entityId(entityId)
                    .description(description)
                    .beforeJson(beforeJson)
                    .afterJson(afterJson)
                    .build();

            auditEventRepository.save(event);
            log.debug("Recorded property audit event: {} for entity {}", action, entityId);
        } catch (Exception ex) {
            log.error("Failed to record property audit event for action {}: {}", action, ex.getMessage());
        }
    }
}
