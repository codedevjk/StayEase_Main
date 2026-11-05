package com.stayease.reservation.util;

import com.stayease.reservation.client.PropertyServiceClient;
import com.stayease.reservation.client.UserServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class MessageResolutionTest {

    @MockBean
    private UserServiceClient userServiceClient;

    @MockBean
    private PropertyServiceClient propertyServiceClient;

    @Autowired
    private MessageSource messageSource;

    @Test
    @DisplayName("Should resolve validation messages for reservation and payment requests")
    void shouldResolveReservationValidationMessages() {
        String hotelIdReq = messageSource.getMessage("NotNull.createReservationRequest.hotelId", null, Locale.getDefault());
        assertEquals("Hotel ID is required", hotelIdReq);

        String roomTypeIdReq = messageSource.getMessage("NotNull.createReservationRequest.roomTypeId", null, Locale.getDefault());
        assertEquals("Room type ID is required", roomTypeIdReq);

        String checkInReq = messageSource.getMessage("NotNull.createReservationRequest.checkInDate", null, Locale.getDefault());
        assertEquals("Check-in date is required", checkInReq);

        String checkOutReq = messageSource.getMessage("NotNull.createReservationRequest.checkOutDate", null, Locale.getDefault());
        assertEquals("Check-out date is required", checkOutReq);

        String guestCountReq = messageSource.getMessage("NotNull.createReservationRequest.guestCount", null, Locale.getDefault());
        assertEquals("Guest count is required", guestCountReq);

        String guestCountMin = messageSource.getMessage("Min.createReservationRequest.guestCount", null, Locale.getDefault());
        assertEquals("Guest count must be at least 1", guestCountMin);

        String paymentPolicyReq = messageSource.getMessage("NotNull.createReservationRequest.paymentPolicy", null, Locale.getDefault());
        assertEquals("Payment policy is required", paymentPolicyReq);
    }

    @Test
    @DisplayName("Should resolve business error messages for reservation exceptions")
    void shouldResolveBusinessErrorMessages() {
        String resNotFound = messageSource.getMessage("reservation.not.found", null, Locale.getDefault());
        assertEquals("Reservation not found", resNotFound);

        String invInsufficient = messageSource.getMessage("inventory.insufficient", null, Locale.getDefault());
        assertEquals("Insufficient room inventory for requested dates", invInsufficient);

        String datesInvalid = messageSource.getMessage("reservation.dates.invalid", null, Locale.getDefault());
        assertEquals("Check-out date must be after check-in date", datesInvalid);

        String holdExpired = messageSource.getMessage("hold.expired", null, Locale.getDefault());
        assertEquals("Booking hold has expired", holdExpired);

        String paymentFailed = messageSource.getMessage("payment.failed", null, Locale.getDefault());
        assertEquals("Payment processing failed", paymentFailed);
    }

    @Test
    @DisplayName("Should handle missing keys safely with fallback default message")
    void shouldHandleMissingKeysWithFallback() {
        String defaultMsg = messageSource.getMessage("unknown.reservation.code", null, "Default Reservation Msg", Locale.getDefault());
        assertEquals("Default Reservation Msg", defaultMsg);

        assertThrows(NoSuchMessageException.class, () ->
                messageSource.getMessage("non.existent.reservation.key", null, Locale.getDefault())
        );
    }
}
