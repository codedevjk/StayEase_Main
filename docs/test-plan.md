# StayEase Comprehensive Verification & Test Plan

**Document Version:** 1.1 (Phase 0 Contract Freeze)  
**Status:** Approved Verification Baseline  
**Scope:** Automated & Manual Verification for Reference Implementation  

---

## 1. Test Strategy & Quality Gate

StayEase enforces a strict **Verification Gate**: No component or phase is marked complete without executable proof from automated tests. The trainee starter code will not be derived until all test suites pass against the reference implementation.

```
+-------------------------------------------------------------------------+
|                              SonarQube Gate                             |
|               0 Vulnerabilities, 0 Bugs, >80% Branch Coverage            |
+-------------------------------------------------------------------------+
                                     ^
                                     |
+-------------------------------------------------------------------------+
|                  End-to-End Acceptance Workflows (E2E)                  |
|                Postman Collection + TestRestTemplate Scenarios           |
+-------------------------------------------------------------------------+
                                     ^
                                     |
+-------------------------------------------------------------------------+
|             Resilience & Fault-Tolerance Tests (Chaos / Mock)           |
|            Circuit Breaker, Fail-Closed, Uncertain Payment Reconcile     |
+-------------------------------------------------------------------------+
                                     ^
                                     |
+-------------------------------------------------------------------------+
|         Integration Tests (@SpringBootTest, MockMvc, TestDB)            |
|       JPA Constraints, Concurrency Locking, Idempotency, JWT Security   |
+-------------------------------------------------------------------------+
                                     ^
                                     |
+-------------------------------------------------------------------------+
|                      Unit Tests (JUnit 5, Mockito)                      |
|         Pricing Precedence, Tax Rounding, Cancellation Penalty, State    |
+-------------------------------------------------------------------------+
```

---

## 2. Test Suites by Domain & Layer

### 2.1 Security, Authentication & Session Verification
| Test ID | Test Category | Target Component | Verification Scenario | Expected Outcome | Phase 2 Status |
|---|---|---|---|---|---|
| `SEC-01` | Unit & Integration | `AuthService`, `UserControllerSecurityIntegrationTest` | Attempt registration with `role: "ADMIN"` in payload | Role is forced to `ROLE_USER`; cannot elevate privilege | ✅ Passed |
| `SEC-02` | Integration | `UserControllerSecurityIntegrationTest` | Register duplicate email | HTTP 409 Conflict; duplicate rejected; password hashed with BCrypt (12) | ✅ Passed |
| `SEC-03` | Integration | `AuthController`, `GlobalExceptionHandler` | Login with incorrect password or unknown email | Generic HTTP 401: `"Invalid email or password"`; no account enumeration | ✅ Passed |
| `SEC-04` | Security | `JwtTokenFilter`, `JwtTokenProviderTest` | Request with expired JWT or tampered signature | HTTP 401 Unauthorized; request rejected at filter | ✅ Passed |
| `SEC-05` | Security | `JwtAuthenticationFilter`, `UserControllerSecurityIntegrationTest` | Request with old `tokenVersion` after password reset or email update | HTTP 401 Unauthorized; previous sessions immediately invalidated | ✅ Passed |
| `SEC-06` | Security | `UserControllerSecurityIntegrationTest` | Profile ownership enforcement; admin password reset | Request operates strictly on authenticated principal; admin reset increments tokenVersion | ✅ Passed |
| `SEC-07` | Security | `SecurityConfig`, `UserControllerSecurityIntegrationTest` | Authenticated `ROLE_USER` accesses `/api/admin/*` | HTTP 403 Forbidden | ✅ Passed |
| `SEC-08` | Integration | `AdminBootstrapRunner`, `AdminBootstrapRunnerTest` | Verify bootstrap and SQL scripts contain no plaintext passwords | Plaintext passwords absent from Git and logs; one-time stdout disclosure only | ✅ Passed |

