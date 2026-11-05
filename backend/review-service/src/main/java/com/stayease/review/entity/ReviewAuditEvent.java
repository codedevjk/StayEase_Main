package com.stayease.review.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "review_audit_events",
        indexes = {
                @Index(name = "idx_rev_audit_timestamp", columnList = "timestamp"),
                @Index(name = "idx_rev_audit_actor", columnList = "actor_id"),
                @Index(name = "idx_rev_audit_category", columnList = "event_category")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(name = "actor_role", nullable = false, length = 20)
    private String actorRole;

    @Column(name = "event_category", nullable = false, length = 50)
    private String eventCategory;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "entity_id", nullable = false, length = 100)
    private String entityId;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "before_json", columnDefinition = "TEXT")
    private String beforeJson;

    @Column(name = "after_json", columnDefinition = "TEXT")
    private String afterJson;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }
}
