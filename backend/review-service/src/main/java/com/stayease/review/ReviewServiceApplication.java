package com.stayease.review;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * StayEase Review Service (port 8084).
 *
 * <p>Responsibilities: verified guest reviews, hotel rating aggregation,
 * admin moderation, and review audit events.
 *
 * <p>Review eligibility is verified by calling Reservation Service via WebClient.
 * If Reservation Service is unavailable, review submission fails closed (HTTP 503).
 */
@SpringBootApplication
@EnableDiscoveryClient
public class ReviewServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReviewServiceApplication.class, args);
    }
}