### 2.2 Property, Search & Pricing Verification
| Test ID | Test Category | Target Component | Verification Scenario | Expected Outcome | Phase 3 Status |
|---|---|---|---|---|---|
| `PRP-01` | Unit | `PricingCalculatorService` | Overlap of base rate, weekend override, and seasonal override | Seasonal overrides weekend; weekend overrides base rate | ✅ Passed |
| `PRP-02` | Unit | `PricingCalculatorService` | Nightly rate calculation and BigDecimal HALF_UP rounding | Exact `BigDecimal` rounding with `HALF_UP` to 2 decimal places | ✅ Passed |
| `PRP-03` | Unit & Integration | `HotelService`, `PropertyControllerSecurityIntegrationTest` | Filter hotels by destination, rating, amenities, and price range | Paginated results match all applied filters | ✅ Passed |
| `PRP-04` | Integration | `PhysicalRoomService`, `RoomTypeService` | Mark physical room `OUT_OF_SERVICE` | Sellable capacity for room type decrements by 1 | ✅ Passed |
| `PRP-05` | Integration | `PhysicalRoomService`, `RoomTypeService` | Mark physical room `DIRTY` or `INSPECTING` | Sellable capacity for future dates unchanged; immediate check-in blocked | ✅ Passed |

### 2.2.1 Reference Compliance & Audit Verification (Pre-Phase 4 Gate)
| Test ID | Test Category | Target Component | Verification Scenario | Expected Outcome | Audit Status |
|---|---|---|---|---|---|
| `AUD-01` | Unit | `gateway-service`: `CorrelationIdGlobalFilterTest` | Inbound request with/without `X-Correlation-ID` | Generates UUID if missing; propagates existing; sets response header | ✅ Passed (2/2) |
| `AUD-02` | Integration | `user-service`: `MessageResolutionTest` | Resolve validation and business messages from `messages.properties` / `message.properties` | Correct message string resolved; fallback on unknown keys | ✅ Passed (3/3) |
| `AUD-03` | Integration | `property-service`: `MessageResolutionTest` | Resolve validation and business messages from `messages.properties` / `message.properties` | Correct message string resolved; fallback on unknown keys | ✅ Passed (3/3) |
| `LOG-01` | Integration | `gateway-service`: `LoggingConfigurationTest` | Verify Log4j2 SLF4J binding and absence of Logback | SLF4J binds to `Log4jLoggerFactory`; `ch.qos.logback.classic.Logger` absent | ✅ Passed (3/3) |
| `LOG-02` | Integration | `user-service`: `LoggingConfigurationTest` | Verify Log4j2 SLF4J binding, Commons Logging, and absence of Logback | SLF4J binds to `Log4jLoggerFactory`; Commons Logging operates via Log4j2; Logback absent | ✅ Passed (3/3) |
| `LOG-03` | Integration | `property-service`: `LoggingConfigurationTest` | Verify Log4j2 SLF4J binding, Commons Logging, and absence of Logback | SLF4J binds to `Log4jLoggerFactory`; Commons Logging operates via Log4j2; Logback absent | ✅ Passed (3/3) |

