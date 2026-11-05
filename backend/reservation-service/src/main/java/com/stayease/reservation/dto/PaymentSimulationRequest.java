package com.stayease.reservation.dto;

import com.stayease.reservation.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentSimulationRequest {

    @NotNull(message = "{NotNull.paymentSimulationRequest.paymentMethod}")
    private PaymentMethod paymentMethod;

    @NotNull(message = "{NotNull.paymentSimulationRequest.simulationOutcome}")
    private SimulationOutcome simulationOutcome;
}
