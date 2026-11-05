package com.stayease.reservation.repository;

import com.stayease.reservation.entity.RoomTypeInventory;
import com.stayease.reservation.entity.RoomTypeInventoryId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoomTypeInventoryRepository extends JpaRepository<RoomTypeInventory, RoomTypeInventoryId> {

    Optional<RoomTypeInventory> findByRoomTypeIdAndStayDate(Long roomTypeId, LocalDate stayDate);

    @Query("SELECT r FROM RoomTypeInventory r WHERE r.roomTypeId = :roomTypeId AND r.stayDate >= :checkInDate AND r.stayDate < :checkOutDate ORDER BY r.stayDate ASC")
    List<RoomTypeInventory> findForDates(
            @Param("roomTypeId") Long roomTypeId,
            @Param("checkInDate") LocalDate checkInDate,
            @Param("checkOutDate") LocalDate checkOutDate);

    /**
     * Pessimistic row locking in strict stayDate ASC order to avoid deadlocks.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RoomTypeInventory r WHERE r.roomTypeId = :roomTypeId AND r.stayDate >= :checkInDate AND r.stayDate < :checkOutDate ORDER BY r.stayDate ASC")
    List<RoomTypeInventory> findAndLockForDates(
            @Param("roomTypeId") Long roomTypeId,
            @Param("checkInDate") LocalDate checkInDate,
            @Param("checkOutDate") LocalDate checkOutDate);
}
