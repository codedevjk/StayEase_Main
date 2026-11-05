package com.stayease.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminDashboardDto {
    private Map<String, Long> reservationCountsByStatus;
    private Long todayCheckIns;
    private Long todayCheckOuts;
    private Double currentOccupancyRate;
    private Long pendingPaymentsCount;
    private BigDecimal totalRevenueCollected;
    private Long cancellationsCount;
    private Long noShowCount;
    private String partialDataWarning;
}
