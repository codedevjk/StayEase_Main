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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service managing physical room lifecycle, housekeeping transitions,
 * operational availability, and check-in room readiness assignment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhysicalRoomService {

    private final PhysicalRoomRepository physicalRoomRepository;
    private final HotelRepository hotelRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final PropertyAuditService propertyAuditService;

    @Transactional
    public PhysicalRoomDto registerPhysicalRoom(Long hotelId, RegisterPhysicalRoomRequest request) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found with ID: " + hotelId));

        RoomType roomType = roomTypeRepository.findById(request.getRoomTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Room type not found with ID: " + request.getRoomTypeId()));

        if (!roomType.getHotel().getId().equals(hotelId)) {
            throw new IllegalArgumentException("Room type " + request.getRoomTypeId() + " does not belong to hotel " + hotelId);
        }

        if (physicalRoomRepository.findByHotelIdAndRoomNumber(hotelId, request.getRoomNumber()).isPresent()) {
            throw new DuplicateRoomNumberException("Room number '" + request.getRoomNumber() + "' already exists in hotel ID " + hotelId);
        }

        PhysicalRoom room = PhysicalRoom.builder()
                .hotel(hotel)
                .roomType(roomType)
                .roomNumber(request.getRoomNumber())
                .housekeepingStatus(request.getHousekeepingStatus() != null ? request.getHousekeepingStatus() : HousekeepingStatus.CLEAN)
                .operationalStatus(request.getOperationalStatus() != null ? request.getOperationalStatus() : OperationalStatus.OPERATIONAL)
                .build();

        PhysicalRoom saved = physicalRoomRepository.save(room);

        // Synchronize total physical room count on room type
        long count = physicalRoomRepository.countByRoomTypeId(roomType.getId());
        roomType.setTotalPhysicalRooms((int) count);
        roomTypeRepository.save(roomType);

        propertyAuditService.recordAudit(
                0L,
                "ADMIN",
                "ROOM_OPERATION",
                "PHYSICAL_ROOM_REGISTERED",
                String.valueOf(saved.getId()),
                "Physical room " + saved.getRoomNumber() + " registered in hotel " + hotel.getName(),
                null,
                "{\"roomNumber\":\"" + saved.getRoomNumber() + "\",\"roomTypeId\":" + roomType.getId() + "}"
        );

        log.info("Registered physical room '{}' (ID {}) for hotel ID {}", saved.getRoomNumber(), saved.getId(), hotelId);
        return mapToDto(saved);
    }

    @Transactional(readOnly = true)
    public List<PhysicalRoomDto> getRoomsByHotel(Long hotelId) {
        if (!hotelRepository.existsById(hotelId)) {
            throw new ResourceNotFoundException("Hotel not found with ID: " + hotelId);
        }
        return physicalRoomRepository.findByHotelId(hotelId).stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public PhysicalRoomDto updateHousekeepingStatus(Long roomId, HousekeepingStatus newStatus) {
        PhysicalRoom room = physicalRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Physical room not found with ID: " + roomId));

        HousekeepingStatus oldStatus = room.getHousekeepingStatus();
        room.setHousekeepingStatus(newStatus);
        PhysicalRoom updated = physicalRoomRepository.save(room);

        propertyAuditService.recordAudit(
                0L,
                "ADMIN",
                "HOUSEKEEPING",
                "HOUSEKEEPING_STATUS_CHANGED",
                String.valueOf(room.getId()),
                "Room " + room.getRoomNumber() + " housekeeping changed from " + oldStatus + " to " + newStatus,
                "{\"housekeepingStatus\":\"" + oldStatus + "\"}",
                "{\"housekeepingStatus\":\"" + newStatus + "\"}"
        );

        log.info("Physical room ID {} status updated from {} to {}", roomId, oldStatus, newStatus);
        return mapToDto(updated);
    }

    @Transactional
    public PhysicalRoomDto updateOperationalStatus(Long roomId, OperationalStatus newStatus) {
        PhysicalRoom room = physicalRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Physical room not found with ID: " + roomId));

        OperationalStatus oldStatus = room.getOperationalStatus();
        room.setOperationalStatus(newStatus);
        PhysicalRoom updated = physicalRoomRepository.save(room);

        propertyAuditService.recordAudit(
                0L,
                "ADMIN",
                "ROOM_OPERATION",
                "OPERATIONAL_STATUS_CHANGED",
                String.valueOf(room.getId()),
                "Room " + room.getRoomNumber() + " operational status changed from " + oldStatus + " to " + newStatus,
                "{\"operationalStatus\":\"" + oldStatus + "\"}",
                "{\"operationalStatus\":\"" + newStatus + "\"}"
        );

        log.info("Physical room ID {} operational status updated from {} to {}", roomId, oldStatus, newStatus);
        return mapToDto(updated);
    }

    /**
     * Check-in assignment logic: finds an eligible physical room where
     * housekeepingStatus == CLEAN AND operationalStatus == OPERATIONAL.
     * Throws 409 Conflict if no clean room is ready.
     */
    @Transactional(readOnly = true)
    public AssignCleanRoomResponse assignCleanRoom(AssignCleanRoomRequest request) {
        PhysicalRoom readyRoom = physicalRoomRepository
                .findFirstByHotelIdAndRoomTypeIdAndHousekeepingStatusAndOperationalStatusOrderByIdAsc(
                        request.getHotelId(),
                        request.getRoomTypeId(),
                        HousekeepingStatus.CLEAN,
                        OperationalStatus.OPERATIONAL
                )
                .orElseThrow(() -> new NoCleanRoomAvailableException(
                        "No clean room ready for check-in; front desk alerted"
                ));

        log.info("Assigned clean room '{}' (ID {}) for reservation ID {}",
                readyRoom.getRoomNumber(), readyRoom.getId(), request.getReservationId());

        return AssignCleanRoomResponse.builder()
                .physicalRoomId(readyRoom.getId())
                .roomNumber(readyRoom.getRoomNumber())
                .build();
    }

    /**
     * Checkout transition: marks the physical room DIRTY.
     */
    @Transactional
    public PhysicalRoomDto markRoomDirty(Long roomId) {
        PhysicalRoom room = physicalRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Physical room not found with ID: " + roomId));

        room.setHousekeepingStatus(HousekeepingStatus.DIRTY);
        PhysicalRoom updated = physicalRoomRepository.save(room);

        propertyAuditService.recordAudit(
                0L,
                "SYSTEM",
                "HOUSEKEEPING",
                "ROOM_CHECKOUT_DIRTIED",
                String.valueOf(room.getId()),
                "Room " + room.getRoomNumber() + " automatically marked DIRTY upon checkout",
                null,
                "{\"housekeepingStatus\":\"DIRTY\"}"
        );

        log.info("Room ID {} marked DIRTY on checkout", roomId);
        return mapToDto(updated);
    }

    public PhysicalRoomDto mapToDto(PhysicalRoom room) {
        return PhysicalRoomDto.builder()
                .id(room.getId())
                .hotelId(room.getHotel().getId())
                .roomTypeId(room.getRoomType().getId())
                .roomNumber(room.getRoomNumber())
                .housekeepingStatus(room.getHousekeepingStatus())
                .operationalStatus(room.getOperationalStatus())
                .createdAt(room.getCreatedAt())
                .updatedAt(room.getUpdatedAt())
                .build();
    }
}
