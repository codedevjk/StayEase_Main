package com.stayease.reservation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "room_assignments",
        indexes = {
                @Index(name = "idx_ra_room", columnList = "physical_room_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reservation_id", nullable = false, unique = true)
    private Long reservationId;

    @Column(name = "physical_room_id", nullable = false)
    private Long physicalRoomId;

    @Column(name = "physical_room_number", nullable = false, length = 20)
    private String physicalRoomNumber;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "checked_in_at", nullable = false)
    private Instant checkedInAt;

    @Column(name = "checked_out_at")
    private Instant checkedOutAt;

    @PrePersist
    protected void onCreate() {
        if (assignedAt == null) {
            assignedAt = Instant.now();
        }
        if (checkedInAt == null) {
            checkedInAt = Instant.now();
        }
    }
}
