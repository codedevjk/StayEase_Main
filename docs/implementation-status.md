# StayEase Implementation Status & Phase 0 Audit Report

**Phase:** Phase 0 — Repository Audit and Contract Freeze  
**Document Version:** 1.1  
**Timestamp:** 2026-10-18T22:25:00+05:30  
**Status:** Phase 0 Complete — Awaiting Review & Phase 1 Authorization  

---

## 1. Executive Summary

Phase 0 (Repository Audit and Contract Freeze) has been completed in full compliance with the safety constraints, project governance, and technical baselines specified in `StayEase_SRS_v1.1`.

All core source documents have been inspected, analyzed, and reconciled:
- Identified and resolved the architectural discrepancy concerning room-type availability ownership between Property Service and Reservation Service.
- Formally finalized the unidirectional service-to-service call topology to eliminate circular dependencies.
- Frozen all entity status enums, state machines, idempotency rules, and uncertain-payment reconciliation protocols.
- Defined the nightly inventory relational ledger and MySQL pessimistic row-locking algorithm.
- Specified complete request/response DTOs, Bean Validation constraints, pagination envelopes, normalized error envelopes, and correlation ID behaviors for all microservices.
- Constructed a comprehensive Requirement-to-Test Traceability Matrix (RTM) covering all 15 functional domains.
- Established a dependency-ordered reference implementation plan strictly separating the complete reference application verification from the later trainee starter-code phase.

**Strict Safety Gate:** No application implementation code has been generated, no database initialization scripts have been executed, and MySQL and Consul have not been started, stopped, or controlled.

---

## 2. Environment & Toolchain Inspection Findings

The host environment at `j:\Capstone\StayEase` and the underlying Windows operating system were audited for toolchain readiness:

| Toolchain Component | Expected Baseline | Observed Version / Status | Audit Finding |
|---|---|---|---|
| **Java JDK** | Java 21 | `21.0.3 LTS` (Oracle Corporation) | **Pass:** Compatible with Spring Boot 3.x baseline. |
| **Build Tool** | Apache Maven 3.x | `3.9.8` (`C:\Users\jagmo\apache-maven-3.9.8`) | **Pass:** Compatible with Maven multi-module parent POM. |
| **Python** | Python 3.x | `3.12.4` | **Pass:** Available for scripting and document extraction. |
| **Git Version Control**| Git 2.x | `2.49.0.windows.1` | **Pass:** Git client installed. Repository not yet initialized in `j:\Capstone\StayEase`. |
| **Database Server** | MySQL 8.x / 9.x | `mysql.exe 9.3.0` installed in PATH | **Pass:** Binary present. Manual management rule observed; server untouched. |
| **Service Discovery** | Consul | `consul.exe` (2.0.2) installed in PATH | **Pass:** Binary present. Manual management rule observed; daemon untouched. |
| **Frontend Runtime** | Node.js 18+ / npm | `node` / `npm` **Not Found** in PATH | **Action Required for Phase 8:** Node.js / npm must be installed or added to the user's PATH before Phase 8 (Angular UI implementation). Backend phases (Phases 1–7) are unblocked. |

---

## 3. Documents Inspected & Reconciled

The following project documents were cross-referenced and analyzed:
1. `docs/StayEase_SRS_v1.1.docx` and `.pdf`: Requirements baseline (immutably preserved).
2. `docs/StayEase_Implementation_Blueprint_v1.0.docx` and `.pdf`: Architectural design draft.
3. `docs/decisions.md`: Code-ready technical decisions v1.0.
4. `docs/api-contract.md`: Endpoint contract working draft.
5. `docs/test-plan.md`: Preliminary verification checklist.
6. `README.md`: System overview and manual infrastructure constraints.

---

## 4. Key Architectural & Contract Decisions Confirmed

### 4.1 Room-Type Availability Ownership & Route
- **Resolution:** Reservation Service is the sole authoritative owner of room-type availability calculations (`GET /api/reservations/availability` and `GET /api/reservations/room-types/{id}/availability`).
- **Call Topology:** Property Service exposes static metadata and sellable room capacity to Reservation Service via internal endpoints (`/internal/properties/room-types/{id}/capacity`). Property Service **never** calls Reservation Service, preventing circular call chains.

### 4.2 Night Range, Calendar & Timezone
- **Occupancy Interval:** Stays occupy nights according to the half-open interval $[checkInDate, checkOutDate)$. Checkout date is not an occupied night.
- **Timezone:** Standardized on `Asia/Kolkata` (`UTC+05:30`), configured via `stayease.time-zone=Asia/Kolkata`.
- **Operational Hours:** Standard check-in at `14:00:00`; standard check-out at `11:00:00`.
- **Pay-Later Deadline:** `paymentDueAt = min(createdAt + 24 hours, checkInDate at 14:00 local time)`.

### 4.3 Nightly Inventory Ledger & Locking
- Inventory is tracked in `stayease_reservation_db.room_type_inventory` per night.
- Pessimistic row locking (`SELECT ... FOR UPDATE` ordered by `stay_date ASC`) prevents overbooking under concurrent requests.
- Prepaid inventory holds expire after exactly **10 minutes**; released idempotently by a background scheduler.

### 4.4 Reservation & Payment State Machines
- **ReservationStatus:** `PENDING_PAYMENT`, `CONFIRMED`, `CHECKED_IN`, `CHECKED_OUT`, `CANCELLED`, `NO_SHOW`, `PAYMENT_FAILED`, `RECONCILIATION_REQUIRED`.
- **PaymentStatus:** `PENDING`, `PROCESSING`, `PAID`, `FAILED`, `REFUNDED`.
- Uncertain payment simulator outcomes (timeouts) place bookings in `RECONCILIATION_REQUIRED` without premature inventory release or duplicate charge.

