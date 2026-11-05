package com.stayease.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancellationSummaryDto {
    private Long reservationId;
    private String status;
    private Instant cancelledAt;
    private Double hoursBeforeCheckIn;
    private BigDecimal totalPaid;
    private BigDecimal cancellationPenalty;
    private BigDecimal refundAmount;
    private String refundStatus;
}
