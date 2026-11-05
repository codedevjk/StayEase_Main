package com.stayease.property.util;

import com.stayease.property.client.UserServiceClient;
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

    @Autowired
    private MessageSource messageSource;

    @Test
    @DisplayName("Should resolve validation messages for hotel and room type creation")
    void shouldResolvePropertyValidationMessages() {
        String hotelNameReq = messageSource.getMessage("NotBlank.createHotelRequest.name", null, Locale.getDefault());
        assertEquals("Hotel name is required", hotelNameReq);

        String cityReq = messageSource.getMessage("NotBlank.createHotelRequest.city", null, Locale.getDefault());
        assertEquals("City is required", cityReq);

        String starRatingMin = messageSource.getMessage("Min.createHotelRequest.starRating", null, Locale.getDefault());
        assertEquals("Star rating must be between 1 and 5", starRatingMin);

        String roomTypeNameReq = messageSource.getMessage("NotBlank.createRoomTypeRequest.name", null, Locale.getDefault());
        assertEquals("Room type name is required", roomTypeNameReq);

        String basePriceReq = messageSource.getMessage("NotNull.createRoomTypeRequest.basePrice", null, Locale.getDefault());
        assertEquals("Base price is required", basePriceReq);
    }

    @Test
    @DisplayName("Should resolve business error messages for property exceptions")
    void shouldResolveBusinessErrorMessages() {
        String hotelNotFound = messageSource.getMessage("hotel.not.found", null, Locale.getDefault());
        assertEquals("Hotel not found", hotelNotFound);

        String roomTypeNotFound = messageSource.getMessage("room.type.not.found", null, Locale.getDefault());
        assertEquals("Room type not found", roomTypeNotFound);

        String noCleanRoom = messageSource.getMessage("clean.room.not.available", null, Locale.getDefault());
        assertEquals("No clean operational room currently available for check-in", noCleanRoom);

        String duplicateRoom = messageSource.getMessage("room.number.duplicate", null, Locale.getDefault());
        assertEquals("Physical room number already exists in this hotel", duplicateRoom);
    }

    @Test
    @DisplayName("Should handle missing keys safely with fallback default message")
    void shouldHandleMissingKeysWithFallback() {
        String defaultMsg = messageSource.getMessage("unknown.property.code", null, "Default Property Msg", Locale.getDefault());
        assertEquals("Default Property Msg", defaultMsg);

        assertThrows(NoSuchMessageException.class, () ->
                messageSource.getMessage("non.existent.property.key", null, Locale.getDefault())
        );
    }
}
