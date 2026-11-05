package com.stayease.reservation.dto;

import com.stayease.reservation.enums.PaymentMethod;
import com.stayease.reservation.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoicePaymentDetailsDto {
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private String transactionId;
}
