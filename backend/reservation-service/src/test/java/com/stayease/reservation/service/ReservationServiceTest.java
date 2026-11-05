package com.stayease.reservation.service;

import com.stayease.reservation.client.*;
import com.stayease.reservation.dto.*;
import com.stayease.reservation.entity.Reservation;
import com.stayease.reservation.entity.RoomAssignment;
import com.stayease.reservation.enums.PaymentPolicy;
import com.stayease.reservation.enums.PaymentStatus;
import com.stayease.reservation.enums.ReservationStatus;
import com.stayease.reservation.exception.InvalidDateRangeException;
import com.stayease.reservation.exception.InvalidStateTransitionException;
import com.stayease.reservation.exception.ReservationConflictException;
import com.stayease.reservation.repository.PaymentTransactionRepository;
import com.stayease.reservation.repository.ReservationRepository;
import com.stayease.reservation.repository.RoomAssignmentRepository;
import com.stayease.reservation.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private RoomAssignmentRepository roomAssignmentRepository;

    @Mock
    private InventoryLedgerService inventoryLedgerService;

    @Mock
    private PricingService pricingService;

    @Mock
    private PropertyServiceClient propertyClient;

    @Mock
    private ReservationAuditService auditService;

    @InjectMocks
    private ReservationService reservationService;

    private UserPrincipal userPrincipal;
    private UserPrincipal adminPrincipal;
    private PropertyHotelDto hotelDto;
    private PropertyRoomTypeDto roomTypeDto;
    private PricingSnapshotDto pricingSnapshot;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reservationService, "timeZone", "Asia/Kolkata");

        userPrincipal = UserPrincipal.create(101L, "jane@example.com", "ROLE_USER", 1L);
        adminPrincipal = UserPrincipal.create(1L, "admin@example.com", "ROLE_ADMIN", 1L);

        roomTypeDto = PropertyRoomTypeDto.builder()
                .id(10L)
                .name("Deluxe Ocean View")
                .maxGuests(2)
                .baseNightlyRate(new BigDecimal("4500.00"))
                .totalRooms(20)
                .build();

        hotelDto = PropertyHotelDto.builder()
                .id(1L)
                .name("StayEase Grand Palace")
                .destination("Goa")
                .roomTypes(List.of(roomTypeDto))
                .build();

        pricingSnapshot = PricingSnapshotDto.builder()
                .lodgingSubtotal(new BigDecimal("9700.00"))
                .taxPercentage(new BigDecimal("12.00"))
                .taxAmount(new BigDecimal("1164.00"))
                .totalAmount(new BigDecimal("10864.00"))
                .nightlyRates(List.of(
                        NightlyRateSnapshotDto.builder().date(LocalDate.now().plusDays(5)).rate(new BigDecimal("4500.00")).rateType("BASE").build(),
                        NightlyRateSnapshotDto.builder().date(LocalDate.now().plusDays(6)).rate(new BigDecimal("5200.00")).rateType("WEEKEND").build()
                ))
                .build();
    }

    @Test
    @DisplayName("Create PREPAID reservation validates dates, creates 10m hold, status PENDING_PAYMENT")
    void testCreatePrepaidReservation() {
        LocalDate checkIn = LocalDate.now().plusDays(5);
        LocalDate checkOut = LocalDate.now().plusDays(7);

        CreateReservationRequest req = CreateReservationRequest.builder()
                .hotelId(1L)
                .roomTypeId(10L)
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .guestCount(2)
                .paymentPolicy(PaymentPolicy.PREPAID)
                .build();

        when(propertyClient.getHotelDetails(1L)).thenReturn(hotelDto);
        when(propertyClient.getSellableCapacity(10L)).thenReturn(PropertyCapacityDto.builder().sellableCapacity(18).build());
        when(propertyClient.getPricing(10L, checkIn, checkOut)).thenReturn(PropertyPricingDto.builder().build());
        when(pricingService.calculatePricingSnapshot(any())).thenReturn(pricingSnapshot);
        when(pricingService.serializeSnapshot(any())).thenReturn("{\"totalAmount\":10864.00}");
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> {
            Reservation r = i.getArgument(0);
            r.setId(5001L);
            return r;
        });

        ReservationDetailDto detail = reservationService.createReservation(req, userPrincipal);

        assertNotNull(detail);
        assertEquals(ReservationStatus.PENDING_PAYMENT, detail.getReservationStatus());
        assertEquals(PaymentPolicy.PREPAID, detail.getPaymentPolicy());
        assertEquals(PaymentStatus.PENDING, detail.getPaymentStatus());
        assertEquals(2, detail.getNights()); // 2 nights for 3 calendar days (half-open interval)
        assertNotNull(detail.getHoldExpiresAt());

        verify(inventoryLedgerService).checkAndHoldInventory(eq(10L), eq(checkIn), eq(checkOut), eq(1), eq(18));
    }

    @Test
    @DisplayName("Create PAY_LATER reservation reserves inventory directly with paymentDueAt deadline")
    void testCreatePayLaterReservation() {
        LocalDate checkIn = LocalDate.now().plusDays(5);
        LocalDate checkOut = LocalDate.now().plusDays(7);

        CreateReservationRequest req = CreateReservationRequest.builder()
                .hotelId(1L)
                .roomTypeId(10L)
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .guestCount(2)
                .paymentPolicy(PaymentPolicy.PAY_LATER)
                .build();

        when(propertyClient.getHotelDetails(1L)).thenReturn(hotelDto);
        when(propertyClient.getSellableCapacity(10L)).thenReturn(PropertyCapacityDto.builder().sellableCapacity(18).build());
        when(propertyClient.getPricing(10L, checkIn, checkOut)).thenReturn(PropertyPricingDto.builder().build());
        when(pricingService.calculatePricingSnapshot(any())).thenReturn(pricingSnapshot);
        when(pricingService.serializeSnapshot(any())).thenReturn("{\"totalAmount\":10864.00}");
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(i -> {
            Reservation r = i.getArgument(0);
            r.setId(5002L);
            return r;
        });

        ReservationDetailDto detail = reservationService.createReservation(req, userPrincipal);

        assertNotNull(detail);
        assertEquals(ReservationStatus.CONFIRMED, detail.getReservationStatus());
        assertEquals(PaymentPolicy.PAY_LATER, detail.getPaymentPolicy());
        assertEquals(PaymentStatus.PENDING, detail.getPaymentStatus());
        assertNotNull(detail.getPaymentDueAt());

        verify(inventoryLedgerService).checkAndReserveInventory(eq(10L), eq(checkIn), eq(checkOut), eq(1), eq(18));
    }

    @Test
    @DisplayName("Check-in date in the past throws InvalidDateRangeException")
    void testPastCheckInDateThrowsException() {
        CreateReservationRequest req = CreateReservationRequest.builder()
                .hotelId(1L)
                .roomTypeId(10L)
                .checkInDate(LocalDate.now().minusDays(1))
                .checkOutDate(LocalDate.now().plusDays(1))
                .guestCount(2)
                .paymentPolicy(PaymentPolicy.PREPAID)
                .build();

        assertThrows(InvalidDateRangeException.class, () ->
                reservationService.createReservation(req, userPrincipal));
    }

    @Test
    @DisplayName("Check-out date before or equal to check-in date throws InvalidDateRangeException")
    void testInvalidCheckOutDateThrowsException() {
        CreateReservationRequest req = CreateReservationRequest.builder()
                .hotelId(1L)
                .roomTypeId(10L)
                .checkInDate(LocalDate.now().plusDays(2))
                .checkOutDate(LocalDate.now().plusDays(2))
                .guestCount(2)
                .paymentPolicy(PaymentPolicy.PREPAID)
                .build();

        assertThrows(InvalidDateRangeException.class, () ->
                reservationService.createReservation(req, userPrincipal));
    }

    @Test
    @DisplayName("Guest count exceeding room type maxGuests throws InvalidDateRangeException")
    void testGuestCountExceedsCapacity() {
        CreateReservationRequest req = CreateReservationRequest.builder()
                .hotelId(1L)
                .roomTypeId(10L)
                .checkInDate(LocalDate.now().plusDays(2))
                .checkOutDate(LocalDate.now().plusDays(4))
                .guestCount(4) // Max is 2
                .paymentPolicy(PaymentPolicy.PREPAID)
                .build();

        when(propertyClient.getHotelDetails(1L)).thenReturn(hotelDto);

        assertThrows(InvalidDateRangeException.class, () ->
                reservationService.createReservation(req, userPrincipal));
    }

    @Test
    @DisplayName("Free cancellation >= 24h before check-in returns 0 penalty and 100% refund")
    void testFreeCancellationEligible() {
        LocalDate checkIn = LocalDate.now().plusDays(5);
        LocalDate checkOut = LocalDate.now().plusDays(7);

        Reservation res = Reservation.builder()
                .id(5001L)
                .userId(101L)
                .roomTypeId(10L)
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .reservationStatus(ReservationStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID)
                .totalAmount(new BigDecimal("10864.00"))
                .priceSnapshotJson("{\"totalAmount\":10864.00}")
                .build();

        when(reservationRepository.findById(5001L)).thenReturn(Optional.of(res));
        when(pricingService.deserializeSnapshot(any())).thenReturn(pricingSnapshot);
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CancellationSummaryDto summary = reservationService.cancelReservation(
                5001L, new CancelReservationRequest("Change of travel plans"), userPrincipal, "key-cancel-1");

        assertNotNull(summary);
        assertEquals("CANCELLED", summary.getStatus());
        assertEquals(new BigDecimal("0.00"), summary.getCancellationPenalty());
        assertEquals(new BigDecimal("10864.00"), summary.getRefundAmount());
        assertEquals("REFUNDED", summary.getRefundStatus());

        verify(inventoryLedgerService).releaseReservation(eq(10L), eq(checkIn), eq(checkOut), eq(1));
    }

    @Test
    @DisplayName("Late cancellation < 24h before check-in retains 1 night penalty and refunds balance")
    void testLateCancellationPenalty() {
        LocalDate checkIn = LocalDate.now(ZoneId.of("Asia/Kolkata")); // scheduled today at 14:00 -> < 24h away
        LocalDate checkOut = checkIn.plusDays(2);

        Reservation res = Reservation.builder()
                .id(5001L)
                .userId(101L)
                .roomTypeId(10L)
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .reservationStatus(ReservationStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID)
                .totalAmount(new BigDecimal("10864.00"))
                .priceSnapshotJson("{\"totalAmount\":10864.00}")
                .build();

        when(reservationRepository.findById(5001L)).thenReturn(Optional.of(res));
        when(pricingService.deserializeSnapshot(any())).thenReturn(pricingSnapshot);
        when(pricingService.calculateOneNightPenalty(any())).thenReturn(new BigDecimal("5040.00"));
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CancellationSummaryDto summary = reservationService.cancelReservation(
                5001L, new CancelReservationRequest("Emergency"), userPrincipal, "key-cancel-2");

        assertNotNull(summary);
        assertEquals("CANCELLED", summary.getStatus());
        assertEquals(new BigDecimal("5040.00"), summary.getCancellationPenalty());
        assertEquals(new BigDecimal("5824.00"), summary.getRefundAmount()); // 10864 - 5040 = 5824
        verify(inventoryLedgerService).releaseReservation(eq(10L), eq(checkIn), eq(checkOut), eq(1));
    }

    @Test
    @DisplayName("Cancellation is idempotent when called repeatedly")
    void testCancellationIsIdempotent() {
        Reservation res = Reservation.builder()
                .id(5001L)
                .userId(101L)
                .checkInDate(LocalDate.now().plusDays(5))
                .checkOutDate(LocalDate.now().plusDays(7))
                .reservationStatus(ReservationStatus.CANCELLED)
                .paymentStatus(PaymentStatus.REFUNDED)
                .penaltyAmount(BigDecimal.ZERO)
                .refundAmount(new BigDecimal("10864.00"))
                .totalAmount(new BigDecimal("10864.00"))
                .cancelledAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        when(reservationRepository.findById(5001L)).thenReturn(Optional.of(res));

        CancellationSummaryDto summary = reservationService.cancelReservation(
                5001L, new CancelReservationRequest("Dup"), userPrincipal, "key-dup-cancel");

        assertNotNull(summary);
        assertEquals("CANCELLED", summary.getStatus());
        // Verify inventory release was NOT called again
        verify(inventoryLedgerService, never()).releaseReservation(any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("Cannot cancel a reservation that is already CHECKED_IN")
    void testCannotCancelCheckedInReservation() {
        Reservation res = Reservation.builder()
                .id(5001L)
                .userId(101L)
                .reservationStatus(ReservationStatus.CHECKED_IN)
                .build();

        when(reservationRepository.findById(5001L)).thenReturn(Optional.of(res));

        assertThrows(InvalidStateTransitionException.class, () ->
                reservationService.cancelReservation(5001L, null, userPrincipal, "key"));
    }

    @Test
    @DisplayName("Check-in requires status CONFIRMED and PAID, and assigns clean room")
    void testCheckInSuccess() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        Reservation res = Reservation.builder()
                .id(5001L)
                .userId(101L)
                .hotelId(1L)
                .roomTypeId(10L)
                .checkInDate(today)
                .checkOutDate(today.plusDays(2))
                .reservationStatus(ReservationStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID)
                .build();

        when(reservationRepository.findById(5001L)).thenReturn(Optional.of(res));
        when(propertyClient.assignCleanRoom(any())).thenReturn(
                AssignCleanRoomResponse.builder().physicalRoomId(301L).roomNumber("104-A").build());
        when(roomAssignmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CheckInResponseDto checkInResponse = reservationService.checkIn(5001L, userPrincipal);

        assertNotNull(checkInResponse);
        assertEquals(ReservationStatus.CHECKED_IN, checkInResponse.getReservationStatus());
        assertEquals("104-A", checkInResponse.getPhysicalRoomNumber());
        assertEquals(ReservationStatus.CHECKED_IN, res.getReservationStatus());
    }

    @Test
    @DisplayName("Check-in fails with 409 if reservation is not PAID")
    void testCheckInFailsIfNotPaid() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        Reservation res = Reservation.builder()
                .id(5001L)
                .userId(101L)
                .checkInDate(today)
                .reservationStatus(ReservationStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        when(reservationRepository.findById(5001L)).thenReturn(Optional.of(res));

        assertThrows(ReservationConflictException.class, () ->
                reservationService.checkIn(5001L, userPrincipal));
    }

    @Test
    @DisplayName("Check-in fails with 409 if date does not match scheduled checkInDate")
    void testCheckInFailsIfWrongDate() {
        Reservation res = Reservation.builder()
                .id(5001L)
                .userId(101L)
                .checkInDate(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(2))
                .reservationStatus(ReservationStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID)
                .build();

        when(reservationRepository.findById(5001L)).thenReturn(Optional.of(res));

        assertThrows(ReservationConflictException.class, () ->
                reservationService.checkIn(5001L, userPrincipal));
    }

    @Test
    @DisplayName("Check-out transitions reservation to CHECKED_OUT and calls markRoomDirty")
    void testCheckOutSuccess() {
        Reservation res = Reservation.builder()
                .id(5001L)
                .userId(101L)
                .reservationStatus(ReservationStatus.CHECKED_IN)
                .build();

        RoomAssignment assignment = RoomAssignment.builder()
                .reservationId(5001L)
                .physicalRoomId(301L)
                .physicalRoomNumber("104-A")
                .build();

        when(reservationRepository.findById(5001L)).thenReturn(Optional.of(res));
        when(roomAssignmentRepository.findByReservationId(5001L)).thenReturn(Optional.of(assignment));
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CheckOutResponseDto checkOutResponse = reservationService.checkOut(5001L, userPrincipal);

        assertNotNull(checkOutResponse);
        assertEquals(ReservationStatus.CHECKED_OUT, checkOutResponse.getReservationStatus());
        assertEquals(ReservationStatus.CHECKED_OUT, res.getReservationStatus());

        verify(propertyClient).markRoomDirty(301L);
    }

    @Test
    @DisplayName("No-show marks reservation NO_SHOW and releases remaining inventory")
    void testNoShowSuccess() {
        LocalDate pastCheckIn = LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(1);
        LocalDate checkOut = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(2);

        Reservation res = Reservation.builder()
                .id(5001L)
                .roomTypeId(10L)
                .checkInDate(pastCheckIn)
                .checkOutDate(checkOut)
                .reservationStatus(ReservationStatus.CONFIRMED)
                .priceSnapshotJson("{\"totalAmount\":10864.00}")
                .build();

        when(reservationRepository.findById(5001L)).thenReturn(Optional.of(res));
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(pricingService.deserializeSnapshot(any())).thenReturn(pricingSnapshot);

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        ReservationDetailDto result = reservationService.noShow(5001L, new NoShowRequest("Guest did not arrive"));

        assertNotNull(result);
        assertEquals(ReservationStatus.NO_SHOW, result.getReservationStatus());
        verify(inventoryLedgerService).releaseReservation(eq(10L), eq(today), eq(checkOut), eq(1));
    }

    @Test
    @DisplayName("Late cancellation < 24h for unpaid reservation caps penalty to totalPaid (0.00)")
    void testLateCancellationUnpaidReservationZeroPenalty() {
        LocalDate checkIn = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        LocalDate checkOut = checkIn.plusDays(2);

        Reservation res = Reservation.builder()
                .id(5001L)
                .userId(101L)
                .roomTypeId(10L)
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .reservationStatus(ReservationStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PENDING)
                .totalAmount(new BigDecimal("10864.00"))
                .priceSnapshotJson("{\"totalAmount\":10864.00}")
                .build();

        when(reservationRepository.findById(5001L)).thenReturn(Optional.of(res));
        when(pricingService.deserializeSnapshot(any())).thenReturn(pricingSnapshot);
        when(pricingService.calculateOneNightPenalty(any())).thenReturn(new BigDecimal("5040.00"));
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CancellationSummaryDto summary = reservationService.cancelReservation(
                5001L, new CancelReservationRequest("Cancelled before pay"), userPrincipal, "key-cancel-unpaid");

        assertNotNull(summary);
        assertEquals("CANCELLED", summary.getStatus());
        assertEquals(new BigDecimal("0.00"), summary.getCancellationPenalty());
        assertEquals(new BigDecimal("0.00"), summary.getRefundAmount());
    }

    @Test
    @DisplayName("Amend reservation before check-in atomically reallocates inventory and reprices")
    void testAmendReservationSuccess() {
        LocalDate checkIn = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(5);
        LocalDate checkOut = checkIn.plusDays(7);
        LocalDate newCheckIn = checkIn.plusDays(1);
        LocalDate newCheckOut = checkOut.plusDays(2);

        Reservation res = Reservation.builder()
                .id(5001L)
                .userId(101L)
                .roomTypeId(10L)
                .guestCount(2)
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .nights(2)
                .reservationStatus(ReservationStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID)
                .totalAmount(new BigDecimal("10864.00"))
                .priceSnapshotJson("{\"totalAmount\":10864.00}")
                .build();

        AmendReservationRequest req = AmendReservationRequest.builder()
                .newCheckInDate(newCheckIn)
                .newCheckOutDate(newCheckOut)
                .newRoomTypeId(20L)
                .newGuestCount(2)
                .build();

        when(reservationRepository.findById(5001L)).thenReturn(Optional.of(res));
        when(propertyClient.getSellableCapacity(20L)).thenReturn(PropertyCapacityDto.builder().sellableCapacity(15).build());
        when(propertyClient.getPricing(eq(20L), eq(newCheckIn), eq(newCheckOut))).thenReturn(PropertyPricingDto.builder().nightlyRates(List.of()).build());
        when(pricingService.calculatePricingSnapshot(any())).thenReturn(pricingSnapshot);
        when(pricingService.serializeSnapshot(any())).thenReturn("{\"newSnapshot\":true}");
        when(reservationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ReservationDetailDto result = reservationService.amendReservation(5001L, req, userPrincipal);

        assertNotNull(result);
        assertEquals(20L, res.getRoomTypeId());
        assertEquals(newCheckIn, res.getCheckInDate());
        assertEquals(newCheckOut, res.getCheckOutDate());
        verify(inventoryLedgerService).amendInventory(eq(10L), eq(checkIn), eq(checkOut), eq(20L), eq(newCheckIn), eq(newCheckOut), eq(1), eq(15), eq(true));
    }

    @Test
    @DisplayName("Amend reservation fails if check-in date has already arrived")
    void testAmendReservationFailsOnOrAfterCheckInDate() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        Reservation res = Reservation.builder()
                .id(5001L)
                .userId(101L)
                .checkInDate(today)
                .checkOutDate(today.plusDays(2))
                .reservationStatus(ReservationStatus.CONFIRMED)
                .build();

        when(reservationRepository.findById(5001L)).thenReturn(Optional.of(res));

        AmendReservationRequest req = AmendReservationRequest.builder()
                .newCheckInDate(today.plusDays(1))
                .newCheckOutDate(today.plusDays(3))
                .build();

        assertThrows(InvalidStateTransitionException.class, () ->
                reservationService.amendReservation(5001L, req, userPrincipal));
    }

    @Test
    @DisplayName("Review eligibility is valid for checked-out reservation within 30 days")
    void testReviewEligibilityValid() {
        Reservation res = Reservation.builder()
                .id(5001L)
                .userId(101L)
                .hotelId(1L)
                .reservationStatus(ReservationStatus.CHECKED_OUT)
                .checkedOutAt(Instant.now().minus(5, ChronoUnit.DAYS))
                .build();

        when(reservationRepository.findById(5001L)).thenReturn(Optional.of(res));

        ReviewEligibilityResponse response = reservationService.getReviewEligibility(5001L, 101L);

        assertTrue(response.isEligible());
        assertEquals(5001L, response.getReservationId());
        assertEquals(1L, response.getHotelId());
    }

    @Test
    @DisplayName("Review eligibility is invalid if more than 30 days have elapsed since checkout")
    void testReviewEligibilityExpired() {
        Reservation res = Reservation.builder()
                .id(5001L)
                .userId(101L)
                .hotelId(1L)
                .reservationStatus(ReservationStatus.CHECKED_OUT)
                .checkedOutAt(Instant.now().minus(35, ChronoUnit.DAYS))
                .build();

        when(reservationRepository.findById(5001L)).thenReturn(Optional.of(res));

        ReviewEligibilityResponse response = reservationService.getReviewEligibility(5001L, 101L);

        assertFalse(response.isEligible());
        assertTrue(response.getReason().contains("expired"));
    }
}
