package com.stayease.reservation.controller;

import com.stayease.reservation.dto.*;
import com.stayease.reservation.enums.ReservationStatus;
import com.stayease.reservation.security.UserPrincipal;
import com.stayease.reservation.service.PaymentService;
import com.stayease.reservation.service.ReservationAuditService;
import com.stayease.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminReservationController {

    private final ReservationService reservationService;
    private final PaymentService paymentService;
    private final ReservationAuditService auditService;

    /**
     * Lists all reservations across the system for administrator operations.
     */
    @GetMapping("/reservations")
    public ResponseEntity<PageResponse<ReservationSummaryDto>> getAllReservations(
            @RequestParam(required = false) ReservationStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        PageResponse<ReservationSummaryDto> response = reservationService.getAllReservationsForAdmin(status, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Admin marks an unfulfilled reservation as NO_SHOW after scheduled check-in date.
     */
    @PostMapping("/reservations/{reservationId}/no-show")
    public ResponseEntity<ReservationDetailDto> markNoShow(
            @PathVariable Long reservationId,
            @RequestBody(required = false) NoShowRequest request) {

        ReservationDetailDto response = reservationService.noShow(reservationId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Operational administrative metrics.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardDto> getDashboard() {
        AdminDashboardDto response = reservationService.getAdminDashboard();
        return ResponseEntity.ok(response);
    }

    /**
     * Queries reservation service audit events.
     */
    @GetMapping("/audit")
    public ResponseEntity<PageResponse<AuditEventDto>> getAuditTrail(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String entityId,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {

        PageResponse<AuditEventDto> response = auditService.queryAuditTrail(from, to, category, actorId, entityId, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Manually or administratively resolves an ambiguous payment state.
     */
    @PostMapping("/reservations/{reservationId}/reconcile")
    public ResponseEntity<PaymentResponseDto> reconcilePayment(
            @PathVariable Long reservationId,
            @RequestParam SimulationOutcome outcome,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        PaymentResponseDto response = paymentService.reconcilePayment(reservationId, outcome, currentUser);
        return ResponseEntity.ok(response);
    }
}
