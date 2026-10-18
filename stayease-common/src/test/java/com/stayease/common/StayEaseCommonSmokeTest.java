package com.stayease.common;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import com.stayease.common.enums.*;
import com.stayease.common.dto.*;

/**
 * Smoke tests verifying that all shared enums and DTOs compile correctly.
 * Full functional tests are located in each service module.
 */
class StayEaseCommonSmokeTest {

    @Test
    void allEnumsAreAccessible() {
        assertThat(ReservationStatus.values()).hasSizeGreaterThan(0);
        assertThat(PaymentStatus.values()).hasSizeGreaterThan(0);
        assertThat(PaymentPolicy.values()).hasSizeGreaterThan(0);
        assertThat(HousekeepingStatus.values()).hasSizeGreaterThan(0);
        assertThat(OperationalStatus.values()).hasSizeGreaterThan(0);
        assertThat(ReviewStatus.values()).hasSizeGreaterThan(0);
        assertThat(CancellationReason.values()).hasSizeGreaterThan(0);
        assertThat(PaymentMethod.values()).hasSizeGreaterThan(0);
    }

    @Test
    void reservationStatusHasAllExpectedValues() {
        assertThat(ReservationStatus.PENDING_PAYMENT).isNotNull();
        assertThat(ReservationStatus.CONFIRMED).isNotNull();
        assertThat(ReservationStatus.CHECKED_IN).isNotNull();
        assertThat(ReservationStatus.CHECKED_OUT).isNotNull();
        assertThat(ReservationStatus.CANCELLED).isNotNull();
        assertThat(ReservationStatus.NO_SHOW).isNotNull();
        assertThat(ReservationStatus.PAYMENT_FAILED).isNotNull();
        assertThat(ReservationStatus.RECONCILIATION_REQUIRED).isNotNull();
    }

    @Test
    void pageResponseCanBeInstantiated() {
        var page = new PageResponse<String>(
                java.util.List.of("item1", "item2"),
                0, 20, 2L, 1, true, true, false
        );
        assertThat(page.content()).hasSize(2);
        assertThat(page.totalElements()).isEqualTo(2L);
    }

    @Test
    void errorResponseCanBeInstantiatedWithoutFieldErrors() {
        var error = new ErrorResponse(
                java.time.Instant.now(),
                409, "Conflict",
                "INSUFFICIENT_INVENTORY",
                "No capacity",
                "/api/reservations",
                "test-correlation-id"
        );
        assertThat(error.code()).isEqualTo("INSUFFICIENT_INVENTORY");
        assertThat(error.fieldErrors()).isEmpty();
    }
}
