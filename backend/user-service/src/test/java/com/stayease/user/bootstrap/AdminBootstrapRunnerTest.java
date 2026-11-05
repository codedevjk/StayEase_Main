package com.stayease.user.bootstrap;

import com.stayease.user.entity.User;
import com.stayease.user.repository.UserRepository;
import com.stayease.user.service.UserAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserAuditService auditService;

    @InjectMocks
    private AdminBootstrapRunner bootstrapRunner;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(bootstrapRunner, "adminEmail", "admin@stayease.local");
        ReflectionTestUtils.setField(bootstrapRunner, "adminFullName", "StayEase Administrator");
        ReflectionTestUtils.setField(bootstrapRunner, "adminPhone", "9999999999");
        ReflectionTestUtils.setField(bootstrapRunner, "configuredPassword", "");
    }

    @Test
    @DisplayName("Should skip bootstrap if administrator account already exists")
    void shouldSkipIfAdminAlreadyExists() {
        User existingAdmin = User.builder()
                .id(1L)
                .email("admin@stayease.local")
                .role("ROLE_ADMIN")
                .build();

        when(userRepository.findByEmail("admin@stayease.local")).thenReturn(Optional.of(existingAdmin));

        bootstrapRunner.run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
        verify(auditService, never()).recordEvent(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("SEC-08: Should seed initial administrator safely when not present")
    void shouldSeedAdminSafelyWhenNotPresent() {
        when(userRepository.findByEmail("admin@stayease.local")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$secureBcryptHash");
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(1L);
            return u;
        });

        bootstrapRunner.run(new DefaultApplicationArguments());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedAdmin = captor.getValue();

        assertThat(savedAdmin.getEmail()).isEqualTo("admin@stayease.local");
        assertThat(savedAdmin.getRole()).isEqualTo("ROLE_ADMIN");
        assertThat(savedAdmin.getTokenVersion()).isEqualTo(1L);
        assertThat(savedAdmin.getPasswordHash()).isEqualTo("$2a$12$secureBcryptHash");

        // Verify audit event recorded without plaintext password
        verify(auditService).recordEvent(
                eq(0L),
                eq("SYSTEM"),
                eq("AUTH"),
                eq("ADMIN_BOOTSTRAP"),
                eq("1"),
                eq("Initial administrator seeded via AdminBootstrapRunner"),
                isNull(),
                isNull()
        );
    }
}
