package com.stayease.user.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordGeneratorTest {

    @Test
    @DisplayName("Generated password should meet complexity rules: length >= 12, uppercase, lowercase, digits, symbols")
    void shouldMeetComplexityRequirements() {
        String password = PasswordGenerator.generatePassword(16);

        assertThat(password).hasSize(16);
        assertThat(password).matches(".*[A-Z].*");
        assertThat(password).matches(".*[a-z].*");
        assertThat(password).matches(".*[0-9].*");
        assertThat(password).matches(".*[!@#$%^&*()\\-_=+].*");
    }

    @Test
    @DisplayName("Enforces minimum length of 12 even if requested length is shorter")
    void shouldEnforceMinimumLengthOf12() {
        String password = PasswordGenerator.generatePassword(6);
        assertThat(password).hasSize(12);
    }

    @Test
    @DisplayName("Generates distinct random passwords across invocations")
    void shouldGenerateDistinctPasswords() {
        Set<String> passwords = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            passwords.add(PasswordGenerator.generatePassword(16));
        }
        assertThat(passwords).hasSize(50);
    }
}
