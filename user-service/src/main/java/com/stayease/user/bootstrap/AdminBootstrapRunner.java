package com.stayease.user.bootstrap;

import com.stayease.user.entity.User;
import com.stayease.user.repository.UserRepository;
import com.stayease.user.service.UserAuditService;
import com.stayease.user.util.PasswordGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Admin bootstrap runner — seeds the initial ADMIN account safely.
 *
 * <h2>Safety Constraints</h2>
 * <ul>
 *   <li>Disabled by default. Activates ONLY when {@code stayease.admin-bootstrap.enabled=true}
 *       is explicitly provided in the local environment.</li>
 *   <li>Never enabled in CI, staging, or production environments.</li>
 *   <li>The generated one-time temporary password is printed strictly to standard output
 *       and is NEVER written to log files, audit tables, or committed to Git.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "stayease.admin-bootstrap.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class AdminBootstrapRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserAuditService auditService;

    @Value("${stayease.admin-bootstrap.admin-email:admin@stayease.local}")
    private String adminEmail;

    @Value("${stayease.admin-bootstrap.admin-full-name:StayEase Administrator}")
    private String adminFullName;

    @Value("${stayease.admin-bootstrap.admin-phone:9999999999}")
    private String adminPhone;

    @Value("${stayease.admin-bootstrap.admin-password:}")
    private String configuredPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.warn("AdminBootstrapRunner is ACTIVE.");
        log.warn("Disable stayease.admin-bootstrap.enabled immediately after execution.");
        log.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        String targetEmail = adminEmail.toLowerCase().trim();
        Optional<User> existing = userRepository.findByEmail(targetEmail);
        if (existing.isPresent()) {
            log.warn("Administrator account with email '{}' already exists (ID: {}). Bootstrap aborted.",
                    targetEmail, existing.get().getId());
            return;
        }

        String rawPassword;
        if (configuredPassword != null && !configuredPassword.isBlank()) {
            rawPassword = configuredPassword.trim();
        } else {
            rawPassword = PasswordGenerator.generatePassword(16);
        }

        String passwordHash = passwordEncoder.encode(rawPassword);

        User admin = User.builder()
                .fullName(adminFullName)
                .email(targetEmail)
                .phone(adminPhone)
                .address("StayEase Headquarters")
                .passwordHash(passwordHash)
                .role("ROLE_ADMIN")
                .tokenVersion(1L)
                .build();

        User savedAdmin = userRepository.save(admin);

        // Record audit event without the password
        auditService.recordEvent(
                0L,
                "SYSTEM",
                "AUTH",
                "ADMIN_BOOTSTRAP",
                String.valueOf(savedAdmin.getId()),
                "Initial administrator seeded via AdminBootstrapRunner",
                null,
                null
        );

        // Disclose password ONLY to stdout (never to logger/files)
        System.out.println("================================================================================");
        System.out.println("=== STAYEASE ADMIN BOOTSTRAP SUCCESSFUL");
        System.out.println("=== Admin Account Created: " + targetEmail + " (User ID: " + savedAdmin.getId() + ")");
        System.out.println("=== Role: ROLE_ADMIN");
        System.out.println("=== One-Time Bootstrap Password: " + rawPassword);
        System.out.println("=== CAUTION: Record this password securely now. It will never be displayed again.");
        System.out.println("=== Action Required: Set stayease.admin-bootstrap.enabled=false and restart.");
        System.out.println("================================================================================");
    }
}
