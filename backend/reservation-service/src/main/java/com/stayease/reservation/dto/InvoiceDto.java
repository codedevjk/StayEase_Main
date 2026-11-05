package com.stayease.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceDto {
    private String invoiceNumber;
    private Instant invoiceDate;
    private String reservationReference;
    private InvoiceHotelDto hotel;
    private InvoiceGuestDto guest;
    private InvoiceStayDto stay;
    private InvoiceChargesDto charges;
    private InvoicePaymentDetailsDto paymentDetails;
}
