package com.stayease.reservation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stayease.reservation.client.*;
import com.stayease.reservation.dto.*;
import com.stayease.reservation.entity.Reservation;
import com.stayease.reservation.enums.PaymentPolicy;
import com.stayease.reservation.enums.PaymentStatus;
import com.stayease.reservation.enums.ReservationStatus;
import com.stayease.reservation.repository.PaymentTransactionRepository;
import com.stayease.reservation.repository.ReservationAuditEventRepository;
import com.stayease.reservation.repository.ReservationRepository;
import com.stayease.reservation.repository.RoomTypeInventoryRepository;
import com.stayease.reservation.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReservationControllerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private RoomTypeInventoryRepository inventoryRepository;

    @Autowired
    private ReservationAuditEventRepository auditEventRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PropertyServiceClient propertyClient;

    @MockBean
    private UserServiceClient userServiceClient;

    private String adminToken;
    private String userToken;
    private PropertyHotelDto hotelDto;
    private PropertyRoomTypeDto roomTypeDto;
    private PropertyPricingDto pricingDto;

    @BeforeEach
    void setUp() {
        when(userServiceClient.getTokenVersion(anyLong())).thenReturn(Optional.of(1L));

        paymentTransactionRepository.deleteAll();
        reservationRepository.deleteAll();
        inventoryRepository.deleteAll();
        auditEventRepository.deleteAll();

        adminToken = "Bearer " + jwtTokenProvider.generateToken(1L, "admin@stayease.local", "ROLE_ADMIN", 1L);
        userToken = "Bearer " + jwtTokenProvider.generateToken(101L, "guest@stayease.local", "ROLE_USER", 1L);

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
                .address("Beach Road 10")
                .contactPhone("9876543210")
                .roomTypes(List.of(roomTypeDto))
                .build();

        pricingDto = PropertyPricingDto.builder()
                .roomTypeId(10L)
                .baseNightlyRate(new BigDecimal("4500.00"))
                .nightlyRates(List.of(
                        PropertyNightlyRateDto.builder().date(LocalDate.now().plusDays(5)).effectiveRate(new BigDecimal("4500.00")).rateType("BASE").build(),
                        PropertyNightlyRateDto.builder().date(LocalDate.now().plusDays(6)).effectiveRate(new BigDecimal("5200.00")).rateType("WEEKEND").build()
                ))
                .build();

        when(propertyClient.getHotelDetails(anyLong())).thenReturn(hotelDto);
        when(propertyClient.getSellableCapacity(anyLong())).thenReturn(PropertyCapacityDto.builder().sellableCapacity(15).build());
        when(propertyClient.getPricing(anyLong(), any(), any())).thenReturn(pricingDto);
    }

    // ── Public Availability Endpoints ──────────────────────────────────────────

    @Test
    @DisplayName("GET /api/reservations/availability is publicly accessible without token")
    void testGetAvailabilityPublic() throws Exception {
        LocalDate checkIn = LocalDate.now().plusDays(5);
        LocalDate checkOut = LocalDate.now().plusDays(7);

        mockMvc.perform(get("/api/reservations/availability")
                        .param("hotelId", "1")
                        .param("checkIn", checkIn.toString())
                        .param("checkOut", checkOut.toString())
                        .param("guests", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hotelId").value(1))
                .andExpect(jsonPath("$.nights").value(2))
                .andExpect(jsonPath("$.roomTypes").isArray());
    }

    @Test
    @DisplayName("GET /api/reservations/room-types/{id}/availability is publicly accessible without token")
    void testGetRoomTypeAvailabilityPublic() throws Exception {
        LocalDate checkIn = LocalDate.now().plusDays(5);
        LocalDate checkOut = LocalDate.now().plusDays(7);

        mockMvc.perform(get("/api/reservations/room-types/10/availability")
                        .param("checkIn", checkIn.toString())
                        .param("checkOut", checkOut.toString())
                        .param("guests", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomTypeId").value(10))
                .andExpect(jsonPath("$.lodgingSubtotal").value(9700.00))
                .andExpect(jsonPath("$.totalAmount").value(10864.00));
    }

    // ── Security & Authentication Enforcement ──────────────────────────────────

    @Test
    @DisplayName("POST /api/reservations without token returns 401 Unauthorized")
    void testCreateReservationWithoutToken() throws Exception {
        CreateReservationRequest req = CreateReservationRequest.builder()
                .hotelId(1L)
                .roomTypeId(10L)
                .checkInDate(LocalDate.now().plusDays(5))
                .checkOutDate(LocalDate.now().plusDays(7))
                .guestCount(2)
                .paymentPolicy(PaymentPolicy.PREPAID)
                .build();

        mockMvc.perform(post("/api/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/reservations/my without token returns 401 Unauthorized")
    void testGetMyReservationsWithoutToken() throws Exception {
        mockMvc.perform(get("/api/reservations/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/admin/dashboard with ROLE_USER returns 403 Forbidden")
    void testAdminDashboardForbiddenForUser() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("GET /api/admin/dashboard with ROLE_ADMIN returns 200 OK")
    void testAdminDashboardAllowedForAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationCountsByStatus").isMap());
    }

    @Test
    @DisplayName("GET /api/admin/audit with ROLE_ADMIN returns 200 OK with PageResponse envelope")
    void testAdminAuditAllowedForAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/audit")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.pageNumber").value(0));
    }

    // ── Reservation Creation & Validation ──────────────────────────────────────

    @Test
    @DisplayName("POST /api/reservations with valid token creates PREPAID reservation (201 Created)")
    void testCreateReservationSuccess() throws Exception {
        CreateReservationRequest req = CreateReservationRequest.builder()
                .hotelId(1L)
                .roomTypeId(10L)
                .checkInDate(LocalDate.now().plusDays(5))
                .checkOutDate(LocalDate.now().plusDays(7))
                .guestCount(2)
                .paymentPolicy(PaymentPolicy.PREPAID)
                .build();

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.publicReference").isNotEmpty())
                .andExpect(jsonPath("$.reservationStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.paymentPolicy").value("PREPAID"))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.nights").value(2))
                .andExpect(jsonPath("$.holdExpiresAt").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/reservations with invalid payload returns 422 Unprocessable Entity")
    void testCreateReservationValidationFailure() throws Exception {
        CreateReservationRequest req = CreateReservationRequest.builder()
                .hotelId(null) // Missing hotelId
                .roomTypeId(10L)
                .guestCount(0) // Invalid guest count
                .build();

        mockMvc.perform(post("/api/reservations")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    // ── Internal Endpoints ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /internal/reservations/{id}/review-eligibility is accessible internally")
    void testReviewEligibilityInternalEndpoint() throws Exception {
        Reservation res = Reservation.builder()
                .publicReference("SE-2026-TEST")
                .userId(101L)
                .hotelId(1L)
                .roomTypeId(10L)
                .checkInDate(LocalDate.now().minusDays(5))
                .checkOutDate(LocalDate.now().minusDays(3))
                .nights(2)
                .guestCount(2)
                .reservationStatus(ReservationStatus.CHECKED_OUT)
                .paymentPolicy(PaymentPolicy.PREPAID)
                .paymentStatus(PaymentStatus.PAID)
                .lodgingSubtotal(new BigDecimal("9700.00"))
                .taxPercentage(new BigDecimal("12.00"))
                .taxAmount(new BigDecimal("1164.00"))
                .totalAmount(new BigDecimal("10864.00"))
                .priceSnapshotJson("{\"totalAmount\":10864.00}")
                .checkedOutAt(Instant.now().minusSeconds(86400))
                .build();
        res = reservationRepository.save(res);

        mockMvc.perform(get("/internal/reservations/" + res.getId() + "/review-eligibility")
                        .param("userId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.reservationId").value(res.getId()))
                .andExpect(jsonPath("$.hotelId").value(1));
    }
}
