# StayEase Requirement-to-Test Traceability Matrix (RTM)

**Document Version:** 1.1 (Phase 0 Contract Freeze)  
**Status:** Approved Requirements Traceability Baseline  
**Requirements Baseline:** `StayEase_SRS_v1.1`  
**Verification Baseline:** `docs/test-plan.md`  

---

## 1. Traceability Architecture

The Requirement Traceability Matrix (RTM) provides bidirectional traceability from every core functional and non-functional requirement in `StayEase_SRS_v1.1` to:
1. Responsible Microservice / Architectural Layer
2. Target Implementation Phase
3. Planned Classes & API Endpoints
4. Verification Test Case ID & Acceptance Scenario

---

## 2. Comprehensive Traceability Matrix

| SRS Req ID | Requirement Summary | Target Service | Phase | Implementation Component / Route | Verification Test ID | Verification Type & Method |
|---|---|---|---|---|---|---|
| **REQ-AUTH-01** | Public registration assigns `ROLE_USER` only; reject or ignore role escalation | `user-service` | Phase 2 | `POST /api/auth/register`, `UserService.registerUser()` | `SEC-01`, `RTM-01` | Unit & Integration: Submit payload with `role: "ADMIN"`; verify persisted role is `ROLE_USER`. |
| **REQ-AUTH-02** | Unique email validation and BCrypt password hashing | `user-service` | Phase 2 | `users` table constraint, `BCryptPasswordEncoder` | `SEC-02`, `RTM-02` | Integration: Attempt duplicate email registration; verify HTTP 409 Conflict and check DB hash. |
| **REQ-AUTH-03** | Generic credential failure message; no account enumeration | `user-service` | Phase 2 | `POST /api/auth/login`, `AuthExceptionHandler` | `SEC-03`, `RTM-03` | Integration: Submit wrong password; verify generic `"Invalid email or password"` message. |
| **REQ-AUTH-04** | 15-minute JWT access token issuance with standard claims | `user-service` | Phase 2 | `JwtTokenProvider`, `JwtAuthenticationFilter` | `SEC-04`, `RTM-04` | Security: Inspect JWT claims (`sub`, `role`, `tokenVersion`, `exp`); verify 900s TTL. |
| **REQ-AUTH-05** | Server-side token invalidation via `tokenVersion` increment | `user-service` | Phase 2 | `PATCH /api/users/me/email`, `POST /api/admin/users/{id}/password-reset` | `SEC-05`, `RTM-05` | Security: Update email or reset password; assert existing JWT rejected with HTTP 401. |
| **REQ-AUTH-06** | Admin-assisted password reset with secure one-time temporary password | `user-service` | Phase 2 | `POST /api/admin/users/{id}/password-reset` | `SEC-08`, `RTM-06` | Integration: Admin resets user password; verify temp password returned once, not logged. |
| **REQ-AUTH-07** | Admin bootstrap without committed plaintext credentials | `user-service` | Phase 1-2 | `AdminBootstrapRunner`, `database/schema.sql` | `SEC-08`, `RTM-07` | Integration: Verify schema/seed scripts contain no plaintext passwords; run bootstrap CLI. |
| **REQ-SRCH-01** | Hotel catalog search by destination, price, rating, amenities | `property-service` | Phase 3 | `GET /api/hotels`, `HotelSpecification` | `PRP-03`, `RTM-08` | Integration: Query `/api/hotels?destination=Goa&minRating=4`; assert paginated results match. |
| **REQ-SRCH-02** | Room-type availability calculation across full stay interval $[checkIn, checkOut)$ | `reservation-service` | Phase 4 | `GET /api/reservations/availability`, `AvailabilityService` | `RES-01`, `RTM-09` | Integration: Verify every night in half-open interval is validated; sold-out date blocks stay. |
| **REQ-PRC-01** | Deterministic rate precedence: Seasonal > Weekend > Base | `property-service` | Phase 3 | `PricingCalculatorService`, `rate_overrides` | `PRP-01`, `RTM-10` | Unit: Test matrix with overlapping seasonal, weekend, base rates; assert exact priority. |
| **REQ-PRC-02** | Configurable 12% tax, INR currency, BigDecimal HALF_UP rounding | `reservation-service` | Phase 4 | `PricingCalculatorService`, `stayease.pricing.tax-percentage` | `PRP-02`, `RTM-11` | Unit: Calculate charges on subtotal; verify 2 decimal places and HALF_UP rounding. |
| **REQ-PRC-03** | Immutable price snapshot persisted at booking confirmation | `reservation-service` | Phase 4 | `reservation.price_snapshot_json` | `INV-01`, `RTM-12` | Integration: Alter base rates in Property Service; assert reservation snapshot unchanged. |
| **REQ-RES-01** | Nightly inventory ledger with pessimistic row locking | `reservation-service` | Phase 4 | `room_type_inventory`, `InventoryRepository.findAndLockForDates()` | `RES-01`, `RTM-13` | Concurrency: Run 20 concurrent booking threads for 1 available room; assert 1 success, 19 fails. |
| **REQ-RES-02** | Prepaid 10-minute temporary inventory hold | `reservation-service` | Phase 4 | `POST /api/reservations` (PREPAID), `HoldExpirationScheduler` | `RES-02`, `RTM-14` | Integration: Create hold; advance mock clock by 11 mins; assert hold cancelled and released. |
| **REQ-RES-03** | Pay-later payment deadline calculation: $\min(createdAt + 24h, checkInDate \text{ 14:00})$ | `reservation-service` | Phase 4 | `POST /api/reservations` (PAY_LATER), `PayLaterExpiryScheduler` | `RES-06`, `RES-07` | Unit: Test 5-day advance booking vs same-day booking; assert deadline calculation. |
| **REQ-RES-04** | Pay-later automatic cancellation and capacity release upon deadline expiry | `reservation-service` | Phase 4 | `PayLaterExpiryScheduler` | `RES-08`, `RTM-15` | Integration: Trigger expiry scheduler on unpaid reservation; assert status CANCELLED, inventory released. |
| **REQ-PAY-01** | Simulated payment processing (CARD, UPI, NETBANKING) | `reservation-service` | Phase 4 | `POST /api/reservations/{id}/payment`, `PaymentSimulator` | `RES-03`, `RTM-16` | Integration: Submit UPI payment; assert reservation transitions to CONFIRMED and payment PAID. |
| **REQ-PAY-02** | Payment idempotency enforcement via `Idempotency-Key` header | `reservation-service` | Phase 4 | `payment_transactions.idempotency_key`, `PaymentService` | `RES-05`, `RTM-17` | Integration: Submit identical payment request twice with same key; assert idempotent replay. |
| **REQ-PAY-03** | Uncertain payment reconciliation handling (no double-charge or early release) | `reservation-service` | Phase 4 | `ReservationStatus.RECONCILIATION_REQUIRED` | `RES-04`, `RTM-18` | Fault-Tolerance: Simulate payment timeout; assert hold protected in RECONCILIATION_REQUIRED. |
| **REQ-CAN-01** | Cancellation $\ge 24\text{h}$ before check-in: 100% refund, 0 penalty | `reservation-service` | Phase 4 | `POST /api/reservations/{id}/cancel`, `CancellationService` | `CAN-01`, `RTM-19` | Unit: Cancel 48 hours before 14:00 check-in; verify full refund amount and zero penalty. |
| **REQ-CAN-02** | Cancellation $< 24\text{h}$ before check-in: 1 night penalty, balance refunded | `reservation-service` | Phase 4 | `POST /api/reservations/{id}/cancel`, `CancellationService` | `CAN-02`, `RTM-20` | Unit: Cancel 10 hours before 14:00 check-in; verify penalty equals Night 1 charge + tax. |
| **REQ-CAN-03** | Idempotent cancellation and inventory release | `reservation-service` | Phase 4 | `CancellationService` | `CAN-03`, `RTM-21` | Integration: Call cancel endpoint twice; assert second call returns identical summary without duplicate release. |
| **REQ-AMD-01** | Reservation amendment: validate capacity, reprice, reconcile payment difference | `reservation-service` | Phase 4 | `POST /api/reservations/{id}/amend`, `AmendmentService` | `CAN-04`, `CAN-05` | Integration: Amend dates to peak season; verify inventory held, price increased, additional payment tracked. |
| **REQ-STAY-01** | Guest self-check-in: requires CONFIRMED, PAID, scheduled date, and CLEAN room | `reservation-service` | Phase 5 | `POST /api/reservations/{id}/check-in`, `CheckInService` | `OPS-01`, `OPS-03` | Integration: Verify check-in succeeds only when paid and clean room of type is available. |
| **REQ-STAY-02** | Check-in rejection when all physical rooms of type are DIRTY | `reservation-service` | Phase 5 | `CheckInService`, `PropertyClient` | `OPS-02`, `RTM-22` | Integration: Mark all rooms DIRTY; attempt check-in; assert HTTP 409 and reservation stays CONFIRMED. |
| **REQ-STAY-03** | Guest self-check-out: transitions reservation to CHECKED_OUT and room to DIRTY | `reservation-service` | Phase 5 | `POST /api/reservations/{id}/check-out`, `CheckOutService` | `OPS-04`, `RTM-23` | Integration: Execute checkout; verify reservation CHECKED_OUT and physical room housekeepingStatus is DIRTY. |
| **REQ-STAY-04** | Admin marks NO_SHOW for unfulfilled reservations on/after check-in date | `reservation-service` | Phase 5 | `POST /api/admin/reservations/{id}/no-show` | `OPS-05`, `RTM-24` | Integration: Admin marks no-show; verify status NO_SHOW and unused inventory released. |
| **REQ-REV-01** | Verified review: guest-owned, CHECKED_OUT within 30 days, 1 review per stay | `review-service` | Phase 6 | `POST /api/reviews`, `ReviewEligibilityValidator` | `REV-01`, `REV-02` | Integration: Verify review accepted within 30 days; reject if >30 days or duplicate (`REV-03`). |
| **REQ-REV-02** | Admin review removal with reason and audit tracking | `review-service` | Phase 6 | `POST /api/admin/reviews/{id}/remove` | `REV-04`, `RTM-25` | Integration: Admin removes review; verify status REMOVED and excluded from hotel rating. |
| **REQ-INV-01** | On-demand printable invoice rendered from stored price snapshot | `reservation-service` | Phase 5 | `GET /api/reservations/{id}/invoice`, `InvoiceService` | `INV-01`, `RTM-26` | Integration: Fetch invoice; verify guest ownership, stay dates, charges, payments match snapshot. |
| **REQ-DSH-01** | Operational administrative dashboard with degraded partial-data warning | `reservation-service` | Phase 5 | `GET /api/admin/dashboard`, `DashboardService` | `DSH-01`, `RTM-27` | Integration: Fetch dashboard metrics; verify counts, occupancy %, revenue, and degradation flag. |
| **REQ-AUD-01** | Per-service audit trail persistence in service databases | All Services | Phases 2-6 | `audit_events` tables in all 4 DBs | `AUD-01`, `RTM-28` | Integration: Execute actions; assert audit records saved with before/after state. |
| **REQ-AUD-02** | Aggregated admin audit view in Angular with partial-failure warning | Gateway / Angular | Phase 8 | `GET /api/admin/audit`, `AuditAggregationService` | `AUD-02`, `RTM-29` | E2E / Integration: Shut down Property Service; assert UI renders User/Res logs with warning banner. |
| **REQ-RESIL-01**| Fail-closed behavior for critical booking, payment, check-in, review calls | Gateway / Clients | Phase 7 | WebClient timeouts & Resilience4j circuit breakers | Resilience Suite | Chaos: Take down Property Service; verify reservation creation fails closed with HTTP 503. |
| **REQ-GATE-01** | Gateway routing, CORS configuration, correlation ID propagation | `gateway-service` | Phase 7 | `GlobalFilter`, `X-Correlation-ID` header | Gateway Suite | Integration: Send request through Gateway; assert `X-Correlation-ID` returned in response. |

