package com.stayease.reservation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stayease.reservation.client.PropertyNightlyRateDto;
import com.stayease.reservation.dto.PricingSnapshotDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PricingServiceTest {

    private PricingService pricingService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        pricingService = new PricingService(new BigDecimal("12.00"), objectMapper);
    }

    @Test
    @DisplayName("Verify pricing snapshot calculates subtotal, 12% tax, and total with HALF_UP rounding")
    void testCalculatePricingSnapshot() {
        List<PropertyNightlyRateDto> rates = List.of(
                PropertyNightlyRateDto.builder()
                        .date(LocalDate.of(2026, 11, 1))
                        .effectiveRate(new BigDecimal("4500.00"))
                        .rateType("BASE")
                        .build(),
                PropertyNightlyRateDto.builder()
                        .date(LocalDate.of(2026, 11, 2))
                        .effectiveRate(new BigDecimal("5200.00"))
                        .rateType("WEEKEND")
                        .build()
        );

        PricingSnapshotDto snapshot = pricingService.calculatePricingSnapshot(rates);

        assertNotNull(snapshot);
        assertEquals(new BigDecimal("9700.00"), snapshot.getLodgingSubtotal());
        assertEquals(new BigDecimal("12.00"), snapshot.getTaxPercentage());
        assertEquals(new BigDecimal("1164.00"), snapshot.getTaxAmount());
        assertEquals(new BigDecimal("10864.00"), snapshot.getTotalAmount());
        assertEquals(2, snapshot.getNightlyRates().size());
    }

    @Test
    @DisplayName("Verify monetary precision and rounding on fractional rates")
    void testRoundingPrecision() {
        // 1999.95 * 12% = 239.994 -> 240.00 with HALF_UP
        List<PropertyNightlyRateDto> rates = List.of(
                PropertyNightlyRateDto.builder()
                        .date(LocalDate.of(2026, 11, 1))
                        .effectiveRate(new BigDecimal("1999.95"))
                        .rateType("BASE")
                        .build()
        );

        PricingSnapshotDto snapshot = pricingService.calculatePricingSnapshot(rates);

        assertEquals(new BigDecimal("1999.95"), snapshot.getLodgingSubtotal());
        // 1999.95 * 12 / 100 = 239.994 -> rounds down to 239.99 with HALF_UP
        assertEquals(new BigDecimal("239.99"), snapshot.getTaxAmount());
        assertEquals(new BigDecimal("2239.94"), snapshot.getTotalAmount());

        // 1999.96 * 12 / 100 = 239.9952 -> rounds UP to 240.00 with HALF_UP
        List<PropertyNightlyRateDto> ratesRoundUp = List.of(
                PropertyNightlyRateDto.builder()
                        .date(LocalDate.of(2026, 11, 1))
                        .effectiveRate(new BigDecimal("1999.96"))
                        .rateType("BASE")
                        .build()
        );
        PricingSnapshotDto snapshotRoundUp = pricingService.calculatePricingSnapshot(ratesRoundUp);
        assertEquals(new BigDecimal("240.00"), snapshotRoundUp.getTaxAmount());
        assertEquals(new BigDecimal("2239.96"), snapshotRoundUp.getTotalAmount());
    }

    @Test
    @DisplayName("Verify serialization and deserialization of pricing snapshot")
    void testSerializeAndDeserializeSnapshot() {
        List<PropertyNightlyRateDto> rates = List.of(
                PropertyNightlyRateDto.builder()
                        .date(LocalDate.of(2026, 11, 1))
                        .effectiveRate(new BigDecimal("4500.00"))
                        .rateType("BASE")
                        .build()
        );

        PricingSnapshotDto original = pricingService.calculatePricingSnapshot(rates);
        String json = pricingService.serializeSnapshot(original);
        assertNotNull(json);

        PricingSnapshotDto deserialized = pricingService.deserializeSnapshot(json);
        assertEquals(original.getLodgingSubtotal(), deserialized.getLodgingSubtotal());
        assertEquals(original.getTotalAmount(), deserialized.getTotalAmount());
        assertEquals(original.getTaxAmount(), deserialized.getTaxAmount());
        assertEquals(1, deserialized.getNightlyRates().size());
        assertEquals(original.getNightlyRates().get(0).getDate(), deserialized.getNightlyRates().get(0).getDate());
    }

    @Test
    @DisplayName("Verify one night cancellation penalty calculates 1st night rate + 12% tax")
    void testCalculateOneNightPenalty() {
        List<PropertyNightlyRateDto> rates = List.of(
                PropertyNightlyRateDto.builder()
                        .date(LocalDate.of(2026, 11, 1))
                        .effectiveRate(new BigDecimal("4500.00"))
                        .rateType("BASE")
                        .build(),
                PropertyNightlyRateDto.builder()
                        .date(LocalDate.of(2026, 11, 2))
                        .effectiveRate(new BigDecimal("5200.00"))
                        .rateType("WEEKEND")
                        .build()
        );

        PricingSnapshotDto snapshot = pricingService.calculatePricingSnapshot(rates);
        BigDecimal penalty = pricingService.calculateOneNightPenalty(snapshot);

        // 4500 + 4500 * 0.12 = 4500 + 540 = 5040.00
        assertEquals(new BigDecimal("5040.00"), penalty);
    }
}
