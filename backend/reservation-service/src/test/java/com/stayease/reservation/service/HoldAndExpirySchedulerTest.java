package com.stayease.reservation.service;

import com.stayease.reservation.entity.Reservation;
import com.stayease.reservation.enums.PaymentPolicy;
import com.stayease.reservation.enums.PaymentStatus;
import com.stayease.reservation.enums.ReservationStatus;
import com.stayease.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HoldAndExpirySchedulerTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private InventoryLedgerService inventoryLedgerService;

    @Mock
    private ReservationAuditService auditService;

    @InjectMocks
    private HoldAndExpiryScheduler scheduler;

    private Reservation expiredHold;
    private Reservation expiredPayLater;

    @BeforeEach
    void setUp() {
        expiredHold = Reservation.builder()
                .id(1001L)
                .roomTypeId(10L)
                .checkInDate(LocalDate.now().plusDays(3))
                .checkOutDate(LocalDate.now().plusDays(5))
                .reservationStatus(ReservationStatus.PENDING_PAYMENT)
                .paymentPolicy(PaymentPolicy.PREPAID)
                .paymentStatus(PaymentStatus.PENDING)
                .holdExpiresAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .build();

        expiredPayLater = Reservation.builder()
                .id(1002L)
                .roomTypeId(10L)
                .checkInDate(LocalDate.now().plusDays(3))
                .checkOutDate(LocalDate.now().plusDays(5))
                .reservationStatus(ReservationStatus.CONFIRMED)
                .paymentPolicy(PaymentPolicy.PAY_LATER)
                .paymentStatus(PaymentStatus.PENDING)
                .paymentDueAt(Instant.now().minus(10, ChronoUnit.MINUTES))
                .build();
    }

    @Test
    @DisplayName("Hold expiry marks PENDING_PAYMENT hold as PAYMENT_FAILED and releases hold inventory")
    void testExpirePrepaidHolds() {
        when(reservationRepository.findExpiredHolds(eq(ReservationStatus.PENDING_PAYMENT), eq(PaymentPolicy.PREPAID), any()))
                .thenReturn(List.of(expiredHold));

        scheduler.expirePrepaidHolds();

        assertEquals(ReservationStatus.PAYMENT_FAILED, expiredHold.getReservationStatus());
        assertEquals(PaymentStatus.FAILED, expiredHold.getPaymentStatus());
        assertEquals("HOLD_EXPIRED", expiredHold.getCancellationReason());

        verify(inventoryLedgerService).releaseHold(
                eq(10L), eq(expiredHold.getCheckInDate()), eq(expiredHold.getCheckOutDate()), eq(1));
        verify(reservationRepository).save(expiredHold);
    }

    @Test
    @DisplayName("Pay-later timeout marks CONFIRMED reservation as CANCELLED and releases reservation inventory")
    void testExpirePayLaterReservations() {
        when(reservationRepository.findExpiredPayLaterReservations(
                eq(ReservationStatus.CONFIRMED), eq(PaymentPolicy.PAY_LATER), eq(PaymentStatus.PENDING), any()))
                .thenReturn(List.of(expiredPayLater));

        scheduler.expirePayLaterReservations();

        assertEquals(ReservationStatus.CANCELLED, expiredPayLater.getReservationStatus());
        assertEquals("PAYMENT_TIMEOUT", expiredPayLater.getCancellationReason());

        verify(inventoryLedgerService).releaseReservation(
                eq(10L), eq(expiredPayLater.getCheckInDate()), eq(expiredPayLater.getCheckOutDate()), eq(1));
        verify(reservationRepository).save(expiredPayLater);
    }
}
