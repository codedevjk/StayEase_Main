package com.stayease.user.security;

import com.stayease.user.entity.User;
import com.stayease.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Filter that validates Bearer JWT access tokens on each inbound request.
 * Enforces server-side token revocation by checking tokenVersion against the User record.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String jwt = extractJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                Long userId = tokenProvider.getUserIdFromToken(jwt);
                Long tokenVersionClaim = tokenProvider.getTokenVersionFromToken(jwt);

                if (userId != null && tokenVersionClaim != null) {
                    Optional<User> userOpt = userRepository.findById(userId);

                    if (userOpt.isPresent()) {
                        User user = userOpt.get();

                        // Immediate server-side token invalidation check
                        if (user.getTokenVersion().equals(tokenVersionClaim)) {
                            UserPrincipal principal = UserPrincipal.create(user);
                            UsernamePasswordAuthenticationToken authentication =
                                    new UsernamePasswordAuthenticationToken(
                                            principal,
                                            null,
                                            principal.getAuthorities()
                                    );
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        } else {
                            log.debug("Rejecting token for user {}: tokenVersion {} is outdated (current in DB: {})",
                                    userId, tokenVersionClaim, user.getTokenVersion());
                        }
                    } else {
                        log.debug("Rejecting token: user {} not found in database", userId);
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
