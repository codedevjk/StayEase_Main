package com.stayease.reservation.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(
        name = "room_type_inventory",
        indexes = {
                @Index(name = "idx_inv_date", columnList = "stay_date")
        }
)
@IdClass(RoomTypeInventoryId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomTypeInventory {

    @Id
    @Column(name = "room_type_id", nullable = false)
    private Long roomTypeId;

    @Id
    @Column(name = "stay_date", nullable = false)
    private LocalDate stayDate;

    @Column(name = "total_capacity", nullable = false)
    private Integer totalCapacity;

    @Column(name = "reserved_count", nullable = false)
    @Builder.Default
    private Integer reservedCount = 0;

    @Column(name = "hold_count", nullable = false)
    @Builder.Default
    private Integer holdCount = 0;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;
}
