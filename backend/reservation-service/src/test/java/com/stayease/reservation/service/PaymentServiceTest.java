package com.stayease.reservation.service;

import com.stayease.reservation.dto.PaymentResponseDto;
import com.stayease.reservation.dto.PaymentSimulationRequest;
import com.stayease.reservation.dto.SimulationOutcome;
import com.stayease.reservation.entity.PaymentTransaction;
import com.stayease.reservation.entity.Reservation;
import com.stayease.reservation.enums.PaymentMethod;
import com.stayease.reservation.enums.PaymentPolicy;
import com.stayease.reservation.enums.PaymentStatus;
import com.stayease.reservation.enums.ReservationStatus;
import com.stayease.reservation.exception.ReservationConflictException;
import com.stayease.reservation.repository.PaymentTransactionRepository;
import com.stayease.reservation.repository.ReservationRepository;
import com.stayease.reservation.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private InventoryLedgerService inventoryLedgerService;

    @Mock
    private ReservationAuditService auditService;

    @InjectMocks
    private PaymentService paymentService;

    private Reservation prepaidReservation;
    private UserPrincipal userPrincipal;

    @BeforeEach
    void setUp() {
        userPrincipal = UserPrincipal.create(101L, "jane@example.com", "ROLE_USER", 1L);

        prepaidReservation = Reservation.builder()
                .id(5001L)
                .publicReference("SE-2026-5001")
                .userId(101L)
                .hotelId(1L)
                .roomTypeId(10L)
                .checkInDate(LocalDate.now().plusDays(5))
                .checkOutDate(LocalDate.now().plusDays(7))
                .nights(2)
                .guestCount(2)
                .reservationStatus(ReservationStatus.PENDING_PAYMENT)
                .paymentPolicy(PaymentPolicy.PREPAID)
                .paymentStatus(PaymentStatus.PENDING)
                .totalAmount(new BigDecimal("10864.00"))
                .holdExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build();
    }

    @Test
    @DisplayName("Prepaid payment SUCCESS confirms hold and transitions to CONFIRMED / PAID")
    void testProcessPaymentSuccess() {
        PaymentSimulationRequest req = PaymentSimulationRequest.builder()
                .paymentMethod(PaymentMethod.UPI)
                .simulationOutcome(SimulationOutcome.SUCCESS)
                .build();

        when(paymentTransactionRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(reservationRepository.findById(5001L)).thenReturn(Optional.of(prepaidReservation));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        PaymentResponseDto response = paymentService.processPayment(5001L, req, "key-1", userPrincipal);

        assertNotNull(response);
        assertEquals(PaymentStatus.PAID, response.getPaymentStatus());
        assertEquals(ReservationStatus.CONFIRMED, response.getReservationStatus());
        assertEquals(ReservationStatus.CONFIRMED, prepaidReservation.getReservationStatus());
        assertEquals(PaymentStatus.PAID, prepaidReservation.getPaymentStatus());

        verify(inventoryLedgerService).confirmHold(eq(10L), any(), any(), eq(1));
    }

    @Test
    @DisplayName("Prepaid payment FAILURE releases hold and transitions to PAYMENT_FAILED / FAILED")
    void testProcessPaymentFailure() {
        PaymentSimulationRequest req = PaymentSimulationRequest.builder()
                .paymentMethod(PaymentMethod.CARD)
                .simulationOutcome(SimulationOutcome.FAILURE)
                .build();

        when(paymentTransactionRepository.findByIdempotencyKey("key-2")).thenReturn(Optional.empty());
        when(reservationRepository.findById(5001L)).thenReturn(Optional.of(prepaidReservation));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        PaymentResponseDto response = paymentService.processPayment(5001L, req, "key-2", userPrincipal);

        assertNotNull(response);
        assertEquals(PaymentStatus.FAILED, response.getPaymentStatus());
        assertEquals(ReservationStatus.PAYMENT_FAILED, response.getReservationStatus());

        verify(inventoryLedgerService).releaseHold(eq(10L), any(), any(), eq(1));
    }

    @Test
    @DisplayName("Payment TIMEOUT transitions reservation to RECONCILIATION_REQUIRED and keeps hold protected")
    void testProcessPaymentTimeout() {
        PaymentSimulationRequest req = PaymentSimulationRequest.builder()
                .paymentMethod(PaymentMethod.NETBANKING)
                .simulationOutcome(SimulationOutcome.TIMEOUT)
                .build();

        when(paymentTransactionRepository.findByIdempotencyKey("key-3")).thenReturn(Optional.empty());
        when(reservationRepository.findById(5001L)).thenReturn(Optional.of(prepaidReservation));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));

        PaymentResponseDto response = paymentService.processPayment(5001L, req, "key-3", userPrincipal);

        assertNotNull(response);
        assertEquals(PaymentStatus.PROCESSING, response.getPaymentStatus());
        assertEquals(ReservationStatus.RECONCILIATION_REQUIRED, response.getReservationStatus());

        // Hold must NOT be released!
        verify(inventoryLedgerService, never()).releaseHold(any(), any(), any(), anyInt());
        verify(inventoryLedgerService, never()).confirmHold(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("Duplicate request with already PAID idempotencyKey returns cached transaction")
    void testDuplicateIdempotencyKeyPaid() {
        PaymentTransaction existing = PaymentTransaction.builder()
                .id(1L)
                .reservationId(5001L)
                .idempotencyKey("key-dup")
                .transactionReference("TXN-12345678")
                .paymentMethod(PaymentMethod.UPI)
                .amount(new BigDecimal("10864.00"))
                .paymentStatus(PaymentStatus.PAID)
                .completedAt(Instant.now())
                .build();

        prepaidReservation.setReservationStatus(ReservationStatus.CONFIRMED);
        when(paymentTransactionRepository.findByIdempotencyKey("key-dup")).thenReturn(Optional.of(existing));
        when(reservationRepository.findById(5001L)).thenReturn(Optional.of(prepaidReservation));

        PaymentSimulationRequest req = PaymentSimulationRequest.builder()
                .paymentMethod(PaymentMethod.UPI)
                .simulationOutcome(SimulationOutcome.SUCCESS)
                .build();

        PaymentResponseDto response = paymentService.processPayment(5001L, req, "key-dup", userPrincipal);

        assertNotNull(response);
        assertEquals("TXN-12345678", response.getTransactionId());
        assertEquals(PaymentStatus.PAID, response.getPaymentStatus());
        verify(paymentTransactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Duplicate request with PROCESSING idempotencyKey throws ReservationConflictException")
    void testDuplicateIdempotencyKeyProcessing() {
        PaymentTransaction existing = PaymentTransaction.builder()
                .id(1L)
                .reservationId(5001L)
                .idempotencyKey("key-proc")
                .paymentStatus(PaymentStatus.PROCESSING)
                .build();

        when(paymentTransactionRepository.findByIdempotencyKey("key-proc")).thenReturn(Optional.of(existing));

        PaymentSimulationRequest req = PaymentSimulationRequest.builder()
                .paymentMethod(PaymentMethod.UPI)
                .simulationOutcome(SimulationOutcome.SUCCESS)
                .build();

        assertThrows(ReservationConflictException.class, () ->
                paymentService.processPayment(5001L, req, "key-proc", userPrincipal));
    }

    @Test
    @DisplayName("Late payment attempt after hold expiration is rejected and does not confirm reservation")
    void testLatePaymentAfterHoldExpiration() {
        prepaidReservation.setHoldExpiresAt(Instant.now().minus(5, ChronoUnit.MINUTES));

        when(paymentTransactionRepository.findByIdempotencyKey("late-key")).thenReturn(Optional.empty());
        when(reservationRepository.findById(5001L)).thenReturn(Optional.of(prepaidReservation));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        PaymentSimulationRequest req = PaymentSimulationRequest.builder()
                .paymentMethod(PaymentMethod.CARD)
                .simulationOutcome(SimulationOutcome.SUCCESS)
                .build();

        PaymentResponseDto response = paymentService.processPayment(5001L, req, "late-key", userPrincipal);

        assertNotNull(response);
        assertEquals(PaymentStatus.FAILED, response.getPaymentStatus());
        assertNotEquals(ReservationStatus.CONFIRMED, prepaidReservation.getReservationStatus());
        verify(inventoryLedgerService, never()).confirmHold(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("Admin reconciliation resolves RECONCILIATION_REQUIRED to CONFIRMED / PAID")
    void testReconcilePaymentSuccess() {
        prepaidReservation.setReservationStatus(ReservationStatus.RECONCILIATION_REQUIRED);
        prepaidReservation.setPaymentStatus(PaymentStatus.PROCESSING);

        when(reservationRepository.findById(5001L)).thenReturn(Optional.of(prepaidReservation));
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UserPrincipal adminPrincipal = UserPrincipal.create(1L, "admin@example.com", "ROLE_ADMIN", 1L);
        PaymentResponseDto response = paymentService.reconcilePayment(5001L, SimulationOutcome.SUCCESS, adminPrincipal);

        assertNotNull(response);
        assertEquals(ReservationStatus.CONFIRMED, response.getReservationStatus());
        assertEquals(PaymentStatus.PAID, response.getPaymentStatus());
        verify(inventoryLedgerService).confirmHold(eq(10L), any(), any(), eq(1));
    }

    @Test
    @DisplayName("Duplicate request with same idempotencyKey but different payload throws ReservationConflictException")
    void testDuplicateIdempotencyKeyDifferentPayloadThrowsConflict() {
        PaymentTransaction existing = PaymentTransaction.builder()
                .id(1L)
                .reservationId(5001L)
                .idempotencyKey("key-diff")
                .paymentMethod(PaymentMethod.UPI)
                .simulatedOutcome("SUCCESS")
                .paymentStatus(PaymentStatus.PAID)
                .build();

        when(paymentTransactionRepository.findByIdempotencyKey("key-diff")).thenReturn(Optional.of(existing));

        // Attempt reuse with different payment method
        PaymentSimulationRequest reqDiffMethod = PaymentSimulationRequest.builder()
                .paymentMethod(PaymentMethod.CARD)
                .simulationOutcome(SimulationOutcome.SUCCESS)
                .build();

        assertThrows(ReservationConflictException.class, () ->
                paymentService.processPayment(5001L, reqDiffMethod, "key-diff", userPrincipal));

        // Attempt reuse with different reservationId
        PaymentSimulationRequest reqSameMethod = PaymentSimulationRequest.builder()
                .paymentMethod(PaymentMethod.UPI)
                .simulationOutcome(SimulationOutcome.SUCCESS)
                .build();

        assertThrows(ReservationConflictException.class, () ->
                paymentService.processPayment(9999L, reqSameMethod, "key-diff", userPrincipal));
    }

    @Test
    @DisplayName("Pay-later payment attempt after paymentDueAt is rejected as late payment")
    void testLatePaymentPayLaterExceededPaymentDueAt() {
        Reservation payLaterRes = Reservation.builder()
                .id(5002L)
                .userId(101L)
                .reservationStatus(ReservationStatus.CONFIRMED)
                .paymentPolicy(PaymentPolicy.PAY_LATER)
                .paymentStatus(PaymentStatus.PENDING)
                .totalAmount(new BigDecimal("10864.00"))
                .paymentDueAt(Instant.now().minus(10, ChronoUnit.MINUTES))
                .build();

        when(paymentTransactionRepository.findByIdempotencyKey("late-paylater-key")).thenReturn(Optional.empty());
        when(reservationRepository.findById(5002L)).thenReturn(Optional.of(payLaterRes));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(i -> i.getArgument(0));

        PaymentSimulationRequest req = PaymentSimulationRequest.builder()
                .paymentMethod(PaymentMethod.UPI)
                .simulationOutcome(SimulationOutcome.SUCCESS)
                .build();

        PaymentResponseDto response = paymentService.processPayment(5002L, req, "late-paylater-key", userPrincipal);

        assertNotNull(response);
        assertEquals(PaymentStatus.FAILED, response.getPaymentStatus());
        assertNotEquals(PaymentStatus.PAID, payLaterRes.getPaymentStatus());
    }

    @Test
    @DisplayName("Reconciliation updates PaymentTransaction records from PROCESSING to terminal outcome")
    void testReconcilePaymentUpdatesPaymentTransactionStatus() {
        prepaidReservation.setReservationStatus(ReservationStatus.RECONCILIATION_REQUIRED);
        prepaidReservation.setPaymentStatus(PaymentStatus.PROCESSING);

        PaymentTransaction pendingTx = PaymentTransaction.builder()
                .id(99L)
                .reservationId(5001L)
                .idempotencyKey("key-reconcile-tx")
                .transactionReference("TXN-RECON-1")
                .paymentMethod(PaymentMethod.UPI)
                .paymentStatus(PaymentStatus.PROCESSING)
                .build();

        when(reservationRepository.findById(5001L)).thenReturn(Optional.of(prepaidReservation));
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(paymentTransactionRepository.findByReservationId(5001L)).thenReturn(List.of(pendingTx));
        when(paymentTransactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        UserPrincipal adminPrincipal = UserPrincipal.create(1L, "admin@example.com", "ROLE_ADMIN", 1L);
        PaymentResponseDto response = paymentService.reconcilePayment(5001L, SimulationOutcome.SUCCESS, adminPrincipal);

        assertNotNull(response);
        assertEquals("TXN-RECON-1", response.getTransactionId());
        assertEquals(PaymentStatus.PAID, pendingTx.getPaymentStatus());
        assertNotNull(pendingTx.getCompletedAt());
        verify(paymentTransactionRepository).save(pendingTx);
    }
}
