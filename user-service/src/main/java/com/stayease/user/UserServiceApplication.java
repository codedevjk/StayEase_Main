package com.stayease.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * StayEase User Service (port 8081).
 *
 * <p>Responsibilities: registration, login/JWT, user profile,
 * email change, admin-assisted password reset, tokenVersion tracking,
 * and user audit events.
 *
 * <p>{@code @EnableScheduling} is included to support the optional
 * AdminBootstrapRunner; it will also be needed for any future
 * scheduled token-version cleanup tasks.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
public class UserServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
