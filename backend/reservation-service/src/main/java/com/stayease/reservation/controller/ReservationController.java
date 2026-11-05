package com.stayease.reservation.controller;

import com.stayease.reservation.dto.*;
import com.stayease.reservation.enums.ReservationStatus;
import com.stayease.reservation.security.UserPrincipal;
import com.stayease.reservation.service.PaymentService;
import com.stayease.reservation.service.ReservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final PaymentService paymentService;

    /**
     * Initiates a reservation (creates a 10-minute hold for PREPAID, or reserves inventory for PAY_LATER).
     */
    @PostMapping
    public ResponseEntity<ReservationDetailDto> createReservation(
            @Valid @RequestBody CreateReservationRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        ReservationDetailDto response = reservationService.createReservation(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Simulates a payment attempt for a pending reservation.
     */
    @PostMapping("/{reservationId}/payment")
    public ResponseEntity<PaymentResponseDto> processPayment(
            @PathVariable Long reservationId,
            @Valid @RequestBody PaymentSimulationRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        PaymentResponseDto response = paymentService.processPayment(reservationId, request, idempotencyKey, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * Lists reservations belonging to the authenticated guest.
     */
    @GetMapping("/my")
    public ResponseEntity<PageResponse<ReservationSummaryDto>> getMyReservations(
            @RequestParam(required = false) ReservationStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        PageResponse<ReservationSummaryDto> response = reservationService.getMyReservations(currentUser, status, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves complete reservation details, price snapshot, and assignment status.
     */
    @GetMapping("/{reservationId}")
    public ResponseEntity<ReservationDetailDto> getReservationById(
            @PathVariable Long reservationId,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        ReservationDetailDto response = reservationService.getReservationById(reservationId, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * Cancels an eligible reservation and calculates policy penalties/refunds.
     */
    @PostMapping("/{reservationId}/cancel")
    public ResponseEntity<CancellationSummaryDto> cancelReservation(
            @PathVariable Long reservationId,
            @RequestBody(required = false) CancelReservationRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        CancellationSummaryDto response = reservationService.cancelReservation(reservationId, request, currentUser, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    /**
     * Modifies reservation dates or room type prior to check-in.
     */
    @PostMapping("/{reservationId}/amend")
    public ResponseEntity<ReservationDetailDto> amendReservation(
            @PathVariable Long reservationId,
            @Valid @RequestBody AmendReservationRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        ReservationDetailDto response = reservationService.amendReservation(reservationId, request, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * Performs guest self-service check-in with physical room assignment.
     */
    @PostMapping("/{reservationId}/check-in")
    public ResponseEntity<CheckInResponseDto> checkIn(
            @PathVariable Long reservationId,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        CheckInResponseDto response = reservationService.checkIn(reservationId, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * Performs guest self-service check-out and marks physical room DIRTY.
     */
    @PostMapping("/{reservationId}/check-out")
    public ResponseEntity<CheckOutResponseDto> checkOut(
            @PathVariable Long reservationId,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        CheckOutResponseDto response = reservationService.checkOut(reservationId, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves printable invoice data backed by immutable booking price snapshot.
     */
    @GetMapping("/{reservationId}/invoice")
    public ResponseEntity<InvoiceDto> getInvoice(
            @PathVariable Long reservationId,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        InvoiceDto response = reservationService.getInvoice(reservationId, currentUser);
        return ResponseEntity.ok(response);
    }
}
