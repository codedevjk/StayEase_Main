package com.stayease.user.controller;

import com.stayease.user.dto.UpdateEmailRequest;
import com.stayease.user.dto.UpdateProfileRequest;
import com.stayease.user.dto.UserProfileDto;
import com.stayease.user.security.UserPrincipal;
import com.stayease.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controller handling authenticated user profile operations.
 * Enforces ownership by deriving user identity strictly from the authenticated principal.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Retrieves the profile of the currently authenticated user.
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> getMyProfile(@AuthenticationPrincipal UserPrincipal principal) {
        UserProfileDto profile = userService.getProfile(principal.getId());
        return ResponseEntity.ok(profile);
    }

    /**
     * Updates profile details (name, phone, address) for the currently authenticated user.
     */
    @PutMapping("/me")
    public ResponseEntity<UserProfileDto> updateMyProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateProfileRequest request) {

        UserProfileDto updated = userService.updateProfile(principal.getId(), request);
        return ResponseEntity.ok(updated);
    }

    /**
     * Updates email address with current password verification.
     * Increments tokenVersion, requiring a fresh login.
     */
    @PatchMapping("/me/email")
    public ResponseEntity<UserProfileDto> updateMyEmail(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateEmailRequest request) {

        UserProfileDto updated = userService.updateEmail(principal.getId(), request);
        return ResponseEntity.ok(updated);
    }
}