### 2.3 Reservation, Inventory Locking & Payment Simulation
| Test ID | Test Category | Target Component | Verification Scenario | Expected Outcome | Phase 4 Status |
|---|---|---|---|---|---|
| `RES-01` | Integration / Concurrency | `InventoryLedgerService` | Concurrent requests attempt to book last remaining room for same dates | Exactly 1 request succeeds; concurrent requests receive HTTP 409 Conflict | ✅ Passed |
| `RES-02` | Unit / Integration | `HoldAndExpiryScheduler` | Prepaid hold exceeds 10 minutes without payment | Status becomes `PAYMENT_FAILED` (reason `HOLD_EXPIRED`); inventory hold released | ✅ Passed |
| `RES-03` | Integration | `PaymentService` | Successful prepaid payment simulation | Reservation status transitions to `CONFIRMED`; payment status `PAID` | ✅ Passed |
| `RES-04` | Integration | `PaymentService` | Payment simulation returns `TIMEOUT` / network failure | Reservation transitions to `RECONCILIATION_REQUIRED`; hold protected | ✅ Passed |
| `RES-05` | Integration | `PaymentService` | Duplicate payment request submitted with same `Idempotency-Key` | Idempotent response returned; no duplicate charge or state change | ✅ Passed |
| `RES-06` | Unit | `ReservationService` | Calculate deadline for booking created 5 days before check-in | `paymentDueAt` = `createdAt + 24 hours` | ✅ Passed |
| `RES-07` | Unit | `ReservationService` | Calculate deadline for same-day booking (check-in today at 14:00) | `paymentDueAt` = check-in date at `14:00 local time` | ✅ Passed |
| `RES-08` | Integration | `HoldAndExpiryScheduler` | Unpaid pay-later booking exceeds `paymentDueAt` | Reservation cancelled (reason `PAYMENT_TIMEOUT`); inventory released | ✅ Passed |

### 2.4 Cancellation, Refund & Amendment Verification
| Test ID | Test Category | Target Component | Verification Scenario | Expected Outcome | Phase 4 Status |
|---|---|---|---|---|---|
| `CAN-01` | Unit | `ReservationService` | Cancellation $\ge 24\text{ hours}$ before scheduled check-in | Penalty = 0.00; Refund = 100% of amount paid | ✅ Passed |
| `CAN-02` | Unit | `ReservationService` | Cancellation $< 24\text{ hours}$ before scheduled check-in | Penalty = 1 night's charge + tax; Refund = balance | ✅ Passed |
| `CAN-03` | Integration | `ReservationService` | Idempotent repeated cancellation calls | Subsequent calls return existing cancellation summary without extra refund | ✅ Passed |
| `CAN-04` | Integration | `ReservationService` | Amend dates to higher-priced weekend / room type | Inventory validated; price recalculated; additional payment requested | ✅ Passed |
| `CAN-05` | Integration | `ReservationService` | Amend dates to lower-priced stay | Inventory updated; refund issued for difference; audit captures before/after | ✅ Passed |

### 2.5 Stay Operations & Physical Room Assignment
| Test ID | Test Category | Target Component | Verification Scenario | Expected Outcome | Phase 4 Status |
|---|---|---|---|---|---|
| `OPS-01` | Integration | `ReservationService` | Check-in attempted when status is `PENDING` payment | HTTP 409 Conflict; check-in denied | ✅ Passed |
| `OPS-02` | Integration | `ReservationService` | Check-in attempted when all physical rooms of type are `DIRTY` | HTTP 409 Conflict; reservation remains `CONFIRMED`; front desk alerted | ✅ Passed |
| `OPS-03` | Integration | `ReservationService` | Check-in attempted with `PAID` reservation and `CLEAN` room ready | Physical room assigned; reservation transitions to `CHECKED_IN` | ✅ Passed |
| `OPS-04` | Integration | `ReservationService` | Check-out for `CHECKED_IN` reservation | Reservation transitions to `CHECKED_OUT`; assigned room marked `DIRTY` | ✅ Passed |
| `OPS-05` | Integration | `ReservationService` | Admin marks reservation `NO_SHOW` on/after check-in date | Status transitions to `NO_SHOW`; remaining inventory released | ✅ Passed |

