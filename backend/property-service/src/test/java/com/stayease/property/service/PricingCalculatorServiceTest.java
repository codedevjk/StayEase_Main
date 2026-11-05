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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingCalculatorServiceTest {

    @Mock
    private RateOverrideRepository rateOverrideRepository;

    @Mock
    private RoomTypeRepository roomTypeRepository;

    @InjectMocks
    private PricingCalculatorService pricingCalculatorService;

    private RoomType sampleRoomType;

    @BeforeEach
    void setUp() {
        sampleRoomType = RoomType.builder()
                .id(10L)
                .name("Deluxe Ocean View")
                .baseNightlyRate(new BigDecimal("4000.00"))
                .totalPhysicalRooms(10)
                .build();
    }

    @Test
    @DisplayName("PRP-01: Seasonal override takes precedence over weekend override, which takes precedence over base rate")
    void shouldApplyRatePrecedenceHierarchy() {
        // 2026-11-05 (Thursday): Base rate
        // 2026-11-06 (Friday): Weekend override
        // 2026-11-07 (Saturday): Seasonal override (overlaps with weekend) -> Seasonal must win
        // 2026-11-08 (Sunday): Seasonal override -> Seasonal must win
        // 2026-11-09 (Monday): Base rate

        LocalDate checkIn = LocalDate.of(2026, 11, 5);
        LocalDate checkOut = LocalDate.of(2026, 11, 10); // 5 nights

        RateOverride weekendOverride = RateOverride.builder()
                .id(1L)
                .roomType(sampleRoomType)
                .startDate(LocalDate.of(2026, 11, 1))
                .endDate(LocalDate.of(2026, 11, 30))
                .overrideType("WEEKEND")
                .nightlyRate(new BigDecimal("5000.00"))
                .build();

        RateOverride seasonalOverride = RateOverride.builder()
                .id(2L)
                .roomType(sampleRoomType)
                .startDate(LocalDate.of(2026, 11, 7))
                .endDate(LocalDate.of(2026, 11, 8))
                .overrideType("SEASONAL")
                .nightlyRate(new BigDecimal("7000.00"))
                .build();

        when(roomTypeRepository.findById(10L)).thenReturn(Optional.of(sampleRoomType));
        when(rateOverrideRepository.findOverlappingOverrides(10L, checkIn, checkOut.minusDays(1)))
                .thenReturn(List.of(weekendOverride, seasonalOverride));

        RoomTypePricingResponse pricing = pricingCalculatorService.calculatePricing(10L, checkIn, checkOut);

        assertThat(pricing.getRoomTypeId()).isEqualTo(10L);
        assertThat(pricing.getBaseNightlyRate()).isEqualByComparingTo("4000.00");
        assertThat(pricing.getNightlyRates()).hasSize(5);

        // Night 1: 2026-11-05 (Thursday) -> BASE
        NightlyRateDto night1 = pricing.getNightlyRates().get(0);
        assertThat(night1.getDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(night1.getRateType()).isEqualTo("BASE");
        assertThat(night1.getEffectiveRate()).isEqualByComparingTo("4000.00");

        // Night 2: 2026-11-06 (Friday) -> WEEKEND
        NightlyRateDto night2 = pricing.getNightlyRates().get(1);
        assertThat(night2.getDate()).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(night2.getRateType()).isEqualTo("WEEKEND");
        assertThat(night2.getEffectiveRate()).isEqualByComparingTo("5000.00");

        // Night 3: 2026-11-07 (Saturday) -> SEASONAL overrides WEEKEND
        NightlyRateDto night3 = pricing.getNightlyRates().get(2);
        assertThat(night3.getDate()).isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(night3.getRateType()).isEqualTo("SEASONAL");
        assertThat(night3.getEffectiveRate()).isEqualByComparingTo("7000.00");

        // Night 4: 2026-11-08 (Sunday) -> SEASONAL
        NightlyRateDto night4 = pricing.getNightlyRates().get(3);
        assertThat(night4.getDate()).isEqualTo(LocalDate.of(2026, 11, 8));
        assertThat(night4.getRateType()).isEqualTo("SEASONAL");
        assertThat(night4.getEffectiveRate()).isEqualByComparingTo("7000.00");

        // Night 5: 2026-11-09 (Monday) -> BASE
        NightlyRateDto night5 = pricing.getNightlyRates().get(4);
        assertThat(night5.getDate()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(night5.getRateType()).isEqualTo("BASE");
        assertThat(night5.getEffectiveRate()).isEqualByComparingTo("4000.00");
    }

    @Test
    @DisplayName("PRP-02: Rates must be rounded with HALF_UP to 2 decimal places")
    void shouldFormatMonetaryValuesToTwoDecimalPlaces() {
        LocalDate checkIn = LocalDate.of(2026, 11, 1);
        LocalDate checkOut = LocalDate.of(2026, 11, 2);

        sampleRoomType.setBaseNightlyRate(new BigDecimal("3333.3333"));
        when(roomTypeRepository.findById(10L)).thenReturn(Optional.of(sampleRoomType));
        when(rateOverrideRepository.findOverlappingOverrides(any(), any(), any())).thenReturn(List.of());

        RoomTypePricingResponse pricing = pricingCalculatorService.calculatePricing(10L, checkIn, checkOut);

        assertThat(pricing.getBaseNightlyRate().scale()).isEqualTo(2);
        assertThat(pricing.getBaseNightlyRate()).isEqualByComparingTo("3333.33");
        assertThat(pricing.getNightlyRates().get(0).getEffectiveRate().scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should reject pricing calculation when check-out is on or before check-in")
    void shouldRejectInvalidStayInterval() {
        LocalDate checkIn = LocalDate.of(2026, 11, 5);
        LocalDate checkOutSameDay = LocalDate.of(2026, 11, 5);
        LocalDate checkOutEarlier = LocalDate.of(2026, 11, 4);

        assertThatThrownBy(() -> pricingCalculatorService.calculatePricing(10L, checkIn, checkOutSameDay))
                .isInstanceOf(InvalidRateDateRangeException.class);

        assertThatThrownBy(() -> pricingCalculatorService.calculatePricing(10L, checkIn, checkOutEarlier))
                .isInstanceOf(InvalidRateDateRangeException.class);
    }

    @Test
    @DisplayName("Should reject pricing calculation when room type does not exist")
    void shouldThrowNotFoundWhenRoomTypeMissing() {
        LocalDate checkIn = LocalDate.of(2026, 11, 1);
        LocalDate checkOut = LocalDate.of(2026, 11, 3);

        when(roomTypeRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pricingCalculatorService.calculatePricing(999L, checkIn, checkOut))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Room type not found with ID: 999");
    }

    @Test
    @DisplayName("Should create rate override with validated dates and half-up rounding")
    void shouldCreateRateOverrideSuccessfully() {
        CreateRateOverrideRequest request = CreateRateOverrideRequest.builder()
                .startDate(LocalDate.of(2026, 12, 20))
                .endDate(LocalDate.of(2027, 1, 5))
                .overrideType("SEASONAL")
                .nightlyRate(new BigDecimal("7500.555"))
                .build();

        when(roomTypeRepository.findById(10L)).thenReturn(Optional.of(sampleRoomType));
        when(rateOverrideRepository.save(any(RateOverride.class))).thenAnswer(invocation -> {
            RateOverride ro = invocation.getArgument(0);
            ro.setId(50L);
            return ro;
        });

        RateOverrideDto dto = pricingCalculatorService.createRateOverride(10L, request);

        assertThat(dto.getId()).isEqualTo(50L);
        assertThat(dto.getRoomTypeId()).isEqualTo(10L);
        assertThat(dto.getOverrideType()).isEqualTo("SEASONAL");
        assertThat(dto.getNightlyRate()).isEqualByComparingTo("7500.56");
        verify(rateOverrideRepository).save(any(RateOverride.class));
    }

    @Test
    @DisplayName("Should reject rate override when endDate is before startDate")
    void shouldRejectRateOverrideWithInvertedDates() {
        CreateRateOverrideRequest request = CreateRateOverrideRequest.builder()
                .startDate(LocalDate.of(2026, 12, 20))
                .endDate(LocalDate.of(2026, 12, 10))
                .overrideType("SEASONAL")
                .nightlyRate(new BigDecimal("7500.00"))
                .build();

        assertThatThrownBy(() -> pricingCalculatorService.createRateOverride(10L, request))
                .isInstanceOf(InvalidRateDateRangeException.class);
    }
}