---

## 3. SRS Section-to-Phase Cross Reference

| SRS Section | Section Name | Implementation Phase | Target Service(s) | Verification Coverage |
|---|---|---|---|---|
| Section 5 | Actors and Authorization | Phase 2, 7, 8 | User, Gateway, UI | `SEC-01`, `SEC-06`, `SEC-07` |
| Section 6 | Authentication and Session | Phase 2, 7, 8 | User, Gateway | `SEC-02`, `SEC-03`, `SEC-04`, `SEC-05` |
| Section 7 | Hotel Search and Availability | Phase 3, 4, 8 | Property, Reservation, UI | `PRP-03`, `RES-01` |
| Section 8 | Pricing and Tax | Phase 3, 4 | Property, Reservation | `PRP-01`, `PRP-02`, `INV-01` |
| Section 9 | Reservation and Payment Workflows | Phase 4, 8 | Reservation, UI | `RES-01`, `RES-02`, `RES-03`, `RES-04`, `RES-05` |
| Section 10 | Cancellation, Refund, Amendments, No-show | Phase 4, 5, 8 | Reservation, UI | `CAN-01`, `CAN-02`, `CAN-03`, `CAN-04`, `OPS-05` |
| Section 11 | Check-in, Check-out, Physical Rooms | Phase 3, 5, 8 | Property, Reservation, UI | `OPS-01`, `OPS-02`, `OPS-03`, `OPS-04` |
| Section 12 | Reviews | Phase 6, 8 | Review, Reservation, UI | `REV-01`, `REV-02`, `REV-03`, `REV-04` |
| Section 13 | Invoice | Phase 5, 8 | Reservation, UI | `INV-01` |
| Section 14 | Admin Operational Dashboard | Phase 5, 8 | Reservation, UI | `DSH-01` |
| Section 15 | Detailed Audit Trail | Phase 2-8 | All Services, UI | `AUD-01`, `AUD-02` |
| Section 16 | Inter-service Resilience and Consistency | Phase 4, 7 | All Services, Gateway | Fail-closed and Chaos suites |
| Section 17 | API and Data Contracts | Phase 0, 1-7 | All Services | `api-contract.md` validation |
| Section 18 | Non-functional Requirements | Phase 1-9 | Architecture & Build | SonarQube, Security, JMeter |
| Section 19 | Core Acceptance Scenarios | Phase 9 | Full System | 19 Core Scenarios Suite |
| Section 20 | Delivery & Verification Phases | Phase 0-10 | Full Lifecycle | Complete Phase Gate Signoff |
