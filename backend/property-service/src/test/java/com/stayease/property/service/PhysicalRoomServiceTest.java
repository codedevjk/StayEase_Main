package com.stayease.property.service;

import com.stayease.property.enums.HousekeepingStatus;
import com.stayease.property.enums.OperationalStatus;
import com.stayease.property.dto.AssignCleanRoomRequest;
import com.stayease.property.dto.AssignCleanRoomResponse;
import com.stayease.property.dto.PhysicalRoomDto;
import com.stayease.property.dto.RegisterPhysicalRoomRequest;
import com.stayease.property.entity.Hotel;
import com.stayease.property.entity.PhysicalRoom;
import com.stayease.property.entity.RoomType;
import com.stayease.property.exception.DuplicateRoomNumberException;
import com.stayease.property.exception.NoCleanRoomAvailableException;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PhysicalRoomServiceTest {

    @Mock
    private PhysicalRoomRepository physicalRoomRepository;

    @Mock
    private HotelRepository hotelRepository;

    @Mock
    private RoomTypeRepository roomTypeRepository;

    @Mock
    private PropertyAuditService propertyAuditService;

    @InjectMocks
    private PhysicalRoomService physicalRoomService;

    private Hotel sampleHotel;
    private RoomType sampleRoomType;
    private PhysicalRoom sampleRoom;

    @BeforeEach
    void setUp() {
        sampleHotel = Hotel.builder()
                .id(1L)
                .name("StayEase Resort")
                .destination("Goa")
                .build();

        sampleRoomType = RoomType.builder()
                .id(10L)
                .hotel(sampleHotel)
                .name("Deluxe Suite")
                .baseNightlyRate(new BigDecimal("5000.00"))
                .totalPhysicalRooms(1)
                .build();

        sampleRoom = PhysicalRoom.builder()
                .id(101L)
                .hotel(sampleHotel)
                .roomType(sampleRoomType)
                .roomNumber("101-A")
                .housekeepingStatus(HousekeepingStatus.CLEAN)
                .operationalStatus(OperationalStatus.OPERATIONAL)
                .build();
    }

    @Test
    @DisplayName("Should successfully register a physical room and synchronize totalPhysicalRooms count")
    void shouldRegisterPhysicalRoomSuccessfully() {
        RegisterPhysicalRoomRequest request = RegisterPhysicalRoomRequest.builder()
                .roomTypeId(10L)
                .roomNumber("102-B")
                .housekeepingStatus(HousekeepingStatus.CLEAN)
                .operationalStatus(OperationalStatus.OPERATIONAL)
                .build();

        when(hotelRepository.findById(1L)).thenReturn(Optional.of(sampleHotel));
        when(roomTypeRepository.findById(10L)).thenReturn(Optional.of(sampleRoomType));
        when(physicalRoomRepository.findByHotelIdAndRoomNumber(1L, "102-B")).thenReturn(Optional.empty());
        when(physicalRoomRepository.save(any(PhysicalRoom.class))).thenAnswer(inv -> {
            PhysicalRoom pr = inv.getArgument(0);
            pr.setId(102L);
            return pr;
        });
        when(physicalRoomRepository.countByRoomTypeId(10L)).thenReturn(2L);

        PhysicalRoomDto result = physicalRoomService.registerPhysicalRoom(1L, request);

        assertThat(result.getId()).isEqualTo(102L);
        assertThat(result.getRoomNumber()).isEqualTo("102-B");
        assertThat(result.getHousekeepingStatus()).isEqualTo(HousekeepingStatus.CLEAN);
        assertThat(result.getOperationalStatus()).isEqualTo(OperationalStatus.OPERATIONAL);

        assertThat(sampleRoomType.getTotalPhysicalRooms()).isEqualTo(2);
        verify(roomTypeRepository).save(sampleRoomType);
    }

    @Test
    @DisplayName("Should reject room registration when roomNumber already exists in the same hotel")
    void shouldRejectDuplicateRoomNumberInHotel() {
        RegisterPhysicalRoomRequest request = RegisterPhysicalRoomRequest.builder()
                .roomTypeId(10L)
                .roomNumber("101-A")
                .build();

        when(hotelRepository.findById(1L)).thenReturn(Optional.of(sampleHotel));
        when(roomTypeRepository.findById(10L)).thenReturn(Optional.of(sampleRoomType));
        when(physicalRoomRepository.findByHotelIdAndRoomNumber(1L, "101-A")).thenReturn(Optional.of(sampleRoom));

        assertThatThrownBy(() -> physicalRoomService.registerPhysicalRoom(1L, request))
                .isInstanceOf(DuplicateRoomNumberException.class)
                .hasMessageContaining("already exists in hotel ID 1");
    }

    @Test
    @DisplayName("PRP-05: Housekeeping status updates transition between CLEAN, DIRTY, and INSPECTING")
    void shouldUpdateHousekeepingStatus() {
        when(physicalRoomRepository.findById(101L)).thenReturn(Optional.of(sampleRoom));
        when(physicalRoomRepository.save(any(PhysicalRoom.class))).thenAnswer(inv -> inv.getArgument(0));

        PhysicalRoomDto dirtyDto = physicalRoomService.updateHousekeepingStatus(101L, HousekeepingStatus.DIRTY);
        assertThat(dirtyDto.getHousekeepingStatus()).isEqualTo(HousekeepingStatus.DIRTY);

        PhysicalRoomDto inspectingDto = physicalRoomService.updateHousekeepingStatus(101L, HousekeepingStatus.INSPECTING);
        assertThat(inspectingDto.getHousekeepingStatus()).isEqualTo(HousekeepingStatus.INSPECTING);

        PhysicalRoomDto cleanDto = physicalRoomService.updateHousekeepingStatus(101L, HousekeepingStatus.CLEAN);
        assertThat(cleanDto.getHousekeepingStatus()).isEqualTo(HousekeepingStatus.CLEAN);
    }

    @Test
    @DisplayName("PRP-04: Operational status updates transition between OPERATIONAL and OUT_OF_SERVICE")
    void shouldUpdateOperationalStatus() {
        when(physicalRoomRepository.findById(101L)).thenReturn(Optional.of(sampleRoom));
        when(physicalRoomRepository.save(any(PhysicalRoom.class))).thenAnswer(inv -> inv.getArgument(0));

        PhysicalRoomDto oosDto = physicalRoomService.updateOperationalStatus(101L, OperationalStatus.OUT_OF_SERVICE);
        assertThat(oosDto.getOperationalStatus()).isEqualTo(OperationalStatus.OUT_OF_SERVICE);

        PhysicalRoomDto opDto = physicalRoomService.updateOperationalStatus(101L, OperationalStatus.OPERATIONAL);
        assertThat(opDto.getOperationalStatus()).isEqualTo(OperationalStatus.OPERATIONAL);
    }

    @Test
    @DisplayName("OPS-03: Assign clean room assigns an eligible room that is CLEAN and OPERATIONAL")
    void shouldAssignCleanRoomWhenReady() {
        AssignCleanRoomRequest request = AssignCleanRoomRequest.builder()
                .hotelId(1L)
                .roomTypeId(10L)
                .reservationId(5001L)
                .build();

        when(physicalRoomRepository.findFirstByHotelIdAndRoomTypeIdAndHousekeepingStatusAndOperationalStatusOrderByIdAsc(
                1L, 10L, HousekeepingStatus.CLEAN, OperationalStatus.OPERATIONAL
        )).thenReturn(Optional.of(sampleRoom));

        AssignCleanRoomResponse response = physicalRoomService.assignCleanRoom(request);

        assertThat(response.getPhysicalRoomId()).isEqualTo(101L);
        assertThat(response.getRoomNumber()).isEqualTo("101-A");
    }

    @Test
    @DisplayName("OPS-02: Check-in fails with HTTP 409 Conflict when no clean room ready")
    void shouldThrowConflictWhenNoCleanRoomReady() {
        AssignCleanRoomRequest request = AssignCleanRoomRequest.builder()
                .hotelId(1L)
                .roomTypeId(10L)
                .reservationId(5001L)
                .build();

        when(physicalRoomRepository.findFirstByHotelIdAndRoomTypeIdAndHousekeepingStatusAndOperationalStatusOrderByIdAsc(
                1L, 10L, HousekeepingStatus.CLEAN, OperationalStatus.OPERATIONAL
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> physicalRoomService.assignCleanRoom(request))
                .isInstanceOf(NoCleanRoomAvailableException.class)
                .hasMessageContaining("No clean room ready for check-in; front desk alerted");
    }

    @Test
    @DisplayName("OPS-04: Checkout automatically marks assigned room DIRTY")
    void shouldMarkRoomDirtyOnCheckout() {
        when(physicalRoomRepository.findById(101L)).thenReturn(Optional.of(sampleRoom));
        when(physicalRoomRepository.save(any(PhysicalRoom.class))).thenAnswer(inv -> inv.getArgument(0));

        PhysicalRoomDto dto = physicalRoomService.markRoomDirty(101L);

        assertThat(dto.getHousekeepingStatus()).isEqualTo(HousekeepingStatus.DIRTY);
        verify(physicalRoomRepository).save(sampleRoom);
    }
}
