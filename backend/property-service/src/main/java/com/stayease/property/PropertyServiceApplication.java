package com.stayease.property;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * StayEase Property Service (port 8082).
 *
 * <p>Responsibilities: hotels, room types, physical rooms, amenities,
 * rate overrides, housekeeping/operational status, and property audit events.
 *
 * <p>IMPORTANT: This service must NEVER make synchronous calls to
 * Reservation Service. All inter-service calls flow in one direction:
 * Reservation → Property.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class PropertyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PropertyServiceApplication.class, args);
    }
}
