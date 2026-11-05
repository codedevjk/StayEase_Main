package com.stayease.user.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class MessageResolutionTest {

    @Autowired
    private MessageSource messageSource;

    @Test
    @DisplayName("Should resolve validation messages for registration from message properties")
    void shouldResolveRegistrationValidationMessages() {
        String emailRequired = messageSource.getMessage("NotBlank.registerRequest.email", null, Locale.getDefault());
        assertEquals("Email is required", emailRequired);

        String emailInvalid = messageSource.getMessage("Email.registerRequest.email", null, Locale.getDefault());
        assertEquals("Email format is invalid", emailInvalid);

        String passSize = messageSource.getMessage("Size.registerRequest.password", null, Locale.getDefault());
        assertEquals("Password must be at least 8 characters", passSize);
    }

    @Test
    @DisplayName("Should resolve business exception error messages from message properties")
    void shouldResolveBusinessErrorMessages() {
        String userNotFound = messageSource.getMessage("user.not.found", null, Locale.getDefault());
        assertEquals("User not found", userNotFound);

        String duplicateEmail = messageSource.getMessage("user.email.duplicate", null, Locale.getDefault());
        assertEquals("Email is already registered", duplicateEmail);

        String invalidCreds = messageSource.getMessage("user.credentials.invalid", null, Locale.getDefault());
        assertEquals("Invalid email or password", invalidCreds);
    }

    @Test
    @DisplayName("Should handle missing keys safely with fallback default message")
    void shouldHandleMissingKeysWithFallback() {
        String defaultMsg = messageSource.getMessage("unknown.key.code", null, "Default Fallback", Locale.getDefault());
        assertEquals("Default Fallback", defaultMsg);

        assertThrows(NoSuchMessageException.class, () ->
                messageSource.getMessage("non.existent.key", null, Locale.getDefault())
        );
    }
}
