package com.stayease.review.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorrelationIdFilter correlationIdFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        // Authenticated user review endpoints
                        .requestMatchers(HttpMethod.GET, "/api/reviews/my").authenticated()
                        // Public review viewing endpoints
                        .requestMatchers(HttpMethod.GET, "/api/hotels/*/reviews").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/hotels/*/reviews/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reviews/hotels/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reviews/hotel/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reviews/reservation/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reviews/{id:[0-9]+}").permitAll()
                        // Actuator health & metrics
                        .requestMatchers("/actuator/**").permitAll()
                        // Internal service-to-service endpoints
                        .requestMatchers("/internal/**").permitAll()
                        // Admin moderation & audit
                        .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                        // Any other request must be authenticated
                        .anyRequest().authenticated()
                )
                .addFilterBefore(correlationIdFilter, SecurityContextHolderFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
