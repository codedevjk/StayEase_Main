package com.stayease.property.controller;

import com.stayease.property.dto.*;
import com.stayease.property.service.HotelService;
import com.stayease.property.service.PhysicalRoomService;
import com.stayease.property.service.PricingCalculatorService;
import com.stayease.property.service.RoomTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@RequiredArgsConstructor
public class AdminPropertyController {

    private final HotelService hotelService;
    private final RoomTypeService roomTypeService;
    private final PhysicalRoomService physicalRoomService;
    private final PricingCalculatorService pricingCalculatorService;

    // ── Hotels ─────────────────────────────────────────────────────────────

    @PostMapping("/hotels")
    public ResponseEntity<HotelDetailDto> createHotel(@Valid @RequestBody CreateHotelRequest request) {
        HotelDetailDto created = hotelService.createHotel(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/hotels/{hotelId}")
    public ResponseEntity<HotelDetailDto> updateHotel(
            @PathVariable Long hotelId,
            @Valid @RequestBody UpdateHotelRequest request) {
        HotelDetailDto updated = hotelService.updateHotel(hotelId, request);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/hotels/{hotelId}/status")
    public ResponseEntity<HotelDetailDto> toggleHotelStatus(
            @PathVariable Long hotelId,
            @RequestParam boolean active) {
        HotelDetailDto updated = hotelService.toggleHotelStatus(hotelId, active);
        return ResponseEntity.ok(updated);
    }

    // ── Room Types ─────────────────────────────────────────────────────────

    @PostMapping("/hotels/{hotelId}/room-types")
    public ResponseEntity<RoomTypeDetailDto> createRoomType(
            @PathVariable Long hotelId,
            @Valid @RequestBody CreateRoomTypeRequest request) {
        RoomTypeDetailDto created = roomTypeService.createRoomType(hotelId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/room-types/{roomTypeId}")
    public ResponseEntity<RoomTypeDetailDto> updateRoomType(
            @PathVariable Long roomTypeId,
            @Valid @RequestBody UpdateRoomTypeRequest request) {
        RoomTypeDetailDto updated = roomTypeService.updateRoomType(roomTypeId, request);
        return ResponseEntity.ok(updated);
    }

    // ── Rate Overrides ─────────────────────────────────────────────────────

    @PostMapping("/room-types/{roomTypeId}/rate-overrides")
    public ResponseEntity<RateOverrideDto> createRateOverride(
            @PathVariable Long roomTypeId,
            @Valid @RequestBody CreateRateOverrideRequest request) {
        RateOverrideDto created = pricingCalculatorService.createRateOverride(roomTypeId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ── Physical Rooms & Housekeeping ──────────────────────────────────────

    @PostMapping("/hotels/{hotelId}/rooms")
    public ResponseEntity<PhysicalRoomDto> registerPhysicalRoom(
            @PathVariable Long hotelId,
            @Valid @RequestBody RegisterPhysicalRoomRequest request) {
        PhysicalRoomDto created = physicalRoomService.registerPhysicalRoom(hotelId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/hotels/{hotelId}/rooms")
    public ResponseEntity<List<PhysicalRoomDto>> getRoomsByHotel(@PathVariable Long hotelId) {
        List<PhysicalRoomDto> rooms = physicalRoomService.getRoomsByHotel(hotelId);
        return ResponseEntity.ok(rooms);
    }

    @PatchMapping("/rooms/{roomId}/housekeeping")
    public ResponseEntity<PhysicalRoomDto> updateHousekeeping(
            @PathVariable Long roomId,
            @Valid @RequestBody UpdateHousekeepingRequest request) {
        PhysicalRoomDto updated = physicalRoomService.updateHousekeepingStatus(roomId, request.getHousekeepingStatus());
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/rooms/{roomId}/operational")
    public ResponseEntity<PhysicalRoomDto> updateOperational(
            @PathVariable Long roomId,
            @Valid @RequestBody UpdateOperationalRequest request) {
        PhysicalRoomDto updated = physicalRoomService.updateOperationalStatus(roomId, request.getOperationalStatus());
        return ResponseEntity.ok(updated);
    }
}
