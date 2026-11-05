package com.stayease.review.entity;

import com.stayease.review.enums.ReviewStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "reviews",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_review_reservation", columnNames = "reservation_id")
        },
        indexes = {
                @Index(name = "idx_review_hotel_status", columnList = "hotel_id, status"),
                @Index(name = "idx_review_user", columnList = "user_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false, unique = true)
    private Long reservationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "hotel_id", nullable = false)
    private Long hotelId;

    @Column(name = "guest_name", nullable = false, length = 100)
    private String guestName;

    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "comment", nullable = false, columnDefinition = "TEXT")
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ReviewStatus status = ReviewStatus.PUBLISHED;

    @Column(name = "removed_by")
    private Long removedBy;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "removal_reason", length = 255)
    private String removalReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = ReviewStatus.PUBLISHED;
        }
    }
}
