package com.stayease.property.service;

import com.stayease.property.enums.OperationalStatus;
import com.stayease.property.dto.CreateRoomTypeRequest;
import com.stayease.property.dto.RoomTypeCapacityResponse;
import com.stayease.property.dto.RoomTypeDetailDto;
import com.stayease.property.dto.UpdateRoomTypeRequest;
import com.stayease.property.entity.Hotel;
import com.stayease.property.entity.RoomType;
import com.stayease.property.exception.ResourceNotFoundException;
import com.stayease.property.repository.HotelRepository;
import com.stayease.property.repository.PhysicalRoomRepository;
import com.stayease.property.repository.RoomTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomTypeServiceTest {

    @Mock
    private RoomTypeRepository roomTypeRepository;

    @Mock
    private HotelRepository hotelRepository;

    @Mock
    private PhysicalRoomRepository physicalRoomRepository;

    @Mock
    private PropertyAuditService propertyAuditService;

    @InjectMocks
    private RoomTypeService roomTypeService;

    private Hotel sampleHotel;
    private RoomType sampleRoomType;

    @BeforeEach
    void setUp() {
        sampleHotel = Hotel.builder()
                .id(1L)
                .name("Grand Palace")
                .destination("Goa")
                .build();

        sampleRoomType = RoomType.builder()
                .id(10L)
                .hotel(sampleHotel)
                .name("Deluxe Ocean View")
                .baseNightlyRate(new BigDecimal("4500.00"))
                .maxGuests(2)
                .totalPhysicalRooms(10)
                .build();
    }

    @Test
    @DisplayName("PRP-04 & PRP-05: Sellable capacity decrements strictly by OUT_OF_SERVICE count; DIRTY/INSPECTING do not affect future capacity")
    void shouldCalculateSellableCapacityExcludingOnlyOutOfServiceRooms() {
        // Room type has 10 physical rooms in total:
        // 5 CLEAN & OPERATIONAL
        // 3 DIRTY & OPERATIONAL (should not reduce capacity)
        // 2 OUT_OF_SERVICE (must reduce capacity by 2)
        // Expected sellableCapacity = 10 - 2 = 8

        when(roomTypeRepository.findById(10L)).thenReturn(Optional.of(sampleRoomType));
        when(physicalRoomRepository.countByRoomTypeId(10L)).thenReturn(10L);
        when(physicalRoomRepository.countByRoomTypeIdAndOperationalStatus(10L, OperationalStatus.OUT_OF_SERVICE))
                .thenReturn(2L);

        RoomTypeCapacityResponse response = roomTypeService.calculateCapacity(10L);

        assertThat(response.getRoomTypeId()).isEqualTo(10L);
        assertThat(response.getTotalPhysicalRooms()).isEqualTo(10);
        assertThat(response.getOutOfServiceRooms()).isEqualTo(2);
        assertThat(response.getSellableCapacity()).isEqualTo(8);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when calculating capacity for non-existent room type")
    void shouldThrowNotFoundForMissingRoomType() {
        when(roomTypeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomTypeService.calculateCapacity(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Room type not found with ID: 999");
    }

    @Test
    @DisplayName("Should create room type with formatted base rate")
    void shouldCreateRoomType() {
        CreateRoomTypeRequest request = CreateRoomTypeRequest.builder()
                .name("Presidential Suite")
                .description("Top floor luxury")
                .maxGuests(4)
                .baseNightlyRate(new BigDecimal("12000.555"))
                .build();

        when(hotelRepository.findById(1L)).thenReturn(Optional.of(sampleHotel));
        when(roomTypeRepository.save(any(RoomType.class))).thenAnswer(inv -> {
            RoomType rt = inv.getArgument(0);
            rt.setId(20L);
            return rt;
        });
        when(physicalRoomRepository.countByRoomTypeId(20L)).thenReturn(0L);

        RoomTypeDetailDto dto = roomTypeService.createRoomType(1L, request);

        assertThat(dto.getId()).isEqualTo(20L);
        assertThat(dto.getName()).isEqualTo("Presidential Suite");
        assertThat(dto.getBaseNightlyRate()).isEqualByComparingTo("12000.56");
        assertThat(dto.getTotalRooms()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should update room type details")
    void shouldUpdateRoomType() {
        UpdateRoomTypeRequest request = UpdateRoomTypeRequest.builder()
                .name("Updated Deluxe Ocean View")
                .description("Updated view")
                .maxGuests(3)
                .baseNightlyRate(new BigDecimal("4800.00"))
                .build();

        when(roomTypeRepository.findById(10L)).thenReturn(Optional.of(sampleRoomType));
        when(roomTypeRepository.save(any(RoomType.class))).thenAnswer(inv -> inv.getArgument(0));
        when(physicalRoomRepository.countByRoomTypeId(10L)).thenReturn(10L);

        RoomTypeDetailDto dto = roomTypeService.updateRoomType(10L, request);

        assertThat(dto.getName()).isEqualTo("Updated Deluxe Ocean View");
        assertThat(dto.getMaxGuests()).isEqualTo(3);
        assertThat(dto.getBaseNightlyRate()).isEqualByComparingTo("4800.00");
    }
}
