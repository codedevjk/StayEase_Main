package com.stayease.user.controller;

import com.stayease.user.dto.AuditEventDto;
import com.stayease.user.dto.PageResponse;
import com.stayease.user.dto.AdminPasswordResetResponse;
import com.stayease.user.dto.AdminUserSummaryDto;
import com.stayease.user.security.UserPrincipal;
import com.stayease.user.service.UserAuditService;
import com.stayease.user.service.UserService;
import com.stayease.user.util.PageUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

/**
 * Controller handling administrative user operations and audit logs.
 * Restricted to ROLE_ADMIN.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;
    private final UserAuditService auditService;

    /**
     * Searches and paginates guest and admin accounts.
     */
    @GetMapping("/users")
    public ResponseEntity<PageResponse<AdminUserSummaryDto>> searchUsers(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "id"));

        Page<AdminUserSummaryDto> results = userService.adminSearchUsers(query, pageable);
        return ResponseEntity.ok(PageUtils.toPageResponse(results));
    }

    /**
     * Resets a user's password with a generated one-time temporary password.
     * Increments tokenVersion to invalidate existing sessions.
     */
    @PostMapping("/users/{userId}/password-reset")
    public ResponseEntity<AdminPasswordResetResponse> resetPassword(
            @AuthenticationPrincipal UserPrincipal adminPrincipal,
            @PathVariable Long userId) {

        AdminPasswordResetResponse response = userService.adminResetPassword(adminPrincipal.getId(), userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves paginated audit trail logs for User Service.
     */
    @GetMapping("/audit")
    public ResponseEntity<PageResponse<AuditEventDto>> getAuditTrail(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "timestamp"));

        Page<AuditEventDto> results = auditService.searchAuditEvents(from, to, category, actorId, entityId, pageable);
        return ResponseEntity.ok(PageUtils.toPageResponse(results));
    }
}
