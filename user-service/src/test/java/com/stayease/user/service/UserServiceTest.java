package com.stayease.user.service;

import com.stayease.user.dto.*;
import com.stayease.user.entity.User;
import com.stayease.user.exception.DuplicateEmailException;
import com.stayease.user.exception.InvalidCredentialsException;
import com.stayease.user.exception.ResourceNotFoundException;
import com.stayease.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserAuditService auditService;

    @InjectMocks
    private UserService userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(101L)
                .fullName("Jane Doe")
                .email("jane.doe@example.com")
                .phone("9876543210")
                .address("123 Main St, Bangalore")
                .passwordHash("$2a$12$sampleHashedPassword")
                .role("ROLE_USER")
                .tokenVersion(1L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should retrieve user profile")
    void shouldRetrieveUserProfile() {
        when(userRepository.findById(101L)).thenReturn(Optional.of(sampleUser));

        UserProfileDto profile = userService.getProfile(101L);

        assertThat(profile.id()).isEqualTo(101L);
        assertThat(profile.email()).isEqualTo("jane.doe@example.com");
        assertThat(profile.tokenVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when user does not exist")
    void shouldThrowExceptionWhenUserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getProfile(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999");
    }

    @Test
    @DisplayName("Should update user profile and record audit event")
    void shouldUpdateProfileAndRecordAudit() {
        UpdateProfileRequest request = new UpdateProfileRequest("Jane Smith", "9876543219", "456 New Road");
        when(userRepository.findById(101L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserProfileDto updated = userService.updateProfile(101L, request);

        assertThat(updated.fullName()).isEqualTo("Jane Smith");
        assertThat(updated.phone()).isEqualTo("9876543219");
        assertThat(updated.address()).isEqualTo("456 New Road");

        verify(auditService).recordEvent(
                eq(101L),
                eq("USER"),
                eq("ACCOUNT"),
                eq("PROFILE_UPDATED"),
                eq("101"),
                eq("User profile updated"),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Email update requires correct current password; invalid password throws exception")
    void shouldRejectEmailUpdateOnWrongPassword() {
        UpdateEmailRequest request = new UpdateEmailRequest("WrongPassword", "new.email@example.com");
        when(userRepository.findById(101L)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("WrongPassword", sampleUser.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> userService.updateEmail(101L, request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid current password");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Email update throws DuplicateEmailException when new email is already in use")
    void shouldRejectEmailUpdateOnDuplicateEmail() {
        UpdateEmailRequest request = new UpdateEmailRequest("CorrectPassword", "taken@example.com");
        when(userRepository.findById(101L)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("CorrectPassword", sampleUser.getPasswordHash())).thenReturn(true);
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.updateEmail(101L, request))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessageContaining("taken@example.com");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("SEC-05: Successful email update must increment tokenVersion to revoke active sessions")
    void shouldIncrementTokenVersionOnEmailUpdate() {
        UpdateEmailRequest request = new UpdateEmailRequest("CorrectPassword", "jane.new@example.com");
        when(userRepository.findById(101L)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("CorrectPassword", sampleUser.getPasswordHash())).thenReturn(true);
        when(userRepository.existsByEmail("jane.new@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserProfileDto updated = userService.updateEmail(101L, request);

        assertThat(updated.email()).isEqualTo("jane.new@example.com");
        assertThat(updated.tokenVersion()).isEqualTo(2L); // Incremented from 1L

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getTokenVersion()).isEqualTo(2L);

        // Verify audit event recorded without password
        verify(auditService).recordEvent(
                eq(101L),
                eq("USER"),
                eq("AUTH"),
                eq("EMAIL_CHANGED"),
                eq("101"),
                contains("jane.new@example.com"),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("SEC-06: Admin password reset must generate temporary password, hash it, increment tokenVersion, and exclude credential from audit")
    void shouldResetPasswordAndIncrementTokenVersionSafely() {
        when(userRepository.findById(101L)).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$12$newHashedTempPassword");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        AdminPasswordResetResponse response = userService.adminResetPassword(1L, 101L);

        assertThat(response.userId()).isEqualTo(101L);
        assertThat(response.temporaryPassword()).isNotBlank();
        assertThat(response.temporaryPassword().length()).isGreaterThanOrEqualTo(12);
        assertThat(response.message()).contains("Temporary password generated");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();
        assertThat(savedUser.getTokenVersion()).isEqualTo(2L); // Incremented from 1L
        assertThat(savedUser.getPasswordHash()).isEqualTo("$2a$12$newHashedTempPassword");

        // Verify audit event does NOT record the temporary password
        verify(auditService).recordEvent(
                eq(1L),
                eq("ADMIN"),
                eq("AUTH"),
                eq("ADMIN_PASSWORD_RESET"),
                eq("101"),
                contains("Admin reset password for user ID 101"),
                isNull(),
                isNull()
        );
    }

    @Test
    @DisplayName("Admin user search should return paginated list")
    void shouldReturnPaginatedUserSearch() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(sampleUser), pageable, 1);
        when(userRepository.searchUsers("Jane", pageable)).thenReturn(page);

        Page<AdminUserSummaryDto> results = userService.adminSearchUsers("Jane", pageable);

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).fullName()).isEqualTo("Jane Doe");
    }

    @Test
    @DisplayName("Should look up tokenVersion for internal service calls")
    void shouldReturnTokenVersion() {
        when(userRepository.findTokenVersionById(101L)).thenReturn(Optional.of(5L));

        Long version = userService.getTokenVersion(101L);
        assertThat(version).isEqualTo(5L);
    }
}
