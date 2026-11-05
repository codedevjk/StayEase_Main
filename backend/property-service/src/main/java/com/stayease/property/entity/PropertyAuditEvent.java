package com.stayease.property.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "property_audit_events",
        indexes = {
                @Index(name = "idx_prop_audit_timestamp", columnList = "timestamp"),
                @Index(name = "idx_prop_audit_actor", columnList = "actor_id"),
                @Index(name = "idx_prop_audit_category", columnList = "event_category")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyAuditEvent {

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

    @Column(name = "before_json", columnDefinition = "JSON")
    private String beforeJson;

    @Column(name = "after_json", columnDefinition = "JSON")
    private String afterJson;

    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;
}
