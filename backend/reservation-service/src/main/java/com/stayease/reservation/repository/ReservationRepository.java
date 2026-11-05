package com.stayease.reservation.repository;

import com.stayease.reservation.entity.Reservation;
import com.stayease.reservation.enums.PaymentPolicy;
import com.stayease.reservation.enums.PaymentStatus;
import com.stayease.reservation.enums.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByPublicReference(String publicReference);

    Page<Reservation> findByUserId(Long userId, Pageable pageable);

    Page<Reservation> findByReservationStatus(ReservationStatus status, Pageable pageable);

    Page<Reservation> findByUserIdAndReservationStatus(Long userId, ReservationStatus status, Pageable pageable);

    @Query("SELECT r FROM Reservation r WHERE r.reservationStatus = :status AND r.paymentPolicy = :policy AND r.holdExpiresAt < :now")
    List<Reservation> findExpiredHolds(
            @Param("status") ReservationStatus status,
            @Param("policy") PaymentPolicy policy,
            @Param("now") Instant now);

    @Query("SELECT r FROM Reservation r WHERE r.reservationStatus = :resStatus AND r.paymentPolicy = :policy AND r.paymentStatus = :payStatus AND r.paymentDueAt < :now")
    List<Reservation> findExpiredPayLaterReservations(
            @Param("resStatus") ReservationStatus resStatus,
            @Param("policy") PaymentPolicy policy,
            @Param("payStatus") PaymentStatus payStatus,
            @Param("now") Instant now);

    long countByReservationStatus(ReservationStatus status);

    long countByCheckInDate(LocalDate checkInDate);

    long countByCheckOutDate(LocalDate checkOutDate);

    long countByPaymentStatus(PaymentStatus paymentStatus);

    @Query("SELECT COALESCE(SUM(r.totalAmount), 0) FROM Reservation r WHERE r.paymentStatus = :paymentStatus")
    BigDecimal sumTotalAmountByPaymentStatus(@Param("paymentStatus") PaymentStatus paymentStatus);
}
