package com.stayease.property.service;

import com.stayease.property.dto.PageResponse;
import com.stayease.property.dto.CreateHotelRequest;
import com.stayease.property.dto.HotelDetailDto;
import com.stayease.property.dto.HotelSummaryDto;
import com.stayease.property.dto.UpdateHotelRequest;
import com.stayease.property.entity.Amenity;
import com.stayease.property.entity.Hotel;
import com.stayease.property.entity.RoomType;
import com.stayease.property.exception.ResourceNotFoundException;
import com.stayease.property.repository.AmenityRepository;
import com.stayease.property.repository.HotelRepository;
import com.stayease.property.repository.PhysicalRoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotelServiceTest {

    @Mock
    private HotelRepository hotelRepository;

    @Mock
    private AmenityRepository amenityRepository;

    @Mock
    private PhysicalRoomRepository physicalRoomRepository;

    @Mock
    private PropertyAuditService propertyAuditService;

    @InjectMocks
    private HotelService hotelService;

    private Hotel sampleHotel;
    private Amenity wifiAmenity;
    private Amenity poolAmenity;
    private RoomType roomType;

    @BeforeEach
    void setUp() {
        wifiAmenity = Amenity.builder().id(1L).name("Free Wi-Fi").icon("bi-wifi").build();
        poolAmenity = Amenity.builder().id(2L).name("Swimming Pool").icon("bi-water").build();

        sampleHotel = Hotel.builder()
                .id(1L)
                .name("StayEase Grand Palace")
                .destination("Goa")
                .address("100 Beach Road, Calangute, Goa")
                .contactEmail("palace.goa@stayease.com")
                .contactPhone("0832223344")
                .description("Luxury beachfront resort")
                .starRating(5)
                .publishedRating(new BigDecimal("4.80"))
                .reviewCount(124)
                .imageUrl("/assets/hotels/hotel1.jpg")
                .amenities(new HashSet<>(Set.of(wifiAmenity, poolAmenity)))
                .roomTypes(new ArrayList<>())
                .build();

        roomType = RoomType.builder()
                .id(10L)
                .hotel(sampleHotel)
                .name("Deluxe Ocean View")
                .description("King bed with ocean balcony")
                .maxGuests(2)
                .baseNightlyRate(new BigDecimal("3500.00"))
                .totalPhysicalRooms(20)
                .build();

        sampleHotel.getRoomTypes().add(roomType);
    }

    @Test
    @DisplayName("PRP-03: Search hotels returns paginated HotelSummaryDto with applied criteria")
    void shouldSearchHotelsWithPaginationAndFilters() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("id").ascending());
        Page<Hotel> hotelPage = new PageImpl<>(List.of(sampleHotel), pageable, 1);

        when(hotelRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(hotelPage);

        PageResponse<HotelSummaryDto> response = hotelService.searchHotels(
                "Goa", "Grand", new BigDecimal("3000.00"), new BigDecimal("5000.00"),
                List.of(1L, 2L), new BigDecimal("4.00"), pageable
        );

        assertThat(response.content()).hasSize(1);
        HotelSummaryDto summary = response.content().get(0);
        assertThat(summary.getId()).isEqualTo(1L);
        assertThat(summary.getName()).isEqualTo("StayEase Grand Palace");
        assertThat(summary.getDestination()).isEqualTo("Goa");
        assertThat(summary.getStartingNightlyRate()).isEqualByComparingTo("3500.00");
        assertThat(summary.getAmenities()).contains("Free Wi-Fi", "Swimming Pool");
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should fetch hotel details by ID with mapped amenities and room types")
    void shouldGetHotelDetailsById() {
        when(hotelRepository.findDetailedById(1L)).thenReturn(Optional.of(sampleHotel));
        when(physicalRoomRepository.countByRoomTypeId(10L)).thenReturn(20L);

        HotelDetailDto detail = hotelService.getHotelDetails(1L);

        assertThat(detail.getId()).isEqualTo(1L);
        assertThat(detail.getName()).isEqualTo("StayEase Grand Palace");
        assertThat(detail.getAmenities()).hasSize(2);
        assertThat(detail.getRoomTypes()).hasSize(1);
        assertThat(detail.getRoomTypes().get(0).getName()).isEqualTo("Deluxe Ocean View");
        assertThat(detail.getRoomTypes().get(0).getTotalRooms()).isEqualTo(20);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when hotel ID does not exist")
    void shouldThrowNotFoundForMissingHotel() {
        when(hotelRepository.findDetailedById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hotelService.getHotelDetails(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Hotel not found with ID: 999");
    }

    @Test
    @DisplayName("Should create hotel and attach requested amenities")
    void shouldCreateHotelSuccessfully() {
        CreateHotelRequest request = CreateHotelRequest.builder()
                .name("Mountain Retreat")
                .destination("Manali")
                .address("Mall Road")
                .contactEmail("retreat@stayease.com")
                .contactPhone("9876543210")
                .description("Scenic mountain view")
                .starRating(4)
                .amenityIds(Set.of(1L))
                .build();

        when(amenityRepository.findAllById(Set.of(1L))).thenReturn(List.of(wifiAmenity));
        when(hotelRepository.save(any(Hotel.class))).thenAnswer(inv -> {
            Hotel h = inv.getArgument(0);
            h.setId(5L);
            h.setRoomTypes(new ArrayList<>());
            return h;
        });
        when(hotelRepository.findDetailedById(5L)).thenAnswer(inv -> {
            Hotel h = Hotel.builder()
                    .id(5L)
                    .name("Mountain Retreat")
                    .destination("Manali")
                    .address("Mall Road")
                    .contactEmail("retreat@stayease.com")
                    .contactPhone("9876543210")
                    .starRating(4)
                    .publishedRating(BigDecimal.ZERO)
                    .reviewCount(0)
                    .amenities(Set.of(wifiAmenity))
                    .roomTypes(new ArrayList<>())
                    .build();
            return Optional.of(h);
        });

        HotelDetailDto created = hotelService.createHotel(request);

        assertThat(created.getId()).isEqualTo(5L);
        assertThat(created.getName()).isEqualTo("Mountain Retreat");
        assertThat(created.getDestination()).isEqualTo("Manali");
        verify(hotelRepository).save(any(Hotel.class));
    }

    @Test
    @DisplayName("Should update hotel details successfully")
    void shouldUpdateHotelSuccessfully() {
        UpdateHotelRequest request = UpdateHotelRequest.builder()
                .name("Updated Grand Palace")
                .destination("North Goa")
                .address("Calangute Beach")
                .contactEmail("updated.goa@stayease.com")
                .contactPhone("0832223399")
                .starRating(5)
                .build();

        when(hotelRepository.findById(1L)).thenReturn(Optional.of(sampleHotel));
        when(hotelRepository.save(any(Hotel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(hotelRepository.findDetailedById(1L)).thenReturn(Optional.of(sampleHotel));

        HotelDetailDto updated = hotelService.updateHotel(1L, request);

        assertThat(updated.getName()).isEqualTo("Updated Grand Palace");
        assertThat(updated.getDestination()).isEqualTo("North Goa");
        verify(hotelRepository).save(sampleHotel);
    }
}