### 4.5 Pricing, Taxes & Snapshot Persistence
- Pricing precedence: 1. Seasonal Override > 2. Weekend Override > 3. Base Nightly Rate.
- Configurable tax percentage: default `12.00%`.
- Currency `INR`, represented via `BigDecimal` with 2 decimal places and `RoundingMode.HALF_UP`.
- Immutable price snapshot JSON persisted at booking time; used for all invoices, amendments, and cancellation penalties.

### 4.6 Physical Rooms & Stay Operations
- Physical rooms have `housekeeping_status` (`CLEAN`, `DIRTY`, `INSPECTING`) and `operational_status` (`OPERATIONAL`, `OUT_OF_SERVICE`).
- Only `OUT_OF_SERVICE` rooms reduce sellable room-type capacity.
- Physical room assignment occurs at check-in (not booking). Requires reservation `CONFIRMED` and `PAID`, and a `CLEAN` room. If no room is ready, check-in is rejected (HTTP 409 Conflict) and reservation remains `CONFIRMED`.
- Checkout automatically marks assigned physical room `DIRTY`.

### 4.7 Security & Admin Bootstrap
- 15-minute JWT access tokens signed with 256-bit HMAC-SHA secret (managed via environment variable `JWT_SECRET`).
- `tokenVersion` check enforced by all services via User Service lookup (`GET /internal/users/{id}/token-version`); fails closed on outage.
- Admin bootstrap implemented via `AdminBootstrapRunner` CLI profile; no plaintext passwords stored in SQL seeds or source control.

### 4.8 Review Eligibility & Moderation
- Reviews permitted only for `CHECKED_OUT` reservations within 30 days of checkout.
- Exactly one review per reservation (`UNIQUE` constraint on `reservation_id`).
- Admin removal soft-deletes (`status = 'REMOVED'`) and is logged in the audit trail.

### 4.9 Audit Aggregation
- Each service maintains its own `audit_events` table in its own database.
- Angular client queries all 4 services via Gateway and merges results sorted by timestamp descending.
- UI renders a prominent warning if any service audit endpoint fails, guaranteeing transparency.

---

## 5. Artifacts Created & Updated in Phase 0

| Artifact Path | Action | Description & Purpose |
|---|---|---|
| `docs/decisions.md` | **Updated (v1.1)** | Comprehensive code-ready decisions, locking strategy, state machines, pricing rules. |
| `docs/api-contract.md` | **Updated (v1.1)** | Complete API specifications, request/response DTOs, Bean Validation constraints, pagination envelopes, error responses. |
| `docs/implementation-blueprint.md`| **Updated (v1.1)** | Architecture diagrams, complete MySQL DDL schemas, inter-service resilience matrix, build sequence. |
| `docs/test-plan.md` | **Updated (v1.1)** | Test suites (security, pricing, inventory locking, stay operations, resilience, SonarQube). |
| `docs/requirement-traceability.md`| **Created (v1.1)** | Bidirectional matrix mapping all SRS v1.1 requirements to phases, components, and test IDs. |
| `docs/implementation-status.md` | **Created (v1.1)** | Phase 0 audit report, environment findings, confirmed decisions, and Phase 1 scope. |

---

## 6. Risks, Limitations & Open Items for Review

1. **Frontend Toolchain:** Node.js / npm is not currently installed in the host's system PATH. While this does not impact backend implementation (Phases 1–7), the user should install Node.js 18+ LTS before Phase 8 (Angular UI implementation).
2. **Infrastructure Management Reminder:** In accordance with the manual infrastructure rule, the user will be prompted to manually initialize the MySQL databases using generated `schema.sql` and `data.sql` scripts, and to start MySQL and Consul prior to integration testing in Phase 9.

---

## 7. Proposed Phase 1 Scope (Pending User Approval)

Upon receipt of user approval for Phase 0, Phase 1 will execute the following foundational activities:
1. Initialize Git repository in `j:\Capstone\StayEase` and record baseline commit of frozen contracts.
2. Create root Maven aggregator `pom.xml` with Spring Boot 3.2.x, Spring Cloud 2023.0.x dependency management, Java 21 compiler configuration, and SonarQube plugin.
3. Scaffold `stayease-common` module containing:
   - Shared DTOs (`PageResponse<T>`, `ErrorResponse`, `FieldErrorDto`, `AuditEventDto`).
   - Standard security filters (`JwtTokenProvider`, `JwtAuthenticationFilter`, `CorrelationIdFilter`).
   - Domain enums (`ReservationStatus`, `PaymentStatus`, `PaymentPolicy`, `HousekeepingStatus`, `OperationalStatus`).
4. Generate service-specific DDL and seed scripts in `database/`:
   - `database/stayease_user_db/schema.sql` and `data.sql`
   - `database/stayease_property_db/schema.sql` and `data.sql`
   - `database/stayease_reservation_db/schema.sql` and `data.sql`
   - `database/stayease_review_db/schema.sql` and `data.sql`
5. Scaffold Spring Boot application skeletons for:
   - `gateway-service` (Port 8080)
   - `user-service` (Port 8081)
   - `property-service` (Port 8082)
   - `reservation-service` (Port 8083)
   - `review-service` (Port 8084)
6. Implement `AdminBootstrapRunner` in `user-service` for reproducible administrator credential initialization.

---

## 8. Safety Gate Sign-Off

- [x] Repository inspected; existing work preserved.
- [x] All 6 project documents reconciled and contracts frozen.
- [x] Zero application implementation code generated in Phase 0.
- [x] Zero database scripts executed.
- [x] MySQL and Consul untouched.
- [x] Phase 0 complete and awaiting explicit user authorization.
