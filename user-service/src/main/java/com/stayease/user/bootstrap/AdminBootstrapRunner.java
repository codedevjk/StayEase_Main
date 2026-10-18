package com.stayease.user.bootstrap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Admin bootstrap runner — seeds the initial ADMIN account.
 *
 * <h2>Safety Constraints</h2>
 * <ul>
 *   <li>This component is disabled by default and will NOT activate unless
 *       the property {@code stayease.admin-bootstrap.enabled=true} is
 *       explicitly set in the local environment or application-local.yml.</li>
 *   <li>It must NEVER be enabled in CI, staging, or production environments.</li>
 *   <li>SQL seed files must NOT contain a reusable plaintext password.</li>
 *   <li>The generated temporary password is printed ONCE to stdout and NEVER
 *       written to log files, audit records, or source control.</li>
 * </ul>
 *
 * <h2>Usage (Local Development Only)</h2>
 * <ol>
 *   <li>Copy {@code application-local.yml.example} to {@code application-local.yml}.</li>
 *   <li>Set {@code stayease.admin-bootstrap.enabled=true}.</li>
 *   <li>Set {@code stayease.admin-bootstrap.admin-email} to the desired admin email.</li>
 *   <li>Start user-service with the {@code local} Spring profile active.</li>
 *   <li>Capture the one-time password printed to console.</li>
 *   <li>Immediately set {@code enabled=false} and restart.</li>
 * </ol>
 *
 * <p><strong>Phase 1 status:</strong> Skeleton only.
 * Full implementation (BCrypt hash generation, admin persistence) is part of Phase 2.
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "stayease.admin-bootstrap.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class AdminBootstrapRunner implements ApplicationRunner {

    /*
     * Phase 2 will @Autowire UserRepository and PasswordEncoder here.
     * Keeping dependencies minimal in Phase 1 to avoid premature wiring.
     */

    @Override
    public void run(ApplicationArguments args) {
        log.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.warn("AdminBootstrapRunner is ACTIVE — Phase 1 skeleton only.");
        log.warn("Full implementation deferred to Phase 2.");
        log.warn("Disable stayease.admin-bootstrap.enabled after bootstrapping.");
        log.warn("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // TODO (Phase 2): Implement the following steps:
        //   1. Read admin email and full name from configuration properties.
        //   2. Check if an admin account already exists for that email.
        //   3. If not, generate a cryptographically secure temporary password using
        //      SecureRandom (never use Math.random or UUID for passwords).
        //   4. Hash the password using BCryptPasswordEncoder.
        //   5. Persist the User entity with ROLE_ADMIN and tokenVersion = 1.
        //   6. Print the one-time temporary password to System.out (NOT log.info,
        //      which goes to log files). Format:
        //        System.out.println("=== ADMIN BOOTSTRAP: one-time password: <password> ===");
        //   7. Clear the in-memory password reference.
        //   8. Throw if already existing admin detected to prevent accidental re-run.
    }
}
