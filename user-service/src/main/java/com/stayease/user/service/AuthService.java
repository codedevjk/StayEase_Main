package com.stayease.user.service;

import com.stayease.user.dto.AuthResponse;
import com.stayease.user.dto.LoginRequest;
import com.stayease.user.dto.RegisterRequest;
import com.stayease.user.dto.UserSummaryDto;
import com.stayease.user.entity.User;
import com.stayease.user.exception.DuplicateEmailException;
import com.stayease.user.exception.InvalidCredentialsException;
import com.stayease.user.repository.UserRepository;
import com.stayease.user.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Service managing user authentication (registration and login).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final UserAuditService auditService;

    /**
     * Registers a new user account.
     *
     * <p>Security requirements:
     * <ul>
     *   <li>Enforces unique email; throws {@link DuplicateEmailException} if taken.</li>
     *   <li>BCrypt-hashes the submitted password.</li>
     *   <li>Forces role to {@code ROLE_USER}; rejects/ignores any attempted privilege escalation.</li>
     *   <li>Issues signed 15-minute JWT access token.</li>
     *   <li>Records audit event without recording sensitive credentials.</li>
     * </ul>
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(request.email());
        }

        // Force public registration to ROLE_USER regardless of payload
        String assignedRole = "ROLE_USER";

        User user = User.builder()
                .fullName(request.fullName())
                .email(request.email().toLowerCase().trim())
                .phone(request.phone().trim())
                .address(request.address())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(assignedRole)
                .tokenVersion(1L)
                .build();

        User savedUser = userRepository.save(user);

        // Record audit event
        auditService.recordEvent(
                savedUser.getId(),
                "USER",
                "AUTH",
                "USER_REGISTERED",
                String.valueOf(savedUser.getId()),
                "User registered successfully",
                null,
                Map.of(
                        "id", savedUser.getId(),
                        "email", savedUser.getEmail(),
                        "fullName", savedUser.getFullName(),
                        "role", savedUser.getRole()
                )
        );

        String token = tokenProvider.generateToken(savedUser);
        UserSummaryDto summary = new UserSummaryDto(
                savedUser.getId(),
                savedUser.getFullName(),
                savedUser.getEmail(),
                savedUser.getPhone(),
                savedUser.getRole()
        );

        return new AuthResponse(token, summary);
    }

    /**
     * Authenticates credentials and issues a signed JWT access token.
     * Uses generic failure messages to prevent account enumeration.
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.email().toLowerCase().trim();

        User user = userRepository.findByEmail(email)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String actorRole = user.getRole().startsWith("ROLE_")
                ? user.getRole().substring(5)
                : user.getRole();

        // Record audit event
        auditService.recordEvent(
                user.getId(),
                actorRole,
                "AUTH",
                "USER_LOGIN",
                String.valueOf(user.getId()),
                "User logged in successfully",
                null,
                null
        );

        String token = tokenProvider.generateToken(user);
        UserSummaryDto summary = new UserSummaryDto(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole()
        );

        return new AuthResponse(token, summary);
    }
}
