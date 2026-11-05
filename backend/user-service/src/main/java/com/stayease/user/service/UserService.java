package com.stayease.user.service;

import com.stayease.user.dto.*;
import com.stayease.user.entity.User;
import com.stayease.user.exception.DuplicateEmailException;
import com.stayease.user.exception.InvalidCredentialsException;
import com.stayease.user.exception.ResourceNotFoundException;
import com.stayease.user.repository.UserRepository;
import com.stayease.user.util.PasswordGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Service managing user profiles, email changes, token versioning, and administrative user operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserAuditService auditService;

    /**
     * Retrieves the profile for the authenticated user.
     */
    @Transactional(readOnly = true)
    public UserProfileDto getProfile(Long userId) {
        User user = getUserOrThrow(userId);
        return toProfileDto(user);
    }

    /**
     * Updates profile fields (name, phone, address) for the authenticated user.
     */
    @Transactional
    public UserProfileDto updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getUserOrThrow(userId);

        Map<String, Object> beforeState = Map.of(
                "fullName", user.getFullName(),
                "phone", user.getPhone(),
                "address", user.getAddress() != null ? user.getAddress() : ""
        );

        user.setFullName(request.fullName().trim());
        user.setPhone(request.phone().trim());
        user.setAddress(request.address() != null ? request.address().trim() : null);

        User savedUser = userRepository.save(user);

        Map<String, Object> afterState = Map.of(
                "fullName", savedUser.getFullName(),
                "phone", savedUser.getPhone(),
                "address", savedUser.getAddress() != null ? savedUser.getAddress() : ""
        );

        String actorRole = user.getRole().startsWith("ROLE_")
                ? user.getRole().substring(5)
                : user.getRole();

        auditService.recordEvent(
                userId,
                actorRole,
                "ACCOUNT",
                "PROFILE_UPDATED",
                String.valueOf(userId),
                "User profile updated",
                beforeState,
                afterState
        );

        return toProfileDto(savedUser);
    }

    /**
     * Changes user email address. Requires current password verification.
     * Increments tokenVersion, instantly invalidating active sessions.
     */
    @Transactional
    public UserProfileDto updateEmail(Long userId, UpdateEmailRequest request) {
        User user = getUserOrThrow(userId);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid current password");
        }

        String newEmail = request.newEmail().toLowerCase().trim();
        if (newEmail.equalsIgnoreCase(user.getEmail())) {
            return toProfileDto(user);
        }

        if (userRepository.existsByEmail(newEmail)) {
            throw new DuplicateEmailException(newEmail);
        }

        String oldEmail = user.getEmail();
        user.setEmail(newEmail);

        // Increment tokenVersion per technical decisions §7.2
        user.setTokenVersion(user.getTokenVersion() + 1);

        User savedUser = userRepository.save(user);

        String actorRole = user.getRole().startsWith("ROLE_")
                ? user.getRole().substring(5)
                : user.getRole();

        auditService.recordEvent(
                userId,
                actorRole,
                "AUTH",
                "EMAIL_CHANGED",
                String.valueOf(userId),
                "User email changed from " + oldEmail + " to " + newEmail,
                Map.of("email", oldEmail),
                Map.of("email", newEmail)
        );

        return toProfileDto(savedUser);
    }

    /**
     * Searches users for administrative management and customer support.
     */
    @Transactional(readOnly = true)
    public Page<AdminUserSummaryDto> adminSearchUsers(String query, Pageable pageable) {
        String trimmedQuery = (query != null && !query.isBlank()) ? query.trim() : null;
        Page<User> usersPage = userRepository.searchUsers(trimmedQuery, pageable);
        return usersPage.map(this::toAdminSummaryDto);
    }

    /**
     * Admin-assisted password reset. Generates a secure temporary password,
     * hashes with BCrypt, increments tokenVersion, and returns the one-time temporary password.
     */
    @Transactional
    public AdminPasswordResetResponse adminResetPassword(Long adminId, Long targetUserId) {
        User targetUser = getUserOrThrow(targetUserId);

        String temporaryPassword = PasswordGenerator.generatePassword(16);
        targetUser.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        targetUser.setTokenVersion(targetUser.getTokenVersion() + 1);

        userRepository.save(targetUser);

        // Audit event — NEVER log or record the temporary password
        auditService.recordEvent(
                adminId,
                "ADMIN",
                "AUTH",
                "ADMIN_PASSWORD_RESET",
                String.valueOf(targetUserId),
                "Admin reset password for user ID " + targetUserId,
                null,
                null
        );

        return new AdminPasswordResetResponse(
                targetUserId,
                temporaryPassword,
                "Temporary password generated. Disclose securely to user; will not be displayed again."
        );
    }

    /**
     * Returns the current tokenVersion for internal inter-service validation.
     */
    @Transactional(readOnly = true)
    public Long getTokenVersion(Long userId) {
        return userRepository.findTokenVersionById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }

    private UserProfileDto toProfileDto(User user) {
        return new UserProfileDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getAddress(),
                user.getRole(),
                user.getTokenVersion(),
                user.getCreatedAt()
        );
    }

    private AdminUserSummaryDto toAdminSummaryDto(User user) {
        return new AdminUserSummaryDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole(),
                user.getTokenVersion(),
                user.getCreatedAt()
        );
    }
}
