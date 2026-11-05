package com.stayease.property.security;

import com.stayease.property.client.UserServiceClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Filter that validates Bearer JWT access tokens on inbound requests to Property Service.
 * Optionally verifies tokenVersion against User Service (fail-closed by default).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserServiceClient userServiceClient;

    @Value("${stayease.security.verify-token-version-with-user-service:true}")
    private boolean verifyTokenVersionWithUserService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String jwt = extractJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                Long userId = tokenProvider.getUserIdFromToken(jwt);
                String email = tokenProvider.getEmailFromToken(jwt);
                String role = tokenProvider.getRoleFromToken(jwt);
                Long tokenVersionClaim = tokenProvider.getTokenVersionFromToken(jwt);

                if (userId != null && tokenVersionClaim != null && role != null) {
                    boolean isValid = true;
                    if (verifyTokenVersionWithUserService) {
                        Optional<Long> liveVersionOpt = userServiceClient.getTokenVersion(userId);
                        if (liveVersionOpt.isEmpty()) {
                            log.warn("User Service unreachable or user {} not found; rejecting token (fail-closed)", userId);
                            isValid = false;
                        } else if (!liveVersionOpt.get().equals(tokenVersionClaim)) {
                            log.debug("Rejecting token for user {}: tokenVersion {} is outdated (current: {})",
                                    userId, tokenVersionClaim, liveVersionOpt.get());
                            isValid = false;
                        }
                    }

                    if (isValid) {
                        UserPrincipal principal = UserPrincipal.create(userId, email, role, tokenVersionClaim);
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(
                                        principal,
                                        null,
                                        principal.getAuthorities()
                                );
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
