package com.stayease.user.controller;

import com.stayease.user.dto.TokenVersionResponse;
import com.stayease.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing internal endpoints for inter-service communication.
 * Specifically used by other microservices to verify tokenVersion in a fail-closed manner.
 */
@RestController
@RequestMapping("/internal/users")
@RequiredArgsConstructor
public class InternalUserController {

    private final UserService userService;

    /**
     * Looks up the current tokenVersion for a given user.
     */
    @GetMapping("/{userId}/token-version")
    public ResponseEntity<TokenVersionResponse> getTokenVersion(@PathVariable Long userId) {
        Long version = userService.getTokenVersion(userId);
        return ResponseEntity.ok(new TokenVersionResponse(userId, version));
    }
}
