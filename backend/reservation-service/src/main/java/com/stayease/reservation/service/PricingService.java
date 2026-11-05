package com.stayease.reservation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stayease.reservation.client.PropertyNightlyRateDto;
import com.stayease.reservation.dto.NightlyRateSnapshotDto;
import com.stayease.reservation.dto.PricingSnapshotDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class PricingService {

    private final BigDecimal taxPercentage;
    private final ObjectMapper objectMapper;

    public PricingService(
            @Value("${stayease.pricing.tax-percentage:12.00}") BigDecimal taxPercentage,
            ObjectMapper objectMapper) {
        this.taxPercentage = taxPercentage.setScale(2, RoundingMode.HALF_UP);
        this.objectMapper = objectMapper;
    }

    public BigDecimal getTaxPercentage() {
        return taxPercentage;
    }

    public PricingSnapshotDto calculatePricingSnapshot(List<PropertyNightlyRateDto> nightlyRates) {
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<NightlyRateSnapshotDto> rateSnapshots = new ArrayList<>();

        for (PropertyNightlyRateDto nr : nightlyRates) {
            BigDecimal rate = nr.getEffectiveRate().setScale(2, RoundingMode.HALF_UP);
            subtotal = subtotal.add(rate);
            rateSnapshots.add(NightlyRateSnapshotDto.builder()
                    .date(nr.getDate())
                    .rate(rate)
                    .rateType(nr.getRateType())
                    .build());
        }

        BigDecimal taxAmount = subtotal
                .multiply(taxPercentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal totalAmount = subtotal.add(taxAmount).setScale(2, RoundingMode.HALF_UP);

        return PricingSnapshotDto.builder()
                .lodgingSubtotal(subtotal)
                .taxPercentage(taxPercentage)
                .taxAmount(taxAmount)
                .totalAmount(totalAmount)
                .nightlyRates(rateSnapshots)
                .build();
    }

    public String serializeSnapshot(PricingSnapshotDto snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize pricing snapshot: {}", e.getMessage());
            throw new RuntimeException("Failed to serialize pricing snapshot", e);
        }
    }

    public PricingSnapshotDto deserializeSnapshot(String json) {
        try {
            return objectMapper.readValue(json, PricingSnapshotDto.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize pricing snapshot: {}", e.getMessage());
            throw new RuntimeException("Failed to deserialize pricing snapshot", e);
        }
    }

    /**
     * Calculates the penalty for late cancellation (< 24 hours before check-in):
     * Night 1 effective rate + applicable tax on Night 1 (HALF_UP).
     */
    public BigDecimal calculateOneNightPenalty(PricingSnapshotDto snapshot) {
        if (snapshot == null || snapshot.getNightlyRates() == null || snapshot.getNightlyRates().isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal night1Rate = snapshot.getNightlyRates().get(0).getRate().setScale(2, RoundingMode.HALF_UP);
        BigDecimal night1Tax = night1Rate
                .multiply(taxPercentage)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        return night1Rate.add(night1Tax).setScale(2, RoundingMode.HALF_UP);
    }
}
