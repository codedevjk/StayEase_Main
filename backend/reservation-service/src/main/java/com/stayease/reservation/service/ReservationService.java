package com.stayease.reservation.service;

import com.stayease.reservation.client.*;
import com.stayease.reservation.dto.*;
import com.stayease.reservation.entity.PaymentTransaction;
import com.stayease.reservation.entity.Reservation;
import com.stayease.reservation.entity.RoomAssignment;
import com.stayease.reservation.enums.CancellationReason;
import com.stayease.reservation.enums.PaymentPolicy;
import com.stayease.reservation.enums.PaymentStatus;
import com.stayease.reservation.enums.ReservationStatus;
import com.stayease.reservation.exception.*;
import com.stayease.reservation.repository.PaymentTransactionRepository;
import com.stayease.reservation.repository.ReservationRepository;
import com.stayease.reservation.repository.RoomAssignmentRepository;
import com.stayease.reservation.security.UserPrincipal;
import com.stayease.reservation.util.PageUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final RoomAssignmentRepository roomAssignmentRepository;
    private final InventoryLedgerService inventoryLedgerService;
    private final PricingService pricingService;
    private final PropertyServiceClient propertyClient;
    private final ReservationAuditService auditService;

    @Value("${stayease.time-zone:Asia/Kolkata}")
    private String timeZone;

    @Transactional
    public ReservationDetailDto createReservation(CreateReservationRequest request, UserPrincipal currentUser) {
        validateDates(request.getCheckInDate(), request.getCheckOutDate());
        validateGuests(request);

        PropertyHotelDto hotel = propertyClient.getHotelDetails(request.getHotelId());
        PropertyRoomTypeDto roomType = hotel.getRoomTypes().stream()
                .filter(rt -> rt.getId().equals(request.getRoomTypeId()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Room type " + request.getRoomTypeId() + " not found in hotel " + request.getHotelId()));

        if (roomType.getMaxGuests() != null && roomType.getMaxGuests() < request.getGuestCount()) {
            throw new InvalidDateRangeException("Room type capacity (" + roomType.getMaxGuests() + ") exceeded by guest count (" + request.getGuestCount() + ")");
        }

        PropertyCapacityDto capacityDto = propertyClient.getSellableCapacity(request.getRoomTypeId());
        int sellableCapacity = capacityDto.getSellableCapacity() != null ? capacityDto.getSellableCapacity() : 0;

        PropertyPricingDto pricingDto = propertyClient.getPricing(
                request.getRoomTypeId(), request.getCheckInDate(), request.getCheckOutDate());
        List<PropertyNightlyRateDto> nightlyRates = pricingDto.getNightlyRates() != null
                ? pricingDto.getNightlyRates()
                : List.of();

        PricingSnapshotDto snapshot = pricingService.calculatePricingSnapshot(nightlyRates);
        String snapshotJson = pricingService.serializeSnapshot(snapshot);

        int nights = (int) ChronoUnit.DAYS.between(request.getCheckInDate(), request.getCheckOutDate());
        Instant now = Instant.now();
        String publicRef = "SE-" + LocalDate.now().getYear() + "-" + (1000 + new Random().nextInt(9000));

        Reservation.ReservationBuilder builder = Reservation.builder()
                .publicReference(publicRef)
                .userId(currentUser.getId())
                .hotelId(request.getHotelId())
                .roomTypeId(request.getRoomTypeId())
                .checkInDate(request.getCheckInDate())
                .checkOutDate(request.getCheckOutDate())
                .nights(nights)
                .guestCount(request.getGuestCount())
                .paymentPolicy(request.getPaymentPolicy())
                .lodgingSubtotal(snapshot.getLodgingSubtotal())
                .taxPercentage(snapshot.getTaxPercentage())
                .taxAmount(snapshot.getTaxAmount())
                .totalAmount(snapshot.getTotalAmount())
                .priceSnapshotJson(snapshotJson)
                .createdAt(now)
                .version(0L);

        if (request.getPaymentPolicy() == PaymentPolicy.PREPAID) {
            inventoryLedgerService.checkAndHoldInventory(
                    request.getRoomTypeId(), request.getCheckInDate(), request.getCheckOutDate(), 1, sellableCapacity);
            builder.reservationStatus(ReservationStatus.PENDING_PAYMENT)
                    .paymentStatus(PaymentStatus.PENDING)
                    .holdExpiresAt(now.plus(Duration.ofMinutes(10)))
                    .paymentDueAt(null);
        } else {
            // PAY_LATER: reserves directly
            inventoryLedgerService.checkAndReserveInventory(
                    request.getRoomTypeId(), request.getCheckInDate(), request.getCheckOutDate(), 1, sellableCapacity);

            Instant checkInDeadline = LocalDateTime.of(request.getCheckInDate(), LocalTime.of(14, 0))
                    .atZone(ZoneId.of(timeZone))
                    .toInstant();
            Instant createdPlus24h = now.plus(Duration.ofHours(24));
            Instant paymentDueAt = createdPlus24h.isBefore(checkInDeadline) ? createdPlus24h : checkInDeadline;

            builder.reservationStatus(ReservationStatus.CONFIRMED)
                    .paymentStatus(PaymentStatus.PENDING)
                    .holdExpiresAt(null)
                    .paymentDueAt(paymentDueAt);
        }

        Reservation saved = reservationRepository.save(builder.build());

        auditService.recordEvent(
                currentUser.getId(),
                currentUser.getRole(),
                "RESERVATION",
                "RESERVATION_CREATED",
                String.valueOf(saved.getId()),
                "Created " + saved.getPaymentPolicy() + " reservation for hotel " + saved.getHotelId(),
                null,
                "{\"status\":\"" + saved.getReservationStatus() + "\",\"policy\":\"" + saved.getPaymentPolicy() + "\"}"
        );

        return mapToDetailDto(saved, snapshot, null);
    }

    @Transactional(readOnly = true)
    public ReservationDetailDto getReservationById(Long id, UserPrincipal currentUser) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation with ID " + id + " not found"));

        if (!currentUser.getRole().contains("ADMIN") && !reservation.getUserId().equals(currentUser.getId())) {
            throw new ForbiddenException("You are not authorized to view this reservation");
        }

        PricingSnapshotDto snapshot = pricingService.deserializeSnapshot(reservation.getPriceSnapshotJson());
        RoomAssignment assignment = roomAssignmentRepository.findByReservationId(id).orElse(null);
        return mapToDetailDto(reservation, snapshot, assignment);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReservationSummaryDto> getMyReservations(
            UserPrincipal currentUser,
            ReservationStatus status,
            Pageable pageable) {

        Page<Reservation> page = (status != null)
                ? reservationRepository.findByUserIdAndReservationStatus(currentUser.getId(), status, pageable)
                : reservationRepository.findByUserId(currentUser.getId(), pageable);

        Page<ReservationSummaryDto> summaryPage = page.map(r -> ReservationSummaryDto.builder()
                .id(r.getId())
                .publicReference(r.getPublicReference())
                .userId(r.getUserId())
                .hotelId(r.getHotelId())
                .roomTypeId(r.getRoomTypeId())
                .checkInDate(r.getCheckInDate())
                .checkOutDate(r.getCheckOutDate())
                .nights(r.getNights())
                .guestCount(r.getGuestCount())
                .reservationStatus(r.getReservationStatus())
                .paymentPolicy(r.getPaymentPolicy())
                .paymentStatus(r.getPaymentStatus())
                .totalAmount(r.getTotalAmount())
                .createdAt(r.getCreatedAt())
                .build());

        return PageUtils.toPageResponse(summaryPage);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReservationSummaryDto> getAllReservationsForAdmin(
            ReservationStatus status,
            Pageable pageable) {

        Page<Reservation> page = (status != null)
                ? reservationRepository.findByReservationStatus(status, pageable)
                : reservationRepository.findAll(pageable);

        Page<ReservationSummaryDto> summaryPage = page.map(r -> ReservationSummaryDto.builder()
                .id(r.getId())
                .publicReference(r.getPublicReference())
                .userId(r.getUserId())
                .hotelId(r.getHotelId())
                .roomTypeId(r.getRoomTypeId())
                .checkInDate(r.getCheckInDate())
                .checkOutDate(r.getCheckOutDate())
                .nights(r.getNights())
                .guestCount(r.getGuestCount())
                .reservationStatus(r.getReservationStatus())
                .paymentPolicy(r.getPaymentPolicy())
                .paymentStatus(r.getPaymentStatus())
                .totalAmount(r.getTotalAmount())
                .createdAt(r.getCreatedAt())
                .build());

        return PageUtils.toPageResponse(summaryPage);
    }

    @Transactional
    public CancellationSummaryDto cancelReservation(
            Long id,
            CancelReservationRequest request,
            UserPrincipal currentUser,
            String idempotencyKey) {

        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation with ID " + id + " not found"));

        if (!currentUser.getRole().contains("ADMIN") && !reservation.getUserId().equals(currentUser.getId())) {
            throw new ForbiddenException("You are not authorized to cancel this reservation");
        }

        // Idempotent return if already cancelled
        if (reservation.getReservationStatus() == ReservationStatus.CANCELLED) {
            log.info("Reservation {} already cancelled; returning idempotent cancellation summary", id);
            return buildCancellationSummary(reservation);
        }

        if (reservation.getReservationStatus() == ReservationStatus.CHECKED_IN
                || reservation.getReservationStatus() == ReservationStatus.CHECKED_OUT) {
            throw new InvalidStateTransitionException("Cannot cancel reservation in status: " + reservation.getReservationStatus());
        }

        Instant now = Instant.now();
        Instant scheduledCheckIn = LocalDateTime.of(reservation.getCheckInDate(), LocalTime.of(14, 0))
                .atZone(ZoneId.of(timeZone))
                .toInstant();

        double hoursBeforeCheckIn = Duration.between(now, scheduledCheckIn).toMinutes() / 60.0;
        BigDecimal totalPaid = (reservation.getPaymentStatus() == PaymentStatus.PAID)
                ? reservation.getTotalAmount()
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal penalty = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal refund = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        String reason;

        PricingSnapshotDto snapshot = pricingService.deserializeSnapshot(reservation.getPriceSnapshotJson());

        if (hoursBeforeCheckIn >= 24.0) {
            penalty = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            refund = totalPaid;
            reason = CancellationReason.GUEST_FREE.name();
        } else {
            BigDecimal calculatedPenalty = pricingService.calculateOneNightPenalty(snapshot);
            penalty = calculatedPenalty.min(totalPaid);
            refund = totalPaid.subtract(penalty).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            reason = CancellationReason.GUEST_PENALTY.name();
        }

        // Release inventory
        if (reservation.getReservationStatus() == ReservationStatus.CONFIRMED) {
            inventoryLedgerService.releaseReservation(
                    reservation.getRoomTypeId(), reservation.getCheckInDate(), reservation.getCheckOutDate(), 1);
        } else if (reservation.getReservationStatus() == ReservationStatus.PENDING_PAYMENT) {
            inventoryLedgerService.releaseHold(
                    reservation.getRoomTypeId(), reservation.getCheckInDate(), reservation.getCheckOutDate(), 1);
        }

        reservation.setReservationStatus(ReservationStatus.CANCELLED);
        reservation.setCancelledAt(now);
        reservation.setCancellationReason(reason);
        reservation.setCancellationNotes(request != null ? request.getCancellationReason() : null);
        reservation.setPenaltyAmount(penalty);
        reservation.setRefundAmount(refund);

        if (refund.compareTo(BigDecimal.ZERO) > 0) {
            reservation.setPaymentStatus(PaymentStatus.REFUNDED);
        }

        reservationRepository.save(reservation);

        auditService.recordEvent(
                currentUser.getId(),
                currentUser.getRole(),
                "CANCELLATION",
                "RESERVATION_CANCELLED",
                String.valueOf(id),
                "Cancelled reservation: reason=" + reason + ", penalty=" + penalty + ", refund=" + refund,
                "{\"status\":\"CONFIRMED\"}",
                "{\"status\":\"CANCELLED\",\"refund\":" + refund + "}"
        );

        return buildCancellationSummary(reservation, hoursBeforeCheckIn, totalPaid);
    }

    @Transactional
    public ReservationDetailDto amendReservation(
            Long id,
            AmendReservationRequest request,
            UserPrincipal currentUser) {

        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation with ID " + id + " not found"));

        if (!currentUser.getRole().contains("ADMIN") && !reservation.getUserId().equals(currentUser.getId())) {
            throw new ForbiddenException("You are not authorized to amend this reservation");
        }

        if (reservation.getReservationStatus() != ReservationStatus.CONFIRMED
                && reservation.getReservationStatus() != ReservationStatus.PENDING_PAYMENT) {
            throw new InvalidStateTransitionException("Cannot amend reservation in status " + reservation.getReservationStatus());
        }

        LocalDate today = LocalDate.now(ZoneId.of(timeZone));
        if (!today.isBefore(reservation.getCheckInDate())) {
            throw new InvalidStateTransitionException("Amendments are only permitted prior to the scheduled check-in date");
        }

        validateDates(request.getNewCheckInDate(), request.getNewCheckOutDate());

        Long targetRoomTypeId = request.getNewRoomTypeId() != null ? request.getNewRoomTypeId() : reservation.getRoomTypeId();
        int targetGuests = request.getNewGuestCount() != null ? request.getNewGuestCount() : reservation.getGuestCount();

        PropertyCapacityDto capacityDto = propertyClient.getSellableCapacity(targetRoomTypeId);
        int sellableCapacity = capacityDto.getSellableCapacity() != null ? capacityDto.getSellableCapacity() : 0;

        String beforeJson = reservation.getPriceSnapshotJson();

        // Atomically reallocate inventory with global lock ordering (roomTypeId ASC, stayDate ASC)
        inventoryLedgerService.amendInventory(
                reservation.getRoomTypeId(),
                reservation.getCheckInDate(),
                reservation.getCheckOutDate(),
                targetRoomTypeId,
                request.getNewCheckInDate(),
                request.getNewCheckOutDate(),
                1,
                sellableCapacity,
                reservation.getReservationStatus() == ReservationStatus.CONFIRMED
        );

        // 3. Reprice with current rates
        PropertyPricingDto pricingDto = propertyClient.getPricing(
                targetRoomTypeId, request.getNewCheckInDate(), request.getNewCheckOutDate());
        List<PropertyNightlyRateDto> nightlyRates = pricingDto.getNightlyRates() != null
                ? pricingDto.getNightlyRates()
                : List.of();

        PricingSnapshotDto newSnapshot = pricingService.calculatePricingSnapshot(nightlyRates);
        int nights = (int) ChronoUnit.DAYS.between(request.getNewCheckInDate(), request.getNewCheckOutDate());

        reservation.setRoomTypeId(targetRoomTypeId);
        reservation.setCheckInDate(request.getNewCheckInDate());
        reservation.setCheckOutDate(request.getNewCheckOutDate());
        reservation.setNights(nights);
        reservation.setGuestCount(targetGuests);
        reservation.setLodgingSubtotal(newSnapshot.getLodgingSubtotal());
        reservation.setTaxPercentage(newSnapshot.getTaxPercentage());
        reservation.setTaxAmount(newSnapshot.getTaxAmount());
        reservation.setTotalAmount(newSnapshot.getTotalAmount());
        reservation.setPriceSnapshotJson(pricingService.serializeSnapshot(newSnapshot));

        Reservation saved = reservationRepository.save(reservation);

        auditService.recordEvent(
                currentUser.getId(),
                currentUser.getRole(),
                "AMENDMENT",
                "RESERVATION_AMENDED",
                String.valueOf(id),
                "Amended reservation dates to " + request.getNewCheckInDate() + " to " + request.getNewCheckOutDate(),
                beforeJson,
                reservation.getPriceSnapshotJson()
        );

        RoomAssignment assignment = roomAssignmentRepository.findByReservationId(id).orElse(null);
        return mapToDetailDto(saved, newSnapshot, assignment);
    }

    @Transactional
    public CheckInResponseDto checkIn(Long id, UserPrincipal currentUser) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation with ID " + id + " not found"));

        if (!currentUser.getRole().contains("ADMIN") && !reservation.getUserId().equals(currentUser.getId())) {
            throw new ForbiddenException("You are not authorized to check in to this reservation");
        }

        if (reservation.getReservationStatus() != ReservationStatus.CONFIRMED) {
            throw new InvalidStateTransitionException("Check-in is only permitted for CONFIRMED reservations (current: " + reservation.getReservationStatus() + ")");
        }

        if (reservation.getPaymentStatus() != PaymentStatus.PAID) {
            throw new ReservationConflictException("Reservation must be fully paid prior to check-in");
        }

        LocalDate today = LocalDate.now(ZoneId.of(timeZone));
        if (!reservation.getCheckInDate().isEqual(today)) {
            throw new ReservationConflictException("Check-in is only allowed on the scheduled check-in date (" + reservation.getCheckInDate() + ")");
        }

        // Call Property Service for clean room
        AssignCleanRoomRequest assignRequest = AssignCleanRoomRequest.builder()
                .hotelId(reservation.getHotelId())
                .roomTypeId(reservation.getRoomTypeId())
                .reservationId(reservation.getId())
                .build();

        AssignCleanRoomResponse roomResponse = propertyClient.assignCleanRoom(assignRequest);

        Instant now = Instant.now();
        RoomAssignment assignment = RoomAssignment.builder()
                .reservationId(reservation.getId())
                .physicalRoomId(roomResponse.getPhysicalRoomId())
                .physicalRoomNumber(roomResponse.getRoomNumber())
                .assignedAt(now)
                .checkedInAt(now)
                .build();
        roomAssignmentRepository.save(assignment);

        reservation.setReservationStatus(ReservationStatus.CHECKED_IN);
        reservation.setCheckedInAt(now);
        reservationRepository.save(reservation);

        auditService.recordEvent(
                currentUser.getId(),
                currentUser.getRole(),
                "CHECK_IN",
                "GUEST_CHECKED_IN",
                String.valueOf(id),
                "Assigned room " + roomResponse.getRoomNumber() + " at check-in",
                "{\"status\":\"CONFIRMED\"}",
                "{\"status\":\"CHECKED_IN\",\"roomNumber\":\"" + roomResponse.getRoomNumber() + "\"}"
        );

        return CheckInResponseDto.builder()
                .reservationId(reservation.getId())
                .reservationStatus(ReservationStatus.CHECKED_IN)
                .physicalRoomNumber(roomResponse.getRoomNumber())
                .checkedInAt(now)
                .build();
    }

    @Transactional
    public CheckOutResponseDto checkOut(Long id, UserPrincipal currentUser) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation with ID " + id + " not found"));

        if (!currentUser.getRole().contains("ADMIN") && !reservation.getUserId().equals(currentUser.getId())) {
            throw new ForbiddenException("You are not authorized to check out of this reservation");
        }

        if (reservation.getReservationStatus() != ReservationStatus.CHECKED_IN) {
            throw new InvalidStateTransitionException("Check-out requires status CHECKED_IN (current: " + reservation.getReservationStatus() + ")");
        }

        Instant now = Instant.now();
        RoomAssignment assignment = roomAssignmentRepository.findByReservationId(id).orElse(null);
        if (assignment != null) {
            assignment.setCheckedOutAt(now);
            roomAssignmentRepository.save(assignment);
            // Mark physical room DIRTY in Property Service
            propertyClient.markRoomDirty(assignment.getPhysicalRoomId());
        }

        reservation.setReservationStatus(ReservationStatus.CHECKED_OUT);
        reservation.setCheckedOutAt(now);
        reservationRepository.save(reservation);

        auditService.recordEvent(
                currentUser.getId(),
                currentUser.getRole(),
                "CHECK_OUT",
                "GUEST_CHECKED_OUT",
                String.valueOf(id),
                "Completed self-checkout; room transitioned to DIRTY",
                "{\"status\":\"CHECKED_IN\"}",
                "{\"status\":\"CHECKED_OUT\"}"
        );

        return CheckOutResponseDto.builder()
                .reservationId(reservation.getId())
                .reservationStatus(ReservationStatus.CHECKED_OUT)
                .checkedOutAt(now)
                .build();
    }

    @Transactional
    public ReservationDetailDto noShow(Long id, NoShowRequest request) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation with ID " + id + " not found"));

        if (reservation.getReservationStatus() != ReservationStatus.CONFIRMED) {
            throw new InvalidStateTransitionException("No-show is only valid for CONFIRMED reservations");
        }

        LocalDate today = LocalDate.now(ZoneId.of(timeZone));
        if (today.isBefore(reservation.getCheckInDate())) {
            throw new ReservationConflictException("Cannot mark NO_SHOW before scheduled check-in date");
        }

        // Release remaining inventory for unexpired stay dates
        LocalDate releaseStart = today.isAfter(reservation.getCheckInDate()) ? today : reservation.getCheckInDate();
        if (releaseStart.isBefore(reservation.getCheckOutDate())) {
            inventoryLedgerService.releaseReservation(
                    reservation.getRoomTypeId(), releaseStart, reservation.getCheckOutDate(), 1);
        }

        reservation.setReservationStatus(ReservationStatus.NO_SHOW);
        Reservation saved = reservationRepository.save(reservation);

        auditService.recordEvent(
                0L,
                "ADMIN",
                "NO_SHOW",
                "ADMIN_MARKED_NO_SHOW",
                String.valueOf(id),
                "Admin marked reservation as NO_SHOW: " + (request != null ? request.getAdminNotes() : ""),
                "{\"status\":\"CONFIRMED\"}",
                "{\"status\":\"NO_SHOW\"}"
        );

        PricingSnapshotDto snapshot = pricingService.deserializeSnapshot(saved.getPriceSnapshotJson());
        return mapToDetailDto(saved, snapshot, null);
    }

    @Transactional(readOnly = true)
    public InvoiceDto getInvoice(Long id, UserPrincipal currentUser) {
        Reservation reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reservation with ID " + id + " not found"));

        if (!currentUser.getRole().contains("ADMIN") && !reservation.getUserId().equals(currentUser.getId())) {
            throw new ForbiddenException("You are not authorized to view this invoice");
        }

        PropertyHotelDto hotel = propertyClient.getHotelDetails(reservation.getHotelId());
        PricingSnapshotDto snapshot = pricingService.deserializeSnapshot(reservation.getPriceSnapshotJson());
        RoomAssignment assignment = roomAssignmentRepository.findByReservationId(id).orElse(null);

        List<PaymentTransaction> transactions = paymentTransactionRepository.findByReservationId(id);
        PaymentTransaction paidTx = transactions.stream()
                .filter(tx -> tx.getPaymentStatus() == PaymentStatus.PAID)
                .findFirst()
                .orElse(null);

        BigDecimal paidAmount = (reservation.getPaymentStatus() == PaymentStatus.PAID)
                ? reservation.getTotalAmount()
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        BigDecimal balanceDue = reservation.getTotalAmount().subtract(paidAmount).setScale(2, RoundingMode.HALF_UP);

        List<NightlyRateItemDto> rateItems = snapshot.getNightlyRates().stream()
                .map(r -> NightlyRateItemDto.builder()
                        .date(r.getDate())
                        .rate(r.getRate())
                        .rateType(r.getRateType())
                        .build())
                .toList();

        return InvoiceDto.builder()
                .invoiceNumber("INV-" + LocalDate.now().getYear() + "-" + reservation.getId())
                .invoiceDate(reservation.getCreatedAt())
                .reservationReference(reservation.getPublicReference())
                .hotel(InvoiceHotelDto.builder()
                        .name(hotel.getName())
                        .address(hotel.getAddress())
                        .contactPhone(hotel.getContactPhone())
                        .build())
                .guest(InvoiceGuestDto.builder()
                        .fullName(currentUser.getUsername())
                        .email(currentUser.getEmail())
                        .phone("")
                        .build())
                .stay(InvoiceStayDto.builder()
                        .roomTypeName("Room Type " + reservation.getRoomTypeId())
                        .checkInDate(reservation.getCheckInDate())
                        .checkOutDate(reservation.getCheckOutDate())
                        .nights(reservation.getNights())
                        .roomNumber(assignment != null ? assignment.getPhysicalRoomNumber() : "Unassigned")
                        .build())
                .charges(InvoiceChargesDto.builder()
                        .nightlyRates(rateItems)
                        .lodgingSubtotal(snapshot.getLodgingSubtotal())
                        .taxPercentage(snapshot.getTaxPercentage())
                        .taxAmount(snapshot.getTaxAmount())
                        .totalAmount(snapshot.getTotalAmount())
                        .amountPaid(paidAmount)
                        .balanceDue(balanceDue)
                        .build())
                .paymentDetails(InvoicePaymentDetailsDto.builder()
                        .paymentMethod(paidTx != null ? paidTx.getPaymentMethod() : null)
                        .paymentStatus(reservation.getPaymentStatus())
                        .transactionId(paidTx != null ? paidTx.getTransactionReference() : null)
                        .build())
                .build();
    }

    @Transactional(readOnly = true)
    public AdminDashboardDto getAdminDashboard() {
        Map<String, Long> statusCounts = new HashMap<>();
        for (ReservationStatus s : ReservationStatus.values()) {
            statusCounts.put(s.name(), reservationRepository.countByReservationStatus(s));
        }

        LocalDate today = LocalDate.now(ZoneId.of(timeZone));
        long todayCheckIns = reservationRepository.countByCheckInDate(today);
        long todayCheckOuts = reservationRepository.countByCheckOutDate(today);
        long pendingPayments = reservationRepository.countByPaymentStatus(PaymentStatus.PENDING);
        BigDecimal revenue = reservationRepository.sumTotalAmountByPaymentStatus(PaymentStatus.PAID);
        long cancellations = reservationRepository.countByReservationStatus(ReservationStatus.CANCELLED);
        long noShows = reservationRepository.countByReservationStatus(ReservationStatus.NO_SHOW);

        return AdminDashboardDto.builder()
                .reservationCountsByStatus(statusCounts)
                .todayCheckIns(todayCheckIns)
                .todayCheckOuts(todayCheckOuts)
                .currentOccupancyRate(0.0) // Aggregated from room inventory
                .pendingPaymentsCount(pendingPayments)
                .totalRevenueCollected(revenue != null ? revenue : BigDecimal.ZERO)
                .cancellationsCount(cancellations)
                .noShowCount(noShows)
                .partialDataWarning(null)
                .build();
    }

    @Transactional(readOnly = true)
    public ReviewEligibilityResponse getReviewEligibility(Long reservationId, Long userId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElse(null);

        if (reservation == null) {
            return ReviewEligibilityResponse.builder()
                    .eligible(false)
                    .reservationId(reservationId)
                    .userId(userId)
                    .reason("Reservation not found")
                    .build();
        }

        if (!reservation.getUserId().equals(userId)) {
            return ReviewEligibilityResponse.builder()
                    .eligible(false)
                    .reservationId(reservationId)
                    .userId(userId)
                    .hotelId(reservation.getHotelId())
                    .reason("Reservation does not belong to specified user")
                    .build();
        }

        if (reservation.getReservationStatus() != ReservationStatus.CHECKED_OUT) {
            return ReviewEligibilityResponse.builder()
                    .eligible(false)
                    .reservationId(reservationId)
                    .userId(userId)
                    .hotelId(reservation.getHotelId())
                    .reason("Reservation is not in CHECKED_OUT status")
                    .build();
        }

        Instant checkoutInstant = reservation.getCheckedOutAt();
        if (checkoutInstant != null) {
            long daysSinceCheckout = Duration.between(checkoutInstant, Instant.now()).toDays();
            if (daysSinceCheckout > 30) {
                return ReviewEligibilityResponse.builder()
                        .eligible(false)
                        .reservationId(reservationId)
                        .userId(userId)
                        .hotelId(reservation.getHotelId())
                        .reason("Review submission window expired (> 30 days since checkout)")
                        .build();
            }
        }

        return ReviewEligibilityResponse.builder()
                .eligible(true)
                .reservationId(reservationId)
                .userId(userId)
                .hotelId(reservation.getHotelId())
                .guestName("Guest " + userId)
                .reason("Eligible for review")
                .build();
    }

    private void validateDates(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null) {
            throw new InvalidDateRangeException("Check-in and check-out dates are required");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new InvalidDateRangeException("Check-out date must be after check-in date");
        }
        LocalDate today = LocalDate.now(ZoneId.of(timeZone));
        if (checkIn.isBefore(today)) {
            throw new InvalidDateRangeException("Check-in date cannot be in the past");
        }
    }

    private void validateGuests(CreateReservationRequest request) {
        if (request.getAdultCount() != null && request.getAdultCount() < 1) {
            throw new InvalidDateRangeException("At least 1 adult is required to make a booking. Child-only bookings are not allowed.");
        }
        if (request.getGuestCount() == null || request.getGuestCount() < 1) {
            throw new InvalidDateRangeException("Guest count must be at least 1");
        }
        if (request.getAdultCount() != null && request.getChildCount() != null) {
            request.setGuestCount(request.getAdultCount() + request.getChildCount());
        }
    }

    private ReservationDetailDto mapToDetailDto(Reservation r, PricingSnapshotDto snapshot, RoomAssignment assignment) {
        return ReservationDetailDto.builder()
                .id(r.getId())
                .publicReference(r.getPublicReference())
                .userId(r.getUserId())
                .hotelId(r.getHotelId())
                .roomTypeId(r.getRoomTypeId())
                .checkInDate(r.getCheckInDate())
                .checkOutDate(r.getCheckOutDate())
                .nights(r.getNights())
                .guestCount(r.getGuestCount())
                .reservationStatus(r.getReservationStatus())
                .paymentPolicy(r.getPaymentPolicy())
                .paymentStatus(r.getPaymentStatus())
                .holdExpiresAt(r.getHoldExpiresAt())
                .paymentDueAt(r.getPaymentDueAt())
                .pricingSnapshot(snapshot)
                .createdAt(r.getCreatedAt())
                .confirmedAt(r.getConfirmedAt())
                .cancelledAt(r.getCancelledAt())
                .cancellationReason(r.getCancellationReason())
                .cancellationNotes(r.getCancellationNotes())
                .penaltyAmount(r.getPenaltyAmount())
                .refundAmount(r.getRefundAmount())
                .physicalRoomId(assignment != null ? assignment.getPhysicalRoomId() : null)
                .physicalRoomNumber(assignment != null ? assignment.getPhysicalRoomNumber() : null)
                .checkedInAt(r.getCheckedInAt())
                .checkedOutAt(r.getCheckedOutAt())
                .build();
    }

    private CancellationSummaryDto buildCancellationSummary(Reservation r) {
        Instant scheduledCheckIn = LocalDateTime.of(r.getCheckInDate(), LocalTime.of(14, 0))
                .atZone(ZoneId.of(timeZone))
                .toInstant();
        double hoursBefore = Duration.between(r.getCancelledAt() != null ? r.getCancelledAt() : Instant.now(), scheduledCheckIn).toMinutes() / 60.0;
        BigDecimal paid = (r.getPaymentStatus() == PaymentStatus.PAID || r.getPaymentStatus() == PaymentStatus.REFUNDED)
                ? r.getTotalAmount()
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return buildCancellationSummary(r, hoursBefore, paid);
    }

    private CancellationSummaryDto buildCancellationSummary(Reservation r, double hoursBeforeCheckIn, BigDecimal totalPaid) {
        return CancellationSummaryDto.builder()
                .reservationId(r.getId())
                .status(r.getReservationStatus().name())
                .cancelledAt(r.getCancelledAt())
                .hoursBeforeCheckIn(hoursBeforeCheckIn)
                .totalPaid(totalPaid)
                .cancellationPenalty(r.getPenaltyAmount() != null ? r.getPenaltyAmount() : BigDecimal.ZERO)
                .refundAmount(r.getRefundAmount() != null ? r.getRefundAmount() : BigDecimal.ZERO)
                .refundStatus(r.getPaymentStatus().name())
                .build();
    }
}
