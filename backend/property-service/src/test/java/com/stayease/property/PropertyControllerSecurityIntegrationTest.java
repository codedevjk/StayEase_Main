package com.stayease.property;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stayease.property.enums.HousekeepingStatus;
import com.stayease.property.enums.OperationalStatus;
import com.stayease.property.client.UserServiceClient;
import com.stayease.property.dto.*;
import com.stayease.property.entity.Amenity;
import com.stayease.property.entity.Hotel;
import com.stayease.property.entity.PhysicalRoom;
import com.stayease.property.entity.RoomType;
import com.stayease.property.repository.AmenityRepository;
import com.stayease.property.repository.HotelRepository;
import com.stayease.property.repository.PhysicalRoomRepository;
import com.stayease.property.repository.RoomTypeRepository;
import com.stayease.property.security.JwtTokenProvider;
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
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive integration and security test suite for StayEase Property Service.
 * Runs against in-memory H2 database under active "test" profile.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PropertyControllerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private AmenityRepository amenityRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private PhysicalRoomRepository physicalRoomRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserServiceClient userServiceClient;

    private String adminToken;
    private String userToken;
    private Hotel testHotel;
    private RoomType testRoomType;
    private Amenity wifiAmenity;

    @BeforeEach
    void setUp() {
        // Mock UserServiceClient to return tokenVersion = 1 for any user by default
        when(userServiceClient.getTokenVersion(anyLong())).thenReturn(Optional.of(1L));

        physicalRoomRepository.deleteAll();
        roomTypeRepository.deleteAll();
        hotelRepository.deleteAll();
        amenityRepository.deleteAll();

        // Generate tokens
        adminToken = "Bearer " + jwtTokenProvider.generateToken(1L, "admin@stayease.local", "ROLE_ADMIN", 1L);
        userToken = "Bearer " + jwtTokenProvider.generateToken(101L, "guest@stayease.local", "ROLE_USER", 1L);

        // Seed basic test data
        wifiAmenity = amenityRepository.save(
                Amenity.builder().name("Free Wi-Fi").icon("bi-wifi").build()
        );

        testHotel = Hotel.builder()
                .name("Goa Beach Resort")
                .destination("Goa")
                .address("Beach Road 10")
                .contactEmail("resort@stayease.local")
                .contactPhone("9876543210")
                .description("Beach resort")
                .starRating(4)
                .publishedRating(new BigDecimal("4.50"))
                .reviewCount(50)
                .amenities(new HashSet<>(Set.of(wifiAmenity)))
                .build();
        testHotel = hotelRepository.save(testHotel);

        testRoomType = RoomType.builder()
                .hotel(testHotel)
                .name("Standard Suite")
                .description("Comfortable double suite")
                .maxGuests(2)
                .baseNightlyRate(new BigDecimal("3000.00"))
                .totalPhysicalRooms(2)
                .build();
        testRoomType = roomTypeRepository.save(testRoomType);

        physicalRoomRepository.save(PhysicalRoom.builder()
                .hotel(testHotel)
                .roomType(testRoomType)
                .roomNumber("101")
                .housekeepingStatus(HousekeepingStatus.CLEAN)
                .operationalStatus(OperationalStatus.OPERATIONAL)
                .build());

        physicalRoomRepository.save(PhysicalRoom.builder()
                .hotel(testHotel)
                .roomType(testRoomType)
                .roomNumber("102")
                .housekeepingStatus(HousekeepingStatus.DIRTY)
                .operationalStatus(OperationalStatus.OPERATIONAL)
                .build());
    }

    // ── Public Catalog Endpoints ───────────────────────────────────────────────

    @Test
    @DisplayName("Public search hotels returns 200 with PageResponse and Correlation ID")
    void shouldSearchHotelsPublicly() throws Exception {
        mockMvc.perform(get("/api/hotels")
                        .param("destination", "Goa")
                        .header("X-Correlation-ID", "test-corr-1234"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "test-corr-1234"))
                .andExpect(jsonPath("$.content[0].name").value("Goa Beach Resort"))
                .andExpect(jsonPath("$.content[0].destination").value("Goa"))
                .andExpect(jsonPath("$.content[0].startingNightlyRate").value(3000.00))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.pageNumber").value(0));
    }

    @Test
    @DisplayName("Public hotel details returns 200 with complete hotel data")
    void shouldGetHotelDetailsPublicly() throws Exception {
        mockMvc.perform(get("/api/hotels/{hotelId}", testHotel.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testHotel.getId()))
                .andExpect(jsonPath("$.name").value("Goa Beach Resort"))
                .andExpect(jsonPath("$.roomTypes[0].name").value("Standard Suite"))
                .andExpect(jsonPath("$.amenities[0].name").value("Free Wi-Fi"));
    }

    @Test
    @DisplayName("Public hotel details returns 404 when hotel ID does not exist")
    void shouldReturn404WhenHotelNotFound() throws Exception {
        mockMvc.perform(get("/api/hotels/{hotelId}", 9999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // ── Role-Based Access to Administrative Endpoints ──────────────────────────

    @Test
    @DisplayName("Admin hotel creation returns 401 Unauthorized for unauthenticated caller")
    void shouldRejectUnauthenticatedAdminEndpoint() throws Exception {
        CreateHotelRequest request = CreateHotelRequest.builder()
                .name("New Palace")
                .destination("Mumbai")
                .address("Marine Drive")
                .contactEmail("palace@stayease.local")
                .contactPhone("9876543210")
                .build();

        mockMvc.perform(post("/api/admin/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Admin hotel creation returns 403 Forbidden for non-admin caller (ROLE_USER)")
    void shouldRejectUserFromAdminEndpoint() throws Exception {
        CreateHotelRequest request = CreateHotelRequest.builder()
                .name("New Palace")
                .destination("Mumbai")
                .address("Marine Drive")
                .contactEmail("palace@stayease.local")
                .contactPhone("9876543210")
                .build();

        mockMvc.perform(post("/api/admin/hotels")
                        .header("Authorization", userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("Admin hotel creation succeeds with 201 Created for authenticated ROLE_ADMIN")
    void shouldAllowAdminToCreateHotel() throws Exception {
        CreateHotelRequest request = CreateHotelRequest.builder()
                .name("Jaipur Heritage")
                .destination("Jaipur")
                .address("Amber Road")
                .contactEmail("heritage@stayease.local")
                .contactPhone("9876543210")
                .starRating(5)
                .build();

        mockMvc.perform(post("/api/admin/hotels")
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Jaipur Heritage"))
                .andExpect(jsonPath("$.destination").value("Jaipur"));
    }

    @Test
    @DisplayName("Admin room registration creates a physical room and returns 201 Created")
    void shouldAllowAdminToRegisterPhysicalRoom() throws Exception {
        RegisterPhysicalRoomRequest request = RegisterPhysicalRoomRequest.builder()
                .roomTypeId(testRoomType.getId())
                .roomNumber("103")
                .housekeepingStatus(HousekeepingStatus.CLEAN)
                .operationalStatus(OperationalStatus.OPERATIONAL)
                .build();

        mockMvc.perform(post("/api/admin/hotels/{hotelId}/rooms", testHotel.getId())
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomNumber").value("103"))
                .andExpect(jsonPath("$.housekeepingStatus").value("CLEAN"));
    }

    @Test
    @DisplayName("Admin room registration returns 409 Conflict on duplicate room number in hotel")
    void shouldReturn409OnDuplicateRoomNumber() throws Exception {
        RegisterPhysicalRoomRequest request = RegisterPhysicalRoomRequest.builder()
                .roomTypeId(testRoomType.getId())
                .roomNumber("101") // already exists
                .build();

        mockMvc.perform(post("/api/admin/hotels/{hotelId}/rooms", testHotel.getId())
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ROOM_NUMBER"));
    }

    @Test
    @DisplayName("Admin housekeeping update changes room housekeeping status")
    void shouldUpdateHousekeepingStatus() throws Exception {
        PhysicalRoom room = physicalRoomRepository.findByHotelIdAndRoomNumber(testHotel.getId(), "102").orElseThrow();

        UpdateHousekeepingRequest request = UpdateHousekeepingRequest.builder()
                .housekeepingStatus(HousekeepingStatus.CLEAN)
                .build();

        mockMvc.perform(patch("/api/admin/rooms/{roomId}/housekeeping", room.getId())
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.housekeepingStatus").value("CLEAN"));
    }

    @Test
    @DisplayName("Admin operational update changes room operational status")
    void shouldUpdateOperationalStatus() throws Exception {
        PhysicalRoom room = physicalRoomRepository.findByHotelIdAndRoomNumber(testHotel.getId(), "102").orElseThrow();

        UpdateOperationalRequest request = UpdateOperationalRequest.builder()
                .operationalStatus(OperationalStatus.OUT_OF_SERVICE)
                .build();

        mockMvc.perform(patch("/api/admin/rooms/{roomId}/operational", room.getId())
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationalStatus").value("OUT_OF_SERVICE"));
    }

    // ── Internal Service-to-Service Endpoints ──────────────────────────────────

    @Test
    @DisplayName("Internal capacity endpoint returns sellable capacity reflecting operational rooms")
    void shouldReturnSellableCapacity() throws Exception {
        // Room 101 is CLEAN & OPERATIONAL
        // Room 102 is DIRTY & OPERATIONAL
        // Both are OPERATIONAL -> sellable capacity = 2
        mockMvc.perform(get("/internal/properties/room-types/{roomTypeId}/capacity", testRoomType.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomTypeId").value(testRoomType.getId()))
                .andExpect(jsonPath("$.sellableCapacity").value(2))
                .andExpect(jsonPath("$.totalPhysicalRooms").value(2))
                .andExpect(jsonPath("$.outOfServiceRooms").value(0));

        // Now set Room 102 to OUT_OF_SERVICE
        PhysicalRoom room102 = physicalRoomRepository.findByHotelIdAndRoomNumber(testHotel.getId(), "102").orElseThrow();
        room102.setOperationalStatus(OperationalStatus.OUT_OF_SERVICE);
        physicalRoomRepository.save(room102);

        // Capacity should now be 2 - 1 = 1
        mockMvc.perform(get("/internal/properties/room-types/{roomTypeId}/capacity", testRoomType.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellableCapacity").value(1))
                .andExpect(jsonPath("$.outOfServiceRooms").value(1));
    }

    @Test
    @DisplayName("Internal pricing endpoint calculates nightly rates for date interval")
    void shouldReturnCalculatedPricing() throws Exception {
        mockMvc.perform(get("/internal/properties/room-types/{roomTypeId}/pricing", testRoomType.getId())
                        .param("checkIn", "2026-11-01")
                        .param("checkOut", "2026-11-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomTypeId").value(testRoomType.getId()))
                .andExpect(jsonPath("$.nightlyRates[0].date").value("2026-11-01"))
                .andExpect(jsonPath("$.nightlyRates[0].effectiveRate").value(3000.00))
                .andExpect(jsonPath("$.nightlyRates[1].date").value("2026-11-02"))
                .andExpect(jsonPath("$.nightlyRates[1].effectiveRate").value(3000.00));
    }

    @Test
    @DisplayName("Internal check-in room assignment returns ready clean room")
    void shouldAssignCleanRoomAtCheckIn() throws Exception {
        AssignCleanRoomRequest request = AssignCleanRoomRequest.builder()
                .hotelId(testHotel.getId())
                .roomTypeId(testRoomType.getId())
                .reservationId(5001L)
                .build();

        mockMvc.perform(post("/internal/properties/rooms/assign-clean")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomNumber").value("101"));
    }

    @Test
    @DisplayName("Internal check-in assignment returns 409 Conflict when no clean room ready")
    void shouldFailCheckInAssignmentWhenNoCleanRoom() throws Exception {
        // Mark room 101 as DIRTY too
        PhysicalRoom room101 = physicalRoomRepository.findByHotelIdAndRoomNumber(testHotel.getId(), "101").orElseThrow();
        room101.setHousekeepingStatus(HousekeepingStatus.DIRTY);
        physicalRoomRepository.save(room101);

        AssignCleanRoomRequest request = AssignCleanRoomRequest.builder()
                .hotelId(testHotel.getId())
                .roomTypeId(testRoomType.getId())
                .reservationId(5002L)
                .build();

        mockMvc.perform(post("/internal/properties/rooms/assign-clean")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_CLEAN_ROOM_AVAILABLE"))
                .andExpect(jsonPath("$.message").value("No clean room ready for check-in; front desk alerted"));
    }

    @Test
    @DisplayName("Internal checkout marks physical room DIRTY")
    void shouldMarkRoomDirtyOnCheckout() throws Exception {
        PhysicalRoom room101 = physicalRoomRepository.findByHotelIdAndRoomNumber(testHotel.getId(), "101").orElseThrow();
        assertThat(room101.getHousekeepingStatus()).isEqualTo(HousekeepingStatus.CLEAN);

        mockMvc.perform(post("/internal/properties/rooms/{roomId}/mark-dirty", room101.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.housekeepingStatus").value("DIRTY"));

        PhysicalRoom reloaded = physicalRoomRepository.findById(room101.getId()).orElseThrow();
        assertThat(reloaded.getHousekeepingStatus()).isEqualTo(HousekeepingStatus.DIRTY);
    }
}
