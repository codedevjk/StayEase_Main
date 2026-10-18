package com.stayease.reservation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * StayEase Reservation Service (port 8083).
 *
 * <p>Responsibilities: room-type availability (authoritative), nightly inventory
 * ledger, reservations, simulated payments, payment deadlines, cancellations,
 * amendments, check-in/out, invoices, dashboard, and reservation audit events.
 *
 * <p>{@code @EnableScheduling} activates the hold-expiration scheduler
 * (every 60 seconds) and the pay-later deadline expiry scheduler.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
public class ReservationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReservationServiceApplication.class, args);
    }
}
