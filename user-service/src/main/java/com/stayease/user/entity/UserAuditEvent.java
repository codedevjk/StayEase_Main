package com.stayease.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * JPA entity representing an audit event in stayease_user_db.
 *
 * <p>Persists security and account changes without passwords or sensitive tokens.
 */
@Entity
@Table(
        name = "user_audit_events",
        indexes = {
                @Index(name = "idx_audit_user_timestamp", columnList = "timestamp"),
                @Index(name = "idx_audit_user_actor", columnList = "actor_id"),
                @Index(name = "idx_audit_user_category", columnList = "event_category")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAuditEvent {

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_json")
    private String beforeJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_json")
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
