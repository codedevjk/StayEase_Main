package com.stayease.property.controller;

import com.stayease.property.dto.*;
import com.stayease.property.service.PhysicalRoomService;
import com.stayease.property.service.PricingCalculatorService;
import com.stayease.property.service.RoomTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * Internal inter-service endpoints queried by Reservation Service.
 * Not exposed via API Gateway to external public clients.
 */
@RestController
@RequestMapping("/internal/properties")
@RequiredArgsConstructor
public class InternalPropertyController {

    private final RoomTypeService roomTypeService;
    private final PricingCalculatorService pricingCalculatorService;
    private final PhysicalRoomService physicalRoomService;

    /**
     * Queries authoritative configured sellable capacity for a room type.
     */
    @GetMapping("/room-types/{roomTypeId}/capacity")
    public ResponseEntity<RoomTypeCapacityResponse> getRoomTypeCapacity(@PathVariable Long roomTypeId) {
        RoomTypeCapacityResponse response = roomTypeService.calculateCapacity(roomTypeId);
        return ResponseEntity.ok(response);
    }

    /**
     * Calculates nightly rates applying seasonal and weekend override precedence.
     */
    @GetMapping("/room-types/{roomTypeId}/pricing")
    public ResponseEntity<RoomTypePricingResponse> getRoomTypePricing(
            @PathVariable Long roomTypeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut) {
        RoomTypePricingResponse response = pricingCalculatorService.calculatePricing(roomTypeId, checkIn, checkOut);
        return ResponseEntity.ok(response);
    }

    /**
     * Check-in assignment: assigns an eligible physical room that is CLEAN and OPERATIONAL.
     */
    @PostMapping("/rooms/assign-clean")
    public ResponseEntity<AssignCleanRoomResponse> assignCleanRoom(
            @Valid @RequestBody AssignCleanRoomRequest request) {
        AssignCleanRoomResponse response = physicalRoomService.assignCleanRoom(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Checkout transition: marks room DIRTY.
     */
    @PostMapping("/rooms/{roomId}/mark-dirty")
    public ResponseEntity<PhysicalRoomDto> markRoomDirty(@PathVariable Long roomId) {
        PhysicalRoomDto response = physicalRoomService.markRoomDirty(roomId);
        return ResponseEntity.ok(response);
    }
}
