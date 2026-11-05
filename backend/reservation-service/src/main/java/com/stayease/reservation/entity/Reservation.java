package com.stayease.reservation.entity;

import com.stayease.reservation.enums.PaymentPolicy;
import com.stayease.reservation.enums.PaymentStatus;
import com.stayease.reservation.enums.ReservationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(
        name = "reservations",
        indexes = {
                @Index(name = "idx_res_user", columnList = "user_id"),
                @Index(name = "idx_res_hotel", columnList = "hotel_id"),
                @Index(name = "idx_res_status", columnList = "reservation_status"),
                @Index(name = "idx_res_dates", columnList = "check_in_date, check_out_date"),
                @Index(name = "idx_res_hold_expiry", columnList = "hold_expires_at, reservation_status"),
                @Index(name = "idx_res_pay_due", columnList = "payment_due_at, reservation_status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_reference", nullable = false, unique = true, length = 30)
    private String publicReference;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "hotel_id", nullable = false)
    private Long hotelId;

    @Column(name = "room_type_id", nullable = false)
    private Long roomTypeId;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    @Column(name = "nights", nullable = false)
    private Integer nights;

    @Column(name = "guest_count", nullable = false)
    private Integer guestCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "reservation_status", nullable = false, length = 30)
    private ReservationStatus reservationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_policy", nullable = false, length = 20)
    private PaymentPolicy paymentPolicy;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Column(name = "payment_due_at")
    private Instant paymentDueAt;

    @Column(name = "lodging_subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal lodgingSubtotal;

    @Column(name = "tax_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxPercentage;

    @Column(name = "tax_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxAmount;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "price_snapshot_json", nullable = false, columnDefinition = "json")
    private String priceSnapshotJson;

    @Column(name = "penalty_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal penaltyAmount = BigDecimal.ZERO;

    @Column(name = "refund_amount", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal refundAmount = BigDecimal.ZERO;

    @Column(name = "cancellation_reason", length = 50)
    private String cancellationReason;

    @Column(name = "cancellation_notes", length = 255)
    private String cancellationNotes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "checked_out_at")
    private Instant checkedOutAt;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (penaltyAmount == null) {
            penaltyAmount = BigDecimal.ZERO;
        }
        if (refundAmount == null) {
            refundAmount = BigDecimal.ZERO;
        }
    }
}
