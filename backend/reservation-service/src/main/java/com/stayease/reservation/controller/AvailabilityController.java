package com.stayease.reservation.controller;

import com.stayease.reservation.dto.HotelAvailabilityResponse;
import com.stayease.reservation.dto.RoomTypeAvailabilityDto;
import com.stayease.reservation.service.AvailabilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    /**
     * Authoritative real-time availability search across hotel room types for requested dates.
     */
    @GetMapping("/availability")
    public ResponseEntity<HotelAvailabilityResponse> getHotelAvailability(
            @RequestParam Long hotelId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(required = false, defaultValue = "1") Integer guests) {

        HotelAvailabilityResponse response = availabilityService.getHotelAvailability(hotelId, checkIn, checkOut, guests);
        return ResponseEntity.ok(response);
    }

    /**
     * Authoritative real-time availability check for a specific room type.
     */
    @GetMapping("/room-types/{roomTypeId}/availability")
    public ResponseEntity<RoomTypeAvailabilityDto> getRoomTypeAvailability(
            @PathVariable Long roomTypeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(required = false, defaultValue = "1") Integer guests) {

        RoomTypeAvailabilityDto response = availabilityService.getRoomTypeAvailability(roomTypeId, checkIn, checkOut, guests);
        return ResponseEntity.ok(response);
    }
}
