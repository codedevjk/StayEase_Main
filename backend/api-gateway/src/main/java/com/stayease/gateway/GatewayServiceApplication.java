package com.stayease.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * StayEase API Gateway — central ingress for all microservices.
 *
 * <p>This service is NOT a business service. It must not contain JPA entities,
 * business logic, or authentication processing.
 *
 * <p>Routing rules are configured in {@code application.yml}.
 * Correlation IDs are injected by a global filter.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
    }
}
