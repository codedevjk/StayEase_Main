package com.stayease.user.service;

import com.stayease.user.dto.AuthResponse;
import com.stayease.user.dto.LoginRequest;
import com.stayease.user.dto.RegisterRequest;
import com.stayease.user.entity.User;
import com.stayease.user.exception.DuplicateEmailException;
import com.stayease.user.exception.InvalidCredentialsException;
import com.stayease.user.repository.UserRepository;
import com.stayease.user.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private UserAuditService auditService;

    @InjectMocks
    private AuthService authService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(101L)
                .fullName("Jane Doe")
                .email("jane.doe@example.com")
                .phone("9876543210")
                .address("123 Main St, Bangalore")
                .passwordHash("$2a$12$hashedPasswordExample")
                .role("ROLE_USER")
                .tokenVersion(1L)
                .build();
    }

    @Test
    @DisplayName("SEC-01: Public registration must strictly enforce ROLE_USER even if ADMIN is submitted")
    void shouldForceRoleUserOnRegistration() {
        RegisterRequest request = new RegisterRequest(
                "Attacker",
                "attacker@example.com",
                "9876543210",
                "Some Address",
                "SuperPassword@123",
                "ROLE_ADMIN" // Privilege escalation attempt
        );

        when(userRepository.existsByEmail("attacker@example.com")).thenReturn(false);
        when(passwordEncoder.encode("SuperPassword@123")).thenReturn("$2a$12$hashedAttackerPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(201L);
            return u;
        });
        when(tokenProvider.generateToken(any(User.class))).thenReturn("mock.jwt.token");

        AuthResponse response = authService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("mock.jwt.token");
        assertThat(response.user().role()).isEqualTo("ROLE_USER");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getRole()).isEqualTo("ROLE_USER");
        assertThat(savedUser.getPasswordHash()).isEqualTo("$2a$12$hashedAttackerPassword");
        assertThat(savedUser.getTokenVersion()).isEqualTo(1L);

        // Verify audit event recorded without password
        verify(auditService).recordEvent(
                eq(201L),
                eq("USER"),
                eq("AUTH"),
                eq("USER_REGISTERED"),
                eq("201"),
                eq("User registered successfully"),
                isNull(),
                any()
        );
    }

    @Test
    @DisplayName("SEC-02: Duplicate email registration should throw DuplicateEmailException (409 Conflict)")
    void shouldThrowDuplicateEmailExceptionWhenEmailExists() {
        RegisterRequest request = new RegisterRequest(
                "Jane Doe",
                "jane.doe@example.com",
                "9876543210",
                "123 Main St",
                "Password@123",
                null
        );

        when(userRepository.existsByEmail("jane.doe@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessageContaining("jane.doe@example.com");

        verify(userRepository, never()).save(any());
        verify(tokenProvider, never()).generateToken(any());
    }

    @Test
    @DisplayName("SEC-03: Login with non-existent email should throw generic InvalidCredentialsException")
    void shouldThrowGenericExceptionWhenUserNotFound() {
        LoginRequest request = new LoginRequest("unknown@example.com", "Password@123");
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("SEC-03: Login with incorrect password should throw generic InvalidCredentialsException")
    void shouldThrowGenericExceptionWhenPasswordMismatch() {
        LoginRequest request = new LoginRequest("jane.doe@example.com", "WrongPassword");
        when(userRepository.findByEmail("jane.doe@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("WrongPassword", sampleUser.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(tokenProvider, never()).generateToken(any());
    }

    @Test
    @DisplayName("Login with valid credentials should record audit and issue JWT")
    void shouldLoginSuccessfullyWithValidCredentials() {
        LoginRequest request = new LoginRequest("jane.doe@example.com", "Password@123");
        when(userRepository.findByEmail("jane.doe@example.com")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("Password@123", sampleUser.getPasswordHash())).thenReturn(true);
        when(tokenProvider.generateToken(sampleUser)).thenReturn("valid.jwt.token");

        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("valid.jwt.token");
        assertThat(response.user().id()).isEqualTo(101L);
        assertThat(response.user().email()).isEqualTo("jane.doe@example.com");

        verify(auditService).recordEvent(
                eq(101L),
                eq("USER"),
                eq("AUTH"),
                eq("USER_LOGIN"),
                eq("101"),
                eq("User logged in successfully"),
                isNull(),
                isNull()
        );
    }
}
