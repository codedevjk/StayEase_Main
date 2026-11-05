package com.stayease.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stayease.user.dto.LoginRequest;
import com.stayease.user.dto.RegisterRequest;
import com.stayease.user.dto.UpdateEmailRequest;
import com.stayease.user.dto.UpdateProfileRequest;
import com.stayease.user.entity.User;
import com.stayease.user.repository.UserAuditEventRepository;
import com.stayease.user.repository.UserRepository;
import com.stayease.user.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprehensive integration and security test suite for StayEase User Service.
 *
 * <p>Executes against in-memory H2 database under active "test" profile.
 * Verifies all API contracts, security boundaries, token revocation,
 * and audit persistence.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAuditEventRepository auditRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;
    private User testAdmin;
    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        auditRepository.deleteAll();
        userRepository.deleteAll();

        // Seed regular user
        testUser = userRepository.save(User.builder()
                .fullName("John Guest")
                .email("john.guest@example.com")
                .phone("9876543210")
                .address("100 MG Road, Bangalore")
                .passwordHash(passwordEncoder.encode("Password@123"))
                .role("ROLE_USER")
                .tokenVersion(1L)
                .build());

        // Seed administrator
        testAdmin = userRepository.save(User.builder()
                .fullName("StayEase Administrator")
                .email("admin@stayease.local")
                .phone("9999999999")
                .address("HQ Bangalore")
                .passwordHash(passwordEncoder.encode("AdminPassword@123"))
                .role("ROLE_ADMIN")
                .tokenVersion(1L)
                .build());

        userToken = jwtTokenProvider.generateToken(testUser);
        adminToken = jwtTokenProvider.generateToken(testAdmin);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Registration Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/register: Successful registration with BCrypt hashing and forced ROLE_USER")
    void shouldRegisterNewUserSuccessfully() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "Alice Smith",
                "alice.smith@example.com",
                "9876543211",
                "200 Indiranagar, Bangalore",
                "SecurePassword@123",
                "ROLE_ADMIN" // Privilege escalation attempt
        );

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .header("X-Correlation-ID", "corr-reg-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.fullName").value("Alice Smith"))
                .andExpect(jsonPath("$.user.email").value("alice.smith@example.com"))
                .andExpect(jsonPath("$.user.role").value("ROLE_USER")) // SEC-01 verified
                .andReturn();

        // Verify database persistence
        User persisted = userRepository.findByEmail("alice.smith@example.com").orElseThrow();
        assertThat(persisted.getRole()).isEqualTo("ROLE_USER");
        assertThat(persisted.getTokenVersion()).isEqualTo(1L);
        assertThat(passwordEncoder.matches("SecurePassword@123", persisted.getPasswordHash())).isTrue();
        assertThat(persisted.getPasswordHash()).startsWith("$2a$12$"); // BCrypt strength 12 verified

        // Verify audit event persisted
        assertThat(auditRepository.findAll()).anyMatch(event ->
                event.getAction().equals("USER_REGISTERED") &&
                        event.getEntityId().equals(String.valueOf(persisted.getId()))
        );
    }

    @Test
    @DisplayName("POST /api/auth/register: Duplicate email returns HTTP 409 Conflict")
    void shouldRejectDuplicateEmailRegistration() throws Exception {
        RegisterRequest duplicate = new RegisterRequest(
                "John Duplicate",
                "john.guest@example.com", // Already exists
                "9876543299",
                "Some Address",
                "Password@123",
                null
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("POST /api/auth/register: Invalid bean validation returns HTTP 422 Unprocessable Entity")
    void shouldReturn422OnValidationErrors() throws Exception {
        RegisterRequest invalid = new RegisterRequest(
                "", // Blank
                "not-an-email", // Bad email
                "123", // Short phone
                null,
                "short", // Password < 8 chars
                null
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Authentication & Login Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/login: Successful login returns 200 OK and valid JWT")
    void shouldLoginSuccessfully() throws Exception {
        LoginRequest request = new LoginRequest("john.guest@example.com", "Password@123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.email").value("john.guest@example.com"))
                .andExpect(jsonPath("$.user.role").value("ROLE_USER"));
    }

    @Test
    @DisplayName("POST /api/auth/login: Generic failure on wrong password prevents enumeration")
    void shouldFailLoginGenericOnWrongPassword() throws Exception {
        LoginRequest request = new LoginRequest("john.guest@example.com", "WrongPassword@999");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("POST /api/auth/login: Generic failure on unknown email prevents enumeration")
    void shouldFailLoginGenericOnUnknownEmail() throws Exception {
        LoginRequest request = new LoginRequest("nonexistent@example.com", "Password@123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. User Profile & Ownership Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/users/me: Unauthenticated request returns 401 Unauthorized")
    void shouldReturn401WhenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("GET /api/users/me: Authenticated request returns user profile")
    void shouldReturnUserProfileForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testUser.getId()))
                .andExpect(jsonPath("$.email").value("john.guest@example.com"))
                .andExpect(jsonPath("$.tokenVersion").value(1));
    }

    @Test
    @DisplayName("PUT /api/users/me: Updates profile fields and writes audit event")
    void shouldUpdateUserProfile() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("John New Name", "9876543299", "500 New Road");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("John New Name"))
                .andExpect(jsonPath("$.phone").value("9876543299"))
                .andExpect(jsonPath("$.address").value("500 New Road"));

        User updatedInDb = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updatedInDb.getFullName()).isEqualTo("John New Name");

        // Verify audit record contains before and after state without sensitive data
        assertThat(auditRepository.findAll()).anyMatch(event ->
                event.getAction().equals("PROFILE_UPDATED") &&
                        event.getAfterJson().contains("John New Name") &&
                        !event.getAfterJson().contains("password")
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Email Change & Server-Side Token Invalidation (tokenVersion)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /api/users/me/email: Requires current password; invalid password returns 401")
    void shouldRejectEmailChangeWithWrongPassword() throws Exception {
        UpdateEmailRequest request = new UpdateEmailRequest("WrongPassword", "john.new@example.com");

        mockMvc.perform(patch("/api/users/me/email")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("PATCH /api/users/me/email: Duplicate email returns 409 Conflict")
    void shouldRejectEmailChangeWithDuplicateEmail() throws Exception {
        UpdateEmailRequest request = new UpdateEmailRequest("Password@123", "admin@stayease.local");

        mockMvc.perform(patch("/api/users/me/email")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    @DisplayName("SEC-05: Successful email change increments tokenVersion and immediately invalidates old token")
    void shouldChangeEmailAndInvalidatePreviousToken() throws Exception {
        UpdateEmailRequest request = new UpdateEmailRequest("Password@123", "john.brandnew@example.com");

        mockMvc.perform(patch("/api/users/me/email")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john.brandnew@example.com"))
                .andExpect(jsonPath("$.tokenVersion").value(2));

        // Verify database tokenVersion was incremented
        User updated = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updated.getTokenVersion()).isEqualTo(2L);
        assertThat(updated.getEmail()).isEqualTo("john.brandnew@example.com");

        // Verify immediate invalidation: previous token (with tokenVersion=1) is now REJECTED
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isUnauthorized());

        // Verify new login works and produces token with tokenVersion=2
        LoginRequest loginWithNewEmail = new LoginRequest("john.brandnew@example.com", "Password@123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginWithNewEmail)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode responseNode = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String freshToken = responseNode.get("token").asText();

        // Fresh token accesses /api/users/me successfully
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("john.brandnew@example.com"))
                .andExpect(jsonPath("$.tokenVersion").value(2));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Admin Authorization, Guest Search & Password Reset
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("SEC-07: ROLE_USER attempting to access /api/admin/users returns 403 Forbidden")
    void shouldRejectRegularUserFromAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/admin/users: ROLE_ADMIN accesses paginated guest search")
    void shouldAllowAdminToSearchUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .param("query", "John")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].email").value("john.guest@example.com"));
    }

    @Test
    @DisplayName("SEC-06: Admin password reset generates temporary password, increments tokenVersion, invalidates existing token")
    void shouldPerformAdminPasswordResetSafely() throws Exception {
        // Admin calls password reset for testUser
        MvcResult resetResult = mockMvc.perform(post("/api/admin/users/" + testUser.getId() + "/password-reset")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(testUser.getId()))
                .andExpect(jsonPath("$.temporaryPassword").exists())
                .andReturn();

        JsonNode json = objectMapper.readTree(resetResult.getResponse().getContentAsString());
        String temporaryPassword = json.get("temporaryPassword").asText();
        assertThat(temporaryPassword).hasSizeGreaterThanOrEqualTo(12);

        // Verify previous token for testUser is now invalidated
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isUnauthorized());

        // Verify user can now log in using the temporary password
        LoginRequest tempLogin = new LoginRequest("john.guest@example.com", temporaryPassword);
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tempLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());

        // Verify audit record does NOT contain the temporary password
        assertThat(auditRepository.findAll()).anyMatch(event ->
                event.getAction().equals("ADMIN_PASSWORD_RESET") &&
                        !event.getDescription().contains(temporaryPassword)
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Audit Trail Endpoint Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/admin/audit: ROLE_ADMIN retrieves paginated audit trail with sensitive data excluded")
    void shouldAllowAdminToQueryAuditTrail() throws Exception {
        // Trigger an audit action
        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest("New John", "9876543210", "HQ"))))
                .andExpect(status().isOk());

        // Admin queries audit trail
        mockMvc.perform(get("/api/admin/audit")
                        .param("category", "ACCOUNT")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].sourceService").value("USER_SERVICE"))
                .andExpect(jsonPath("$.content[0].action").value("PROFILE_UPDATED"));

        // Verify audit records do not contain password in database
        assertThat(auditRepository.findAll()).allMatch(e ->
                (e.getBeforeJson() == null || !e.getBeforeJson().contains("password")) &&
                        (e.getAfterJson() == null || !e.getAfterJson().contains("password"))
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Internal Service-to-Service Endpoint Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /internal/users/{userId}/token-version: Returns tokenVersion for downstream service verification")
    void shouldReturnTokenVersionForInternalCalls() throws Exception {
        mockMvc.perform(get("/internal/users/" + testUser.getId() + "/token-version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(testUser.getId()))
                .andExpect(jsonPath("$.tokenVersion").value(1));
    }

    @Test
    @DisplayName("GET /internal/users/{userId}/token-version: Unknown user returns 404 Not Found")
    void shouldReturn404ForUnknownUserInternalCall() throws Exception {
        mockMvc.perform(get("/internal/users/9999/token-version"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }
}
