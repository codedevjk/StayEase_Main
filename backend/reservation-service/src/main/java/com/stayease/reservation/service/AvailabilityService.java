package com.stayease.reservation.service;

import com.stayease.reservation.client.*;
import com.stayease.reservation.dto.HotelAvailabilityResponse;
import com.stayease.reservation.dto.NightlyRateItemDto;
import com.stayease.reservation.dto.PricingSnapshotDto;
import com.stayease.reservation.dto.RoomTypeAvailabilityDto;
import com.stayease.reservation.exception.InvalidDateRangeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    private final PropertyServiceClient propertyClient;
    private final InventoryLedgerService inventoryLedgerService;
    private final PricingService pricingService;

    @Value("${stayease.time-zone:Asia/Kolkata}")
    private String timeZone;

    public HotelAvailabilityResponse getHotelAvailability(
            Long hotelId,
            LocalDate checkIn,
            LocalDate checkOut,
            Integer guests) {

        validateDates(checkIn, checkOut);

        PropertyHotelDto hotel = propertyClient.getHotelDetails(hotelId);
        int nights = (int) ChronoUnit.DAYS.between(checkIn, checkOut);
        int guestCount = (guests != null && guests > 0) ? guests : 1;

        List<RoomTypeAvailabilityDto> roomTypesAvailability = new ArrayList<>();

        if (hotel.getRoomTypes() != null) {
            java.util.Set<Long> seenIds = new java.util.HashSet<>();
            for (PropertyRoomTypeDto rt : hotel.getRoomTypes()) {
                if (rt.getId() != null && !seenIds.add(rt.getId())) {
                    continue; // Skip duplicate room type
                }
                if (rt.getMaxGuests() != null && rt.getMaxGuests() < guestCount) {
                    continue; // Room cannot accommodate guest count
                }

                RoomTypeAvailabilityDto availabilityDto = calculateRoomTypeAvailability(rt.getId(), rt.getName(), rt.getMaxGuests(), checkIn, checkOut);
                roomTypesAvailability.add(availabilityDto);
            }
        }

        return HotelAvailabilityResponse.builder()
                .hotelId(hotelId)
                .checkInDate(checkIn)
                .checkOutDate(checkOut)
                .nights(nights)
                .roomTypes(roomTypesAvailability)
                .build();
    }

    public RoomTypeAvailabilityDto getRoomTypeAvailability(
            Long roomTypeId,
            LocalDate checkIn,
            LocalDate checkOut,
            Integer guests) {

        validateDates(checkIn, checkOut);
        return calculateRoomTypeAvailability(roomTypeId, null, guests, checkIn, checkOut);
    }

    private RoomTypeAvailabilityDto calculateRoomTypeAvailability(
            Long roomTypeId,
            String roomTypeName,
            Integer maxGuests,
            LocalDate checkIn,
            LocalDate checkOut) {

        PropertyCapacityDto capacityDto = propertyClient.getSellableCapacity(roomTypeId);
        int sellableCapacity = capacityDto.getSellableCapacity() != null ? capacityDto.getSellableCapacity() : 0;

        int availableRooms = inventoryLedgerService.calculateAvailableCapacity(
                roomTypeId, checkIn, checkOut, sellableCapacity);

        PropertyPricingDto pricingDto = propertyClient.getPricing(roomTypeId, checkIn, checkOut);
        List<PropertyNightlyRateDto> nightlyRates = pricingDto.getNightlyRates() != null
                ? pricingDto.getNightlyRates()
                : List.of();

        PricingSnapshotDto snapshot = pricingService.calculatePricingSnapshot(nightlyRates);

        List<NightlyRateItemDto> rateItems = snapshot.getNightlyRates().stream()
                .map(r -> NightlyRateItemDto.builder()
                        .date(r.getDate())
                        .rate(r.getRate())
                        .rateType(r.getRateType())
                        .build())
                .toList();

        return RoomTypeAvailabilityDto.builder()
                .roomTypeId(roomTypeId)
                .name(roomTypeName)
                .maxGuests(maxGuests)
                .availableRooms(availableRooms)
                .nightlyRates(rateItems)
                .lodgingSubtotal(snapshot.getLodgingSubtotal())
                .taxPercentage(snapshot.getTaxPercentage())
                .taxAmount(snapshot.getTaxAmount())
                .totalAmount(snapshot.getTotalAmount())
                .build();
    }

    private void validateDates(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null) {
            throw new InvalidDateRangeException("Check-in and check-out dates are required");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new InvalidDateRangeException("Check-out date must be after check-in date");
        }
        LocalDate today = LocalDate.now(ZoneId.of(timeZone));
        if (checkIn.isBefore(today)) {
            throw new InvalidDateRangeException("Check-in date cannot be in the past");
        }
    }
}
