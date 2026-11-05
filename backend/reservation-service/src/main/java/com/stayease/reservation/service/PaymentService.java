package com.stayease.reservation.service;

import com.stayease.reservation.dto.PaymentResponseDto;
import com.stayease.reservation.dto.PaymentSimulationRequest;
import com.stayease.reservation.dto.SimulationOutcome;
import com.stayease.reservation.entity.PaymentTransaction;
import com.stayease.reservation.entity.Reservation;
import com.stayease.reservation.enums.PaymentPolicy;
import com.stayease.reservation.enums.PaymentStatus;
import com.stayease.reservation.enums.ReservationStatus;
import com.stayease.reservation.exception.InvalidStateTransitionException;
import com.stayease.reservation.exception.PaymentSimulationException;
import com.stayease.reservation.exception.ReservationConflictException;
import com.stayease.reservation.exception.ResourceNotFoundException;
import com.stayease.reservation.repository.PaymentTransactionRepository;
import com.stayease.reservation.repository.ReservationRepository;
import com.stayease.reservation.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final ReservationRepository reservationRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final InventoryLedgerService inventoryLedgerService;
    private final ReservationAuditService auditService;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentResponseDto processPayment(
            Long reservationId,
            PaymentSimulationRequest request,
            String idempotencyKey,
            UserPrincipal currentUser) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new PaymentSimulationException("Idempotency-Key header is required for payment processing");
        }

        // Idempotency check
        Optional<PaymentTransaction> existingTxOpt = paymentTransactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existingTxOpt.isPresent()) {
            PaymentTransaction existingTx = existingTxOpt.get();
            if (existingTx.getReservationId() != null && !existingTx.getReservationId().equals(reservationId)) {
                throw new ReservationConflictException("Idempotency key was previously used with a different request payload");
            }
            if (existingTx.getPaymentMethod() != null && request.getPaymentMethod() != null && existingTx.getPaymentMethod() != request.getPaymentMethod()) {
                throw new ReservationConflictException("Idempotency key was previously used with a different request payload");
            }
            if (existingTx.getSimulatedOutcome() != null && request.getSimulationOutcome() != null && !existingTx.getSimulatedOutcome().equals(request.getSimulationOutcome().name())) {
                throw new ReservationConflictException("Idempotency key was previously used with a different request payload");
            }
            if (existingTx.getPaymentStatus() == PaymentStatus.PROCESSING) {
                log.warn("Duplicate payment request in progress for idempotencyKey: {}", idempotencyKey);
                throw new ReservationConflictException("Payment transaction is currently in progress");
            }
            log.info("Returning existing payment transaction for idempotencyKey: {}", idempotencyKey);
            Reservation res = reservationRepository.findById(existingTx.getReservationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Reservation not found"));
            return mapToResponseDto(existingTx, res.getReservationStatus());
        }

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation with ID " + reservationId + " not found"));

        // User authorization check
        if (!currentUser.getRole().contains("ADMIN") && !reservation.getUserId().equals(currentUser.getId())) {
            throw new ReservationConflictException("You are not authorized to process payment for this reservation");
        }

        // Check current reservation status eligibility
        if (reservation.getReservationStatus() == ReservationStatus.CONFIRMED && reservation.getPaymentStatus() == PaymentStatus.PAID) {
            throw new InvalidStateTransitionException("Reservation is already fully paid");
        }

        Instant now = Instant.now();

        // Late payment check: if hold expired, payment deadline exceeded, or reservation is in terminal/failed state
        boolean isExpired = (reservation.getHoldExpiresAt() != null && reservation.getHoldExpiresAt().isBefore(now))
                || (reservation.getPaymentDueAt() != null && reservation.getPaymentDueAt().isBefore(now))
                || reservation.getReservationStatus() == ReservationStatus.PAYMENT_FAILED
                || reservation.getReservationStatus() == ReservationStatus.CANCELLED
                || reservation.getReservationStatus() == ReservationStatus.NO_SHOW;

        String txnRef = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        SimulationOutcome outcome = request.getSimulationOutcome();

        PaymentTransaction transaction = PaymentTransaction.builder()
                .reservationId(reservation.getId())
                .idempotencyKey(idempotencyKey)
                .transactionReference(txnRef)
                .paymentMethod(request.getPaymentMethod())
                .amount(reservation.getTotalAmount())
                .simulatedOutcome(outcome.name())
                .createdAt(now)
                .build();

        if (isExpired) {
            log.warn("Late payment received for expired/cancelled reservation {}. Rejecting confirmation.", reservationId);
            transaction.setPaymentStatus(PaymentStatus.FAILED);
            transaction.setCompletedAt(now);
            paymentTransactionRepository.save(transaction);

            auditService.recordEvent(
                    currentUser.getId(),
                    currentUser.getRole(),
                    "PAYMENT",
                    "PAYMENT_REJECTED_LATE",
                    String.valueOf(reservationId),
                    "Late payment success rejected because hold or reservation had already expired",
                    null,
                    "{\"transactionStatus\":\"FAILED\",\"reason\":\"LATE_PAYMENT\"}"
            );

            return mapToResponseDto(transaction, reservation.getReservationStatus());
        }

        switch (outcome) {
            case SUCCESS -> {
                transaction.setPaymentStatus(PaymentStatus.PAID);
                transaction.setCompletedAt(now);
                reservation.setPaymentStatus(PaymentStatus.PAID);

                if (reservation.getPaymentPolicy() == PaymentPolicy.PREPAID) {
                    inventoryLedgerService.confirmHold(
                            reservation.getRoomTypeId(),
                            reservation.getCheckInDate(),
                            reservation.getCheckOutDate(),
                            1
                    );
                    reservation.setReservationStatus(ReservationStatus.CONFIRMED);
                    reservation.setConfirmedAt(now);
                } else {
                    // PAY_LATER is already CONFIRMED
                    reservation.setReservationStatus(ReservationStatus.CONFIRMED);
                }

                reservationRepository.save(reservation);
                paymentTransactionRepository.save(transaction);

                auditService.recordEvent(
                        currentUser.getId(),
                        currentUser.getRole(),
                        "PAYMENT",
                        "PAYMENT_SUCCESS",
                        String.valueOf(reservationId),
                        "Payment of " + transaction.getAmount() + " processed successfully via " + transaction.getPaymentMethod(),
                        "{\"status\":\"" + reservation.getReservationStatus() + "\"}",
                        "{\"status\":\"CONFIRMED\",\"paymentStatus\":\"PAID\"}"
                );
            }
            case FAILURE -> {
                transaction.setPaymentStatus(PaymentStatus.FAILED);
                transaction.setCompletedAt(now);
                reservation.setPaymentStatus(PaymentStatus.FAILED);

                if (reservation.getPaymentPolicy() == PaymentPolicy.PREPAID) {
                    inventoryLedgerService.releaseHold(
                            reservation.getRoomTypeId(),
                            reservation.getCheckInDate(),
                            reservation.getCheckOutDate(),
                            1
                    );
                    reservation.setReservationStatus(ReservationStatus.PAYMENT_FAILED);
                }

                reservationRepository.save(reservation);
                paymentTransactionRepository.save(transaction);

                auditService.recordEvent(
                        currentUser.getId(),
                        currentUser.getRole(),
                        "PAYMENT",
                        "PAYMENT_FAILED",
                        String.valueOf(reservationId),
                        "Payment attempt failed for reservation " + reservationId,
                        null,
                        "{\"paymentStatus\":\"FAILED\"}"
                );
            }
            case TIMEOUT -> {
                // Uncertain outcome: transition to RECONCILIATION_REQUIRED, leave hold protected
                transaction.setPaymentStatus(PaymentStatus.PROCESSING);
                reservation.setPaymentStatus(PaymentStatus.PROCESSING);
                reservation.setReservationStatus(ReservationStatus.RECONCILIATION_REQUIRED);

                reservationRepository.save(reservation);
                paymentTransactionRepository.save(transaction);

                auditService.recordEvent(
                        currentUser.getId(),
                        currentUser.getRole(),
                        "PAYMENT",
                        "PAYMENT_TIMEOUT_RECONCILIATION",
                        String.valueOf(reservationId),
                        "Simulated payment timed out; reservation frozen in RECONCILIATION_REQUIRED",
                        null,
                        "{\"status\":\"RECONCILIATION_REQUIRED\",\"paymentStatus\":\"PROCESSING\"}"
                );
            }
        }

        return mapToResponseDto(transaction, reservation.getReservationStatus());
    }

    @Transactional
    public PaymentResponseDto reconcilePayment(Long reservationId, SimulationOutcome outcome, UserPrincipal currentUser) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation not found: " + reservationId));

        if (reservation.getReservationStatus() != ReservationStatus.RECONCILIATION_REQUIRED) {
            throw new InvalidStateTransitionException("Reservation is not awaiting reconciliation");
        }

        Instant now = Instant.now();
        if (outcome == SimulationOutcome.SUCCESS) {
            if (reservation.getPaymentPolicy() == PaymentPolicy.PREPAID) {
                inventoryLedgerService.confirmHold(
                        reservation.getRoomTypeId(),
                        reservation.getCheckInDate(),
                        reservation.getCheckOutDate(),
                        1
                );
            }
            reservation.setReservationStatus(ReservationStatus.CONFIRMED);
            reservation.setPaymentStatus(PaymentStatus.PAID);
            reservation.setConfirmedAt(now);
        } else {
            if (reservation.getPaymentPolicy() == PaymentPolicy.PREPAID) {
                inventoryLedgerService.releaseHold(
                        reservation.getRoomTypeId(),
                        reservation.getCheckInDate(),
                        reservation.getCheckOutDate(),
                        1
                );
            }
            reservation.setReservationStatus(ReservationStatus.PAYMENT_FAILED);
            reservation.setPaymentStatus(PaymentStatus.FAILED);
        }

        reservationRepository.save(reservation);

        List<PaymentTransaction> txs = paymentTransactionRepository.findByReservationId(reservationId);
        PaymentTransaction reconciledTx = null;
        for (PaymentTransaction tx : txs) {
            if (tx.getPaymentStatus() == PaymentStatus.PROCESSING) {
                tx.setPaymentStatus(outcome == SimulationOutcome.SUCCESS ? PaymentStatus.PAID : PaymentStatus.FAILED);
                tx.setCompletedAt(now);
                paymentTransactionRepository.save(tx);
                reconciledTx = tx;
            }
        }

        auditService.recordEvent(
                currentUser.getId(),
                currentUser.getRole(),
                "PAYMENT",
                "PAYMENT_RECONCILED",
                String.valueOf(reservationId),
                "Reservation reconciled with outcome " + outcome,
                "{\"status\":\"RECONCILIATION_REQUIRED\"}",
                "{\"status\":\"" + reservation.getReservationStatus() + "\"}"
        );

        return PaymentResponseDto.builder()
                .transactionId(reconciledTx != null ? reconciledTx.getTransactionReference() : null)
                .reservationId(reservation.getId())
                .idempotencyKey(reconciledTx != null ? reconciledTx.getIdempotencyKey() : null)
                .amount(reservation.getTotalAmount())
                .paymentMethod(reconciledTx != null ? reconciledTx.getPaymentMethod() : null)
                .paymentStatus(reservation.getPaymentStatus())
                .reservationStatus(reservation.getReservationStatus())
                .completedAt(now)
                .build();
    }

    private PaymentResponseDto mapToResponseDto(PaymentTransaction tx, ReservationStatus reservationStatus) {
        return PaymentResponseDto.builder()
                .transactionId(tx.getTransactionReference())
                .reservationId(tx.getReservationId())
                .idempotencyKey(tx.getIdempotencyKey())
                .amount(tx.getAmount())
                .paymentMethod(tx.getPaymentMethod())
                .paymentStatus(tx.getPaymentStatus())
                .reservationStatus(reservationStatus)
                .completedAt(tx.getCompletedAt())
                .build();
    }
}
