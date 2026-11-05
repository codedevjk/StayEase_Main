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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;

/**
 * Service managing room-type definitions and authoritative sellable capacity calculations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomTypeService {

    private final RoomTypeRepository roomTypeRepository;
    private final HotelRepository hotelRepository;
    private final PhysicalRoomRepository physicalRoomRepository;
    private final PropertyAuditService propertyAuditService;

    @Transactional
    public RoomTypeDetailDto createRoomType(Long hotelId, CreateRoomTypeRequest request) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID: " + hotelId));

        RoomType roomType = RoomType.builder()
                .hotel(hotel)
                .name(request.getName())
                .description(request.getDescription())
                .maxGuests(request.getMaxGuests() != null ? request.getMaxGuests() : 2)
                .baseNightlyRate(request.getBaseNightlyRate().setScale(2, RoundingMode.HALF_UP))
                .totalPhysicalRooms(0)
                .build();

        RoomType saved = roomTypeRepository.save(roomType);

        propertyAuditService.recordAudit(
                0L,
                "ADMIN",
                "HOTEL_OPERATION",
                "ROOM_TYPE_CREATED",
                String.valueOf(saved.getId()),
                "Room type " + saved.getName() + " created for hotel " + hotel.getName(),
                null,
                "{\"name\":\"" + saved.getName() + "\",\"baseNightlyRate\":" + saved.getBaseNightlyRate() + "}"
        );

        log.info("Created room type '{}' (ID {}) for hotel ID {}", saved.getName(), saved.getId(), hotelId);
        return mapToDetailDto(saved);
    }

    @Transactional
    public RoomTypeDetailDto updateRoomType(Long roomTypeId, UpdateRoomTypeRequest request) {
        RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Room type not found with ID: " + roomTypeId));

        roomType.setName(request.getName());
        roomType.setDescription(request.getDescription());
        if (request.getMaxGuests() != null) {
            roomType.setMaxGuests(request.getMaxGuests());
        }
        if (request.getBaseNightlyRate() != null) {
            roomType.setBaseNightlyRate(request.getBaseNightlyRate().setScale(2, RoundingMode.HALF_UP));
        }

        RoomType updated = roomTypeRepository.save(roomType);
        log.info("Updated room type ID {}", roomTypeId);
        return mapToDetailDto(updated);
    }

    /**
     * Calculates authoritative sellable capacity for Reservation Service:
     * Sellable Capacity = Total Physical Rooms of Type - Count(OUT_OF_SERVICE rooms).
     * DIRTY and INSPECTING rooms do NOT reduce sellable capacity.
     */
    @Transactional(readOnly = true)
    public RoomTypeCapacityResponse calculateCapacity(Long roomTypeId) {
        RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Room type not found with ID: " + roomTypeId));

        long totalRooms = physicalRoomRepository.countByRoomTypeId(roomTypeId);
        long outOfServiceRooms = physicalRoomRepository.countByRoomTypeIdAndOperationalStatus(
                roomTypeId, OperationalStatus.OUT_OF_SERVICE
        );

        int sellableCapacity = (int) Math.max(0, totalRooms - outOfServiceRooms);

        log.debug("Capacity check for roomType {}: total={}, outOfService={}, sellableCapacity={}",
                roomTypeId, totalRooms, outOfServiceRooms, sellableCapacity);

        return RoomTypeCapacityResponse.builder()
                .roomTypeId(roomType.getId())
                .sellableCapacity(sellableCapacity)
                .totalPhysicalRooms((int) totalRooms)
                .outOfServiceRooms((int) outOfServiceRooms)
                .build();
    }

    public RoomTypeDetailDto mapToDetailDto(RoomType roomType) {
        long totalRooms = physicalRoomRepository.countByRoomTypeId(roomType.getId());
        return RoomTypeDetailDto.builder()
                .id(roomType.getId())
                .name(roomType.getName())
                .description(roomType.getDescription())
                .maxGuests(roomType.getMaxGuests())
                .baseNightlyRate(roomType.getBaseNightlyRate())
                .totalRooms((int) totalRooms)
                .build();
    }
}
