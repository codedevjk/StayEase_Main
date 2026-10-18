package com.stayease.reservation.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * General application configuration for StayEase Reservation Service.
 *
 * <p>Aligned with the CourseCart reference architecture for standardizing
 * inter-service communication via load-balanced {@link WebClient.Builder}.
 */
@Configuration
public class AppConfig {

    /**
     * Provides a load-balanced WebClient.Builder that resolves service names
     * via Spring Cloud Consul discovery.
     */
    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
