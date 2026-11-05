package com.stayease.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stayease.user.dto.AuditEventDto;
import com.stayease.user.entity.UserAuditEvent;
import com.stayease.user.repository.UserAuditEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAuditServiceTest {

    @Mock
    private UserAuditEventRepository auditRepository;

    private UserAuditService auditService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        auditService = new UserAuditService(auditRepository, objectMapper);
    }

    @Test
    @DisplayName("Should persist audit event with JSON snapshots and sourceService = USER_SERVICE")
    void shouldPersistAuditEvent() {
        when(auditRepository.save(any(UserAuditEvent.class))).thenAnswer(i -> {
            UserAuditEvent event = i.getArgument(0);
            event.setId(501L);
            return event;
        });

        Map<String, String> before = Map.of("phone", "9876543210");
        Map<String, String> after = Map.of("phone", "9876543219");

        UserAuditEvent result = auditService.recordEvent(
                101L,
                "USER",
                "ACCOUNT",
                "PROFILE_UPDATED",
                "101",
                "Phone number updated",
                before,
                after
        );

        assertThat(result.getId()).isEqualTo(501L);

        ArgumentCaptor<UserAuditEvent> captor = ArgumentCaptor.forClass(UserAuditEvent.class);
        verify(auditRepository).save(captor.capture());
        UserAuditEvent saved = captor.getValue();

        assertThat(saved.getActorId()).isEqualTo(101L);
        assertThat(saved.getActorRole()).isEqualTo("USER");
        assertThat(saved.getEventCategory()).isEqualTo("ACCOUNT");
        assertThat(saved.getAction()).isEqualTo("PROFILE_UPDATED");
        assertThat(saved.getEntityId()).isEqualTo("101");
        assertThat(saved.getBeforeJson()).contains("9876543210");
        assertThat(saved.getAfterJson()).contains("9876543219");

        // Verify DTO mapping includes USER_SERVICE source
        AuditEventDto dto = auditService.toDto(saved);
        assertThat(dto.sourceService()).isEqualTo("USER_SERVICE");
        assertThat(dto.action()).isEqualTo("PROFILE_UPDATED");
    }

    @Test
    @DisplayName("Audit recording should never contain password fields")
    void shouldNotContainPasswordsInAuditRecord() {
        when(auditRepository.save(any(UserAuditEvent.class))).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> state = Map.of("email", "admin@stayease.local", "role", "ROLE_ADMIN");

        UserAuditEvent result = auditService.recordEvent(
                0L,
                "SYSTEM",
                "AUTH",
                "ADMIN_BOOTSTRAP",
                "1",
                "Seeded admin",
                null,
                state
        );

        assertThat(result.getAfterJson()).doesNotContain("password");
        assertThat(result.getAfterJson()).doesNotContain("passwordHash");
        assertThat(result.getAfterJson()).doesNotContain("secret");
    }
}
