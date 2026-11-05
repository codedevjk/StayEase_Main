package com.stayease.review.client;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
public class UserServiceClient {

    private final WebClient webClient;

    @Autowired
    public UserServiceClient(
            WebClient.Builder webClientBuilder,
            @Value("${stayease.internal.user-service-url:http://stayease-user-service}") String userServiceUrl) {
        this.webClient = webClientBuilder.baseUrl(userServiceUrl).build();
    }

    public Optional<Long> getTokenVersion(Long userId) {
        try {
            TokenVersionDto response = webClient.get()
                    .uri("/internal/users/{userId}/token-version", userId)
                    .retrieve()
                    .bodyToMono(TokenVersionDto.class)
                    .timeout(Duration.ofSeconds(3))
                    .block();

            if (response != null && response.getTokenVersion() != null) {
                return Optional.of(response.getTokenVersion());
            }
        } catch (Exception ex) {
            log.error("Failed to query tokenVersion from user-service for userId {}: {}", userId, ex.getMessage());
        }
        return Optional.empty();
    }

    @Data
    public static class TokenVersionDto {
        private Long userId;
        private Long tokenVersion;
    }
}
