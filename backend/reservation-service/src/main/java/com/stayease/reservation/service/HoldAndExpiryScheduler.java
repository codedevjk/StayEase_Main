package com.stayease.reservation.service;

import com.stayease.reservation.entity.Reservation;
import com.stayease.reservation.enums.CancellationReason;
import com.stayease.reservation.enums.PaymentPolicy;
import com.stayease.reservation.enums.PaymentStatus;
import com.stayease.reservation.enums.ReservationStatus;
import com.stayease.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class HoldAndExpiryScheduler {

    private final ReservationRepository reservationRepository;
    private final InventoryLedgerService inventoryLedgerService;
    private final ReservationAuditService auditService;

    /**
     * Executes every 60 seconds to release expired prepaid holds (10m duration)
     * and auto-cancel unpaid pay-later reservations exceeding paymentDueAt.
     */
    @Scheduled(fixedRateString = "${stayease.inventory.expiry-check-interval-seconds:60}000")
    @Transactional
    public void processExpirations() {
        expirePrepaidHolds();
        expirePayLaterReservations();
    }

    public void expirePrepaidHolds() {
        Instant now = Instant.now();
        List<Reservation> expiredHolds = reservationRepository.findExpiredHolds(
                ReservationStatus.PENDING_PAYMENT, PaymentPolicy.PREPAID, now);

        if (!expiredHolds.isEmpty()) {
            log.info("Found {} expired prepaid holds to release", expiredHolds.size());
            for (Reservation r : expiredHolds) {
                try {
                    inventoryLedgerService.releaseHold(
                            r.getRoomTypeId(), r.getCheckInDate(), r.getCheckOutDate(), 1);

                    r.setReservationStatus(ReservationStatus.PAYMENT_FAILED);
                    r.setPaymentStatus(PaymentStatus.FAILED);
                    r.setCancellationReason("HOLD_EXPIRED");
                    reservationRepository.save(r);

                    auditService.recordEvent(
                            0L,
                            "SYSTEM",
                            "HOLD_EXPIRY",
                            "HOLD_EXPIRED",
                            String.valueOf(r.getId()),
                            "Prepaid hold expired after 10 minutes without payment",
                            "{\"status\":\"PENDING_PAYMENT\"}",
                            "{\"status\":\"PAYMENT_FAILED\"}"
                    );
                    log.debug("Expired hold for reservation ID {}", r.getId());
                } catch (Exception ex) {
                    log.error("Failed to expire hold for reservation {}: {}", r.getId(), ex.getMessage());
                }
            }
        }
    }

    public void expirePayLaterReservations() {
        Instant now = Instant.now();
        List<Reservation> expiredPayLater = reservationRepository.findExpiredPayLaterReservations(
                ReservationStatus.CONFIRMED, PaymentPolicy.PAY_LATER, PaymentStatus.PENDING, now);

        if (!expiredPayLater.isEmpty()) {
            log.info("Found {} unpaid pay-later reservations exceeding payment deadline", expiredPayLater.size());
            for (Reservation r : expiredPayLater) {
                try {
                    inventoryLedgerService.releaseReservation(
                            r.getRoomTypeId(), r.getCheckInDate(), r.getCheckOutDate(), 1);

                    r.setReservationStatus(ReservationStatus.CANCELLED);
                    r.setCancelledAt(now);
                    r.setCancellationReason(CancellationReason.PAYMENT_TIMEOUT.name());
                    reservationRepository.save(r);

                    auditService.recordEvent(
                            0L,
                            "SYSTEM",
                            "PAYMENT_EXPIRY",
                            "PAY_LATER_TIMEOUT",
                            String.valueOf(r.getId()),
                            "Pay-later reservation cancelled due to payment timeout",
                            "{\"status\":\"CONFIRMED\"}",
                            "{\"status\":\"CANCELLED\",\"reason\":\"PAYMENT_TIMEOUT\"}"
                    );
                    log.debug("Auto-cancelled unpaid pay-later reservation ID {}", r.getId());
                } catch (Exception ex) {
                    log.error("Failed to cancel unpaid pay-later reservation {}: {}", r.getId(), ex.getMessage());
                }
            }
        }
    }
}