### 2.6 Review, Invoice, Dashboard & Audit Verification
| Test ID | Test Category | Target Component | Verification Scenario | Expected Outcome | Phase Status |
|---|---|---|---|---|---|
| `REV-01` | Integration | `ReviewService` | Guest submits review within 30 days of checkout | Review created with status `PUBLISHED` | ✅ Passed (Phase 5) |
| `REV-02` | Integration | `ReviewService` | Guest submits review > 30 days after checkout | HTTP 403 Forbidden; review rejected | ✅ Passed (Phase 5) |
| `REV-03` | Integration | `ReviewService` | Guest attempts second review for same reservation | HTTP 409 Conflict; duplicate rejected | ✅ Passed (Phase 5) |
| `REV-04` | Integration | `ReviewModerationService`| Admin removes review with reason | Review status becomes `REMOVED`; removed from public listing | ✅ Passed (Phase 5) |
| `INV-01` | Integration | `ReservationService` | Render invoice for confirmed reservation | Uses immutable price snapshot and payment ledger; matches booking total | ✅ Passed (Phase 4) |
| `DSH-01` | Integration | `ReservationService` | Admin dashboard metrics aggregation with downstream service outage | Operational metrics computed; partial data warning supported | ✅ Passed (Phase 4) |
| `AUD-01` | Integration | `ReservationAuditService` | Admin queries audit trail with date range and category filters | Returns paged audit records from service database | ✅ Passed (Phase 4) |
| `AUD-02` | Integration | Frontend UI / Gateway | Admin queries aggregated audit trail while Property Service is offline | UI displays logs from User, Reservation, Review with warning banner | Pending (Phase 8) |

---

## 3. Resilience, Fault-Tolerance & Negative Path Scenarios

1. **Fail-Closed Verification:**
   - Terminate Property Service: Attempting to create a reservation or check in must return HTTP 503 Service Unavailable immediately. System must not use stale inventory cache or confirm booking.
   - Terminate Reservation Service: Attempting to submit a review must fail closed with HTTP 503. Client-provided reservation IDs must not be trusted.
   - Terminate User Service: Protected API requests across all services must fail closed (HTTP 503 / 401) because `tokenVersion` cannot be verified.
2. **Network Timeout & Circuit Breaking:**
   - Simulate 3000ms delay in Property Service responses: WebClient timeout (configured at 1500ms) trips, triggering Resilience4j circuit breaker and throwing `ServiceUnavailableException`.

---

## 4. Postman End-to-End API Test Collection

A complete Postman collection (`postman/StayEase_E2E_Collection.json`) with automated pre-request scripts and assertions covers the entire guest and administrator lifecycle:
1. `Auth`: Register Guest $\rightarrow$ Login Guest $\rightarrow$ Verify Token.
2. `Discovery`: Search Hotels Goa $\rightarrow$ Select Hotel $\rightarrow$ Fetch Room Types.
3. `Availability`: Check Availability for 2 nights across room types.
4. `Prepaid Booking`: Create Reservation (PREPAID) $\rightarrow$ Verify 10-minute hold $\rightarrow$ Simulate UPI Payment $\rightarrow$ Verify `CONFIRMED`.
5. `Pay-Later Booking`: Create Reservation (PAY_LATER) $\rightarrow$ Verify `CONFIRMED` with `PENDING` payment $\rightarrow$ Simulate Card Payment.
6. `Self Check-in`: Execute Check-in on scheduled date $\rightarrow$ Verify Physical Room Assigned.
7. `Self Check-out`: Execute Check-out $\rightarrow$ Verify Room marked `DIRTY`.
8. `Review`: Submit Verified Review $\rightarrow$ Verify Hotel rating recalculated.
9. `Invoice`: Retrieve printable invoice payload $\rightarrow$ Verify snapshot matches total.
10. `Admin Audit`: Admin queries audit logs $\rightarrow$ Verify event sequence captured.

---

## 5. Verification Execution Instructions

### 5.1 Unit & Integration Test Execution (Maven)
```powershell
# Run all unit tests across all microservices
mvn clean test

# Run integration tests against service test profiles (H2/Testcontainers)
mvn test -Pintegration-test
```

### 5.2 SonarQube Quality Analysis
```powershell
mvn clean verify sonar:sonar `
  -Dsonar.host.url=http://localhost:9000 `
  -Dsonar.login=<SONARQUBE_TOKEN>
```
**Quality Gate Thresholds:**
- Bugs: 0
- Vulnerabilities: 0
- Code Smells: < 10
- Branch Test Coverage: $\ge 80\%$
- Duplicated Lines: $< 3.0\%$
