package com.stayease.property.repository;

import com.stayease.property.enums.HousekeepingStatus;
import com.stayease.property.enums.OperationalStatus;
import com.stayease.property.entity.PhysicalRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PhysicalRoomRepository extends JpaRepository<PhysicalRoom, Long> {

    List<PhysicalRoom> findByHotelId(Long hotelId);

    List<PhysicalRoom> findByRoomTypeId(Long roomTypeId);

    Optional<PhysicalRoom> findByHotelIdAndRoomNumber(Long hotelId, String roomNumber);

    long countByRoomTypeId(Long roomTypeId);

    long countByRoomTypeIdAndOperationalStatus(Long roomTypeId, OperationalStatus operationalStatus);

    Optional<PhysicalRoom> findFirstByHotelIdAndRoomTypeIdAndHousekeepingStatusAndOperationalStatusOrderByIdAsc(
            Long hotelId,
            Long roomTypeId,
            HousekeepingStatus housekeepingStatus,
            OperationalStatus operationalStatus
    );
}
