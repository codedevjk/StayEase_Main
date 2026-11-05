package com.stayease.property.service;

import com.stayease.property.dto.CreateRateOverrideRequest;
import com.stayease.property.dto.NightlyRateDto;
import com.stayease.property.dto.RateOverrideDto;
import com.stayease.property.dto.RoomTypePricingResponse;
import com.stayease.property.entity.RateOverride;
import com.stayease.property.entity.RoomType;
import com.stayease.property.exception.InvalidRateDateRangeException;
import com.stayease.property.exception.ResourceNotFoundException;
import com.stayease.property.repository.RateOverrideRepository;
import com.stayease.property.repository.RoomTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements approved pricing calculation and override precedence rules:
 * Seasonal override > Weekend override > Base nightly rate.
 *
 * <p>All currency calculations strictly use {@link BigDecimal} with scale 2 and {@link RoundingMode#HALF_UP}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingCalculatorService {

    private final RateOverrideRepository rateOverrideRepository;
    private final RoomTypeRepository roomTypeRepository;

    /**
     * Calculates calculated nightly rates for a room type across stay interval [checkIn, checkOut).
     * Check-out date is the departure day and is excluded from night count.
     */
    @Transactional(readOnly = true)
    public RoomTypePricingResponse calculatePricing(Long roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        if (checkOut.isBefore(checkIn) || checkOut.isEqual(checkIn)) {
            throw new InvalidRateDateRangeException("Check-out date must be strictly after check-in date");
        }

        RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Room type not found with ID: " + roomTypeId));

        // Inclusive night range is [checkIn, checkOut.minusDays(1)]
        LocalDate lastNight = checkOut.minusDays(1);
        List<RateOverride> overrides = rateOverrideRepository.findOverlappingOverrides(roomTypeId, checkIn, lastNight);

        List<NightlyRateDto> nightlyRates = new ArrayList<>();
        LocalDate current = checkIn;

        while (current.isBefore(checkOut)) {
            NightlyRateDto rate = determineEffectiveRateForDate(roomType, current, overrides);
            nightlyRates.add(rate);
            current = current.plusDays(1);
        }

        return RoomTypePricingResponse.builder()
                .roomTypeId(roomType.getId())
                .baseNightlyRate(roomType.getBaseNightlyRate().setScale(2, RoundingMode.HALF_UP))
                .nightlyRates(nightlyRates)
                .build();
    }

    /**
     * Applies rate precedence hierarchy for a single date:
     * 1. Seasonal override (matches date)
     * 2. Weekend override (if date is Friday or Saturday and weekend override exists)
     * 3. Base nightly rate
     */
    public NightlyRateDto determineEffectiveRateForDate(RoomType roomType, LocalDate date, List<RateOverride> overrides) {
        // 1. Seasonal override check
        for (RateOverride ro : overrides) {
            if ("SEASONAL".equalsIgnoreCase(ro.getOverrideType()) &&
                    !date.isBefore(ro.getStartDate()) && !date.isAfter(ro.getEndDate())) {
                return NightlyRateDto.builder()
                        .date(date)
                        .effectiveRate(ro.getNightlyRate().setScale(2, RoundingMode.HALF_UP))
                        .rateType("SEASONAL")
                        .build();
            }
        }

        // 2. Weekend override check (Friday or Saturday night)
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.FRIDAY || dow == DayOfWeek.SATURDAY) {
            for (RateOverride ro : overrides) {
                if ("WEEKEND".equalsIgnoreCase(ro.getOverrideType()) &&
                        !date.isBefore(ro.getStartDate()) && !date.isAfter(ro.getEndDate())) {
                    return NightlyRateDto.builder()
                            .date(date)
                            .effectiveRate(ro.getNightlyRate().setScale(2, RoundingMode.HALF_UP))
                            .rateType("WEEKEND")
                            .build();
                }
            }
        }

        // 3. Fallback to base rate
        return NightlyRateDto.builder()
                .date(date)
                .effectiveRate(roomType.getBaseNightlyRate().setScale(2, RoundingMode.HALF_UP))
                .rateType("BASE")
                .build();
    }

    /**
     * Creates a new rate override for a room type.
     */
    @Transactional
    public RateOverrideDto createRateOverride(Long roomTypeId, CreateRateOverrideRequest request) {
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new InvalidRateDateRangeException("End date cannot be before start date");
        }

        RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Room type not found with ID: " + roomTypeId));

        RateOverride override = RateOverride.builder()
                .roomType(roomType)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .overrideType(request.getOverrideType().toUpperCase())
                .nightlyRate(request.getNightlyRate().setScale(2, RoundingMode.HALF_UP))
                .build();

        RateOverride saved = rateOverrideRepository.save(override);
        log.info("Created {} rate override ID {} for roomType {}", saved.getOverrideType(), saved.getId(), roomTypeId);

        return RateOverrideDto.builder()
                .id(saved.getId())
                .roomTypeId(roomType.getId())
                .startDate(saved.getStartDate())
                .endDate(saved.getEndDate())
                .overrideType(saved.getOverrideType())
                .nightlyRate(saved.getNightlyRate())
                .build();
    }
}
