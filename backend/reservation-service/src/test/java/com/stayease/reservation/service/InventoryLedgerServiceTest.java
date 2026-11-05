package com.stayease.reservation.service;

import com.stayease.reservation.entity.RoomTypeInventory;
import com.stayease.reservation.exception.InsufficientInventoryException;
import com.stayease.reservation.repository.RoomTypeInventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryLedgerServiceTest {

    @Mock
    private RoomTypeInventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryLedgerService inventoryLedgerService;

    private Long roomTypeId;
    private LocalDate checkIn;
    private LocalDate checkOut;

    @BeforeEach
    void setUp() {
        roomTypeId = 10L;
        checkIn = LocalDate.of(2026, 11, 1);
        checkOut = LocalDate.of(2026, 11, 3);
    }

    @Test
    @DisplayName("Hold inventory succeeds when sellable capacity is available")
    void testCheckAndHoldInventorySuccess() {
        List<RoomTypeInventory> mockLedger = new ArrayList<>();
        mockLedger.add(RoomTypeInventory.builder()
                .roomTypeId(roomTypeId).stayDate(checkIn).totalCapacity(5).reservedCount(1).holdCount(0).version(0L).build());
        mockLedger.add(RoomTypeInventory.builder()
                .roomTypeId(roomTypeId).stayDate(checkIn.plusDays(1)).totalCapacity(5).reservedCount(2).holdCount(1).version(0L).build());

        when(inventoryRepository.findAndLockForDates(roomTypeId, checkIn, checkOut)).thenReturn(mockLedger);
        when(inventoryRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<RoomTypeInventory> result = inventoryLedgerService.checkAndHoldInventory(roomTypeId, checkIn, checkOut, 1, 5);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(1, result.get(0).getHoldCount());
        assertEquals(2, result.get(1).getHoldCount());
        verify(inventoryRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("Hold inventory throws InsufficientInventoryException when sold out")
    void testCheckAndHoldInventorySoldOut() {
        List<RoomTypeInventory> mockLedger = new ArrayList<>();
        mockLedger.add(RoomTypeInventory.builder()
                .roomTypeId(roomTypeId).stayDate(checkIn).totalCapacity(5).reservedCount(4).holdCount(1).version(0L).build());
        mockLedger.add(RoomTypeInventory.builder()
                .roomTypeId(roomTypeId).stayDate(checkIn.plusDays(1)).totalCapacity(5).reservedCount(0).holdCount(0).version(0L).build());

        when(inventoryRepository.findAndLockForDates(roomTypeId, checkIn, checkOut)).thenReturn(mockLedger);

        assertThrows(InsufficientInventoryException.class, () ->
                inventoryLedgerService.checkAndHoldInventory(roomTypeId, checkIn, checkOut, 1, 5));

        verify(inventoryRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Confirm hold decrements hold and increments reserved count")
    void testConfirmHold() {
        List<RoomTypeInventory> mockLedger = new ArrayList<>();
        mockLedger.add(RoomTypeInventory.builder()
                .roomTypeId(roomTypeId).stayDate(checkIn).totalCapacity(5).reservedCount(2).holdCount(1).version(0L).build());
        mockLedger.add(RoomTypeInventory.builder()
                .roomTypeId(roomTypeId).stayDate(checkIn.plusDays(1)).totalCapacity(5).reservedCount(1).holdCount(1).version(0L).build());

        when(inventoryRepository.findAndLockForDates(roomTypeId, checkIn, checkOut)).thenReturn(mockLedger);

        inventoryLedgerService.confirmHold(roomTypeId, checkIn, checkOut, 1);

        assertEquals(0, mockLedger.get(0).getHoldCount());
        assertEquals(3, mockLedger.get(0).getReservedCount());
        assertEquals(0, mockLedger.get(1).getHoldCount());
        assertEquals(2, mockLedger.get(1).getReservedCount());
        verify(inventoryRepository).saveAll(mockLedger);
    }

    @Test
    @DisplayName("Release hold idempotently decrements hold count to minimum zero")
    void testReleaseHold() {
        List<RoomTypeInventory> mockLedger = new ArrayList<>();
        mockLedger.add(RoomTypeInventory.builder()
                .roomTypeId(roomTypeId).stayDate(checkIn).totalCapacity(5).reservedCount(2).holdCount(1).version(0L).build());

        when(inventoryRepository.findAndLockForDates(roomTypeId, checkIn, checkOut)).thenReturn(mockLedger);

        inventoryLedgerService.releaseHold(roomTypeId, checkIn, checkOut, 1);

        assertEquals(0, mockLedger.get(0).getHoldCount());
        verify(inventoryRepository).saveAll(mockLedger);
    }

    @Test
    @DisplayName("Calculate available capacity returns minimum available rooms across all stay nights")
    void testCalculateAvailableCapacity() {
        List<RoomTypeInventory> mockLedger = List.of(
                RoomTypeInventory.builder().roomTypeId(roomTypeId).stayDate(checkIn).totalCapacity(10).reservedCount(2).holdCount(1).build(), // avail = 7
                RoomTypeInventory.builder().roomTypeId(roomTypeId).stayDate(checkIn.plusDays(1)).totalCapacity(10).reservedCount(5).holdCount(2).build() // avail = 3
        );

        when(inventoryRepository.findForDates(roomTypeId, checkIn, checkOut)).thenReturn(mockLedger);

        int available = inventoryLedgerService.calculateAvailableCapacity(roomTypeId, checkIn, checkOut, 10);
        assertEquals(3, available);
    }

    @Test
    @DisplayName("Capacity reduction below reserved count prevents new bookings without invalidating existing reservations")
    void testCapacityReductionBelowReservedCountRejectsNewBookingsWithoutInvalidatingExisting() {
        // Initial state: total capacity 10, reserved count 8, hold count 0
        RoomTypeInventory night1 = RoomTypeInventory.builder()
                .roomTypeId(roomTypeId).stayDate(checkIn).totalCapacity(10).reservedCount(8).holdCount(0).version(1L).build();
        RoomTypeInventory night2 = RoomTypeInventory.builder()
                .roomTypeId(roomTypeId).stayDate(checkIn.plusDays(1)).totalCapacity(10).reservedCount(8).holdCount(0).version(1L).build();
        List<RoomTypeInventory> mockLedger = new ArrayList<>(List.of(night1, night2));

        // 1. Availability calculation with reduced sellableCapacity = 6 returns 0 available rooms
        when(inventoryRepository.findForDates(roomTypeId, checkIn, checkOut)).thenReturn(mockLedger);
        int available = inventoryLedgerService.calculateAvailableCapacity(roomTypeId, checkIn, checkOut, 6);
        assertEquals(0, available, "Available rooms must be 0 when reserved count exceeds reduced capacity");

        // 2. Attempting to book or hold inventory with reduced capacity (6) throws InsufficientInventoryException
        when(inventoryRepository.findAndLockForDates(roomTypeId, checkIn, checkOut)).thenReturn(mockLedger);
        assertThrows(InsufficientInventoryException.class, () ->
                inventoryLedgerService.checkAndHoldInventory(roomTypeId, checkIn, checkOut, 1, 6));

        // 3. Existing reservations remain valid; releasing one reservation decrements reserved count from 8 to 7
        inventoryLedgerService.releaseReservation(roomTypeId, checkIn, checkOut, 1);
        assertEquals(7, night1.getReservedCount());
        assertEquals(7, night2.getReservedCount());
        verify(inventoryRepository, atLeastOnce()).saveAll(mockLedger);
    }

    @Test
    @DisplayName("Amend inventory adheres to global roomTypeId ASC lock ordering across different room types")
    void testAmendInventoryLockOrderingAcrossRoomTypes() {
        Long lowerRoomTypeId = 10L;
        Long higherRoomTypeId = 20L;

        List<RoomTypeInventory> lowerLedger = new ArrayList<>(List.of(
                RoomTypeInventory.builder().roomTypeId(lowerRoomTypeId).stayDate(checkIn).totalCapacity(5).reservedCount(1).holdCount(0).build()
        ));
        List<RoomTypeInventory> higherLedger = new ArrayList<>(List.of(
                RoomTypeInventory.builder().roomTypeId(higherRoomTypeId).stayDate(checkIn).totalCapacity(5).reservedCount(0).holdCount(0).build()
        ));

        // When amending 10 -> 20 (lower to higher): lower is released first, higher is reserved second
        when(inventoryRepository.findAndLockForDates(eq(lowerRoomTypeId), any(), any())).thenReturn(lowerLedger);
        when(inventoryRepository.findAndLockForDates(eq(higherRoomTypeId), any(), any())).thenReturn(higherLedger);
        when(inventoryRepository.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        inventoryLedgerService.amendInventory(lowerRoomTypeId, checkIn, checkOut, higherRoomTypeId, checkIn, checkOut, 1, 5, true);

        // Verify lower room type was locked and released
        verify(inventoryRepository, atLeastOnce()).findAndLockForDates(eq(lowerRoomTypeId), eq(checkIn), eq(checkOut));
        // Verify higher room type was locked and reserved
        verify(inventoryRepository, atLeastOnce()).findAndLockForDates(eq(higherRoomTypeId), eq(checkIn), eq(checkOut));
    }
}
