package com.stayease.reservation.service;

import com.stayease.reservation.entity.RoomTypeInventory;
import com.stayease.reservation.exception.InsufficientInventoryException;
import com.stayease.reservation.repository.RoomTypeInventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryLedgerService {

    private final RoomTypeInventoryRepository inventoryRepository;

    /**
     * Locks inventory rows for the stay dates in strict stay_date ASC order,
     * ensuring coverage for any uninitialized dates, and holding inventory for PREPAID reservations.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<RoomTypeInventory> checkAndHoldInventory(
            Long roomTypeId,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            int requestedRooms,
            int sellableCapacity) {

        List<RoomTypeInventory> ledger = lockAndEnsureCoverage(roomTypeId, checkInDate, checkOutDate, sellableCapacity);

        // Verify invariant
        for (RoomTypeInventory night : ledger) {
            int available = night.getTotalCapacity() - (night.getReservedCount() + night.getHoldCount());
            if (available < requestedRooms) {
                log.warn("Insufficient inventory for roomType {} on date {}: total={}, reserved={}, hold={}, requested={}",
                        roomTypeId, night.getStayDate(), night.getTotalCapacity(), night.getReservedCount(), night.getHoldCount(), requestedRooms);
                throw new InsufficientInventoryException("Sold out for date: " + night.getStayDate());
            }
        }

        // Increment hold count
        ledger.forEach(night -> night.setHoldCount(night.getHoldCount() + requestedRooms));
        return inventoryRepository.saveAll(ledger);
    }

    /**
     * Locks inventory rows in stay_date ASC order and reserves inventory directly for PAY_LATER reservations.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<RoomTypeInventory> checkAndReserveInventory(
            Long roomTypeId,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            int requestedRooms,
            int sellableCapacity) {

        List<RoomTypeInventory> ledger = lockAndEnsureCoverage(roomTypeId, checkInDate, checkOutDate, sellableCapacity);

        // Verify invariant
        for (RoomTypeInventory night : ledger) {
            int available = night.getTotalCapacity() - (night.getReservedCount() + night.getHoldCount());
            if (available < requestedRooms) {
                log.warn("Insufficient inventory for roomType {} on date {}: total={}, reserved={}, hold={}, requested={}",
                        roomTypeId, night.getStayDate(), night.getTotalCapacity(), night.getReservedCount(), night.getHoldCount(), requestedRooms);
                throw new InsufficientInventoryException("Sold out for date: " + night.getStayDate());
            }
        }

        // Increment reserved count
        ledger.forEach(night -> night.setReservedCount(night.getReservedCount() + requestedRooms));
        return inventoryRepository.saveAll(ledger);
    }

    /**
     * Confirms an active hold: moves count from hold_count to reserved_count.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void confirmHold(Long roomTypeId, LocalDate checkInDate, LocalDate checkOutDate, int rooms) {
        List<RoomTypeInventory> ledger = inventoryRepository.findAndLockForDates(roomTypeId, checkInDate, checkOutDate);
        for (RoomTypeInventory night : ledger) {
            night.setHoldCount(Math.max(0, night.getHoldCount() - rooms));
            night.setReservedCount(night.getReservedCount() + rooms);
        }
        inventoryRepository.saveAll(ledger);
        log.debug("Confirmed hold for roomType {} between {} and {}", roomTypeId, checkInDate, checkOutDate);
    }

    /**
     * Idempotently releases an active hold: decrements hold_count.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void releaseHold(Long roomTypeId, LocalDate checkInDate, LocalDate checkOutDate, int rooms) {
        List<RoomTypeInventory> ledger = inventoryRepository.findAndLockForDates(roomTypeId, checkInDate, checkOutDate);
        for (RoomTypeInventory night : ledger) {
            night.setHoldCount(Math.max(0, night.getHoldCount() - rooms));
        }
        inventoryRepository.saveAll(ledger);
        log.debug("Released hold for roomType {} between {} and {}", roomTypeId, checkInDate, checkOutDate);
    }

    /**
     * Idempotently releases a confirmed reservation: decrements reserved_count.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void releaseReservation(Long roomTypeId, LocalDate checkInDate, LocalDate checkOutDate, int rooms) {
        List<RoomTypeInventory> ledger = inventoryRepository.findAndLockForDates(roomTypeId, checkInDate, checkOutDate);
        for (RoomTypeInventory night : ledger) {
            night.setReservedCount(Math.max(0, night.getReservedCount() - rooms));
        }
        inventoryRepository.saveAll(ledger);
        log.debug("Released reservation inventory for roomType {} between {} and {}", roomTypeId, checkInDate, checkOutDate);
    }

    /**
     * Atomically releases previous inventory and allocates new inventory for an amendment,
     * acquiring locks in strict global order (roomTypeId ASC, stayDate ASC) to prevent deadlocks
     * across different room types.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void amendInventory(
            Long oldRoomTypeId,
            LocalDate oldCheckIn,
            LocalDate oldCheckOut,
            Long newRoomTypeId,
            LocalDate newCheckIn,
            LocalDate newCheckOut,
            int rooms,
            int sellableCapacity,
            boolean isConfirmed) {

        if (oldRoomTypeId.equals(newRoomTypeId)) {
            // Same room type: release old dates, then hold/reserve new dates
            if (isConfirmed) {
                releaseReservation(oldRoomTypeId, oldCheckIn, oldCheckOut, rooms);
                checkAndReserveInventory(newRoomTypeId, newCheckIn, newCheckOut, rooms, sellableCapacity);
            } else {
                releaseHold(oldRoomTypeId, oldCheckIn, oldCheckOut, rooms);
                checkAndHoldInventory(newRoomTypeId, newCheckIn, newCheckOut, rooms, sellableCapacity);
            }
        } else if (oldRoomTypeId < newRoomTypeId) {
            // Strict global lock order: lock lower ID first, higher ID second
            if (isConfirmed) {
                releaseReservation(oldRoomTypeId, oldCheckIn, oldCheckOut, rooms);
                checkAndReserveInventory(newRoomTypeId, newCheckIn, newCheckOut, rooms, sellableCapacity);
            } else {
                releaseHold(oldRoomTypeId, oldCheckIn, oldCheckOut, rooms);
                checkAndHoldInventory(newRoomTypeId, newCheckIn, newCheckOut, rooms, sellableCapacity);
            }
        } else {
            // Strict global lock order: lock lower ID (newRoomTypeId) first, higher ID (oldRoomTypeId) second
            if (isConfirmed) {
                checkAndReserveInventory(newRoomTypeId, newCheckIn, newCheckOut, rooms, sellableCapacity);
                releaseReservation(oldRoomTypeId, oldCheckIn, oldCheckOut, rooms);
            } else {
                checkAndHoldInventory(newRoomTypeId, newCheckIn, newCheckOut, rooms, sellableCapacity);
                releaseHold(oldRoomTypeId, oldCheckIn, oldCheckOut, rooms);
            }
        }
    }

    /**
     * Queries available capacity across the date range without locking.
     */
    @Transactional(readOnly = true)
    public int calculateAvailableCapacity(
            Long roomTypeId,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            int sellableCapacity) {

        List<RoomTypeInventory> ledger = inventoryRepository.findForDates(roomTypeId, checkInDate, checkOutDate);
        Map<LocalDate, RoomTypeInventory> ledgerMap = ledger.stream()
                .collect(Collectors.toMap(RoomTypeInventory::getStayDate, r -> r));

        int minAvailable = sellableCapacity;

        LocalDate curr = checkInDate;
        while (curr.isBefore(checkOutDate)) {
            RoomTypeInventory night = ledgerMap.get(curr);
            int nightCapacity = (night != null) ? Math.min(night.getTotalCapacity(), sellableCapacity) : sellableCapacity;
            int nightReserved = (night != null) ? night.getReservedCount() : 0;
            int nightHold = (night != null) ? night.getHoldCount() : 0;

            int nightAvailable = Math.max(0, nightCapacity - (nightReserved + nightHold));
            if (nightAvailable < minAvailable) {
                minAvailable = nightAvailable;
            }
            curr = curr.plusDays(1);
        }

        return minAvailable;
    }

    private List<RoomTypeInventory> lockAndEnsureCoverage(
            Long roomTypeId,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            int sellableCapacity) {

        // First lock existing rows in strict order
        List<RoomTypeInventory> existing = inventoryRepository.findAndLockForDates(roomTypeId, checkInDate, checkOutDate);
        Map<LocalDate, RoomTypeInventory> existingMap = existing.stream()
                .collect(Collectors.toMap(RoomTypeInventory::getStayDate, r -> r));

        List<RoomTypeInventory> result = new ArrayList<>();
        List<RoomTypeInventory> newRows = new ArrayList<>();
        boolean modifiedExisting = false;

        LocalDate curr = checkInDate;
        while (curr.isBefore(checkOutDate)) {
            RoomTypeInventory row = existingMap.get(curr);
            if (row == null) {
                row = RoomTypeInventory.builder()
                        .roomTypeId(roomTypeId)
                        .stayDate(curr)
                        .totalCapacity(sellableCapacity)
                        .reservedCount(0)
                        .holdCount(0)
                        .version(0L)
                        .build();
                newRows.add(row);
            } else if (row.getTotalCapacity() != sellableCapacity) {
                row.setTotalCapacity(sellableCapacity);
                modifiedExisting = true;
            }
            result.add(row);
            curr = curr.plusDays(1);
        }

        if (modifiedExisting) {
            inventoryRepository.saveAll(existing);
        }

        if (!newRows.isEmpty()) {
            inventoryRepository.saveAll(newRows);
            // Re-lock all in strict stayDate order to ensure consistency
            return inventoryRepository.findAndLockForDates(roomTypeId, checkInDate, checkOutDate);
        }

        result.sort(Comparator.comparing(RoomTypeInventory::getStayDate));
        return result;
    }
}
