# StayEase Architecture & Implementation Blueprint

**Document Version:** 1.1 (Phase 0 Contract Freeze)  
**Status:** Approved Engineering Blueprint  
**Technology Baseline:** Java 21, Spring Boot 3.2.x, Spring Cloud 2023.0.x (Gateway + Consul), Angular 16.2.x, MySQL 8.x  

---

## 1. System Topology & Architecture Overview

StayEase is designed as a modular, domain-driven microservices architecture communicating via REST APIs over HTTP. External clients interact exclusively through the Spring Cloud Gateway. Service discovery is provided by HashiCorp Consul.

```
                           +------------------------+
                           |  Angular 16.2 Client   |
                           |       (Port 4200)      |
                           +-----------+------------+
                                       |
                                       | HTTP / REST
                                       v
                           +------------------------+
                           |  Spring Cloud Gateway  |
                           |       (Port 8080)      |
                           +-----------+------------+
                                       |
              +------------------------+-----------------------+
              |                        |                       |
              v                        v                       v
     +-----------------+      +-----------------+     +-----------------+
     |  User Service   |      | Property Service|     |Reservation Serv.|
     |   (Port 8081)   |      |   (Port 8082)   |     |   (Port 8083)   |
     +--------+--------+      +--------+--------+     +--------+--------+
              |                        ^                       ^
              |                        |                       |
              |                        +-----------+-----------+
              |             (Internal Capacity/    | (Internal
              |              Pricing/Rooms)        |  Eligibility)
              |                                    |
              |                               +----+------------+
              |                               |  Review Service |
              |                               |   (Port 8084)   |
              |                               +--------+--------+
              |                                        |
              +--------------------+-------------------+
                                   |
                                   v
                      +-------------------------+
                      |   Consul Discovery      |
                      |       (Port 8500)       |
                      +-------------------------+
```

### 1.1 Component Port & Database Allocation
| Service Name | Port | Database Name | Primary Responsibility |
|---|---|---|---|
| **API Gateway** | 8080 | — | Centralized ingress routing, CORS, correlation ID injection |
| **User Service** | 8081 | `stayease_user_db` | User identity, authentication, profile, password reset, tokenVersion |
| **Property Service** | 8082 | `stayease_property_db` | Hotels, room types, physical rooms, rate overrides, amenities, housekeeping |
| **Reservation Service** | 8083 | `stayease_reservation_db` | Nightly room inventory ledger, reservations, payment simulation, check-in/out, invoices |
| **Review Service** | 8084 | `stayease_review_db` | Verified guest reviews, hotel rating aggregation, admin moderation |
| **Consul** | 8500 | — | Service discovery and registration registry |
| **Angular Client** | 4200 | — | Single Page Application (SPA) with guest and admin portals |

---

## 2. Repository Layout & Module Structure

```
j:/Capstone/StayEase/
├── pom.xml                               # Root parent aggregator POM
├── stayease-common/                      # Shared DTOs, security filters, error envelope, correlation ID
├── gateway-service/                      # Spring Cloud Gateway (8080)
├── user-service/                         # User Service (8081)
├── property-service/                     # Property Service (8082)
├── reservation-service/                  # Reservation Service (8083)
├── review-service/                       # Review Service (8084)
├── stayease-ui/                          # Angular 16.2.x frontend (4200)
├── database/                             # Database initialization scripts
│   ├── stayease_user_db/
│   │   ├── schema.sql
│   │   └── data.sql
│   ├── stayease_property_db/
│   │   ├── schema.sql
│   │   └── data.sql
│   ├── stayease_reservation_db/
│   │   ├── schema.sql
│   │   └── data.sql
│   └── stayease_review_db/
│       ├── schema.sql
│       └── data.sql
├── docs/                                 # Architecture, contracts, decisions, runbooks
│   ├── StayEase_SRS_v1.1.docx
│   ├── StayEase_SRS_v1.1.pdf
│   ├── api-contract.md
│   ├── decisions.md
│   ├── implementation-blueprint.md
│   ├── test-plan.md
│   ├── requirement-traceability.md
│   └── implementation-status.md
└── README.md
```

### 2.1 Standard Layered Package Convention
Each Spring Boot microservice enforces standard layered packaging:
- `com.stayease.<service>.config`: Spring configuration, security beans, WebClient, OpenAPI.
- `com.stayease.<service>.controller`: REST controllers exposing endpoints defined in API contract.
- `com.stayease.<service>.dto`: Request and response data transfer objects with Bean Validation annotations.
- `com.stayease.<service>.entity`: JPA entities mapped to domain tables.
- `com.stayease.<service>.repository`: Spring Data JPA repositories with query methods and locking.
- `com.stayease.<service>.service`: Core business logic, transactional workflows, state transitions.
- `com.stayease.<service>.client`: Resilience4j-guarded WebClient interfaces for inter-service communication.
- `com.stayease.<service>.security`: JWT filter, authentication manager, role-based access control.
- `com.stayease.<service>.exception`: Global exception handler (`@RestControllerAdvice`) and domain exceptions.
- `com.stayease.<service>.audit`: Audit trail event listeners and persistence logic.

---

## 3. Database Schemas & Persistence Model

### 3.1 `stayease_user_db` (User Service)
```sql
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    phone VARCHAR(20) NOT NULL,
    address VARCHAR(255) NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'ROLE_USER',
    token_version BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id BIGINT NOT NULL,
    actor_role VARCHAR(20) NOT NULL,
    event_category VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    description VARCHAR(255) NOT NULL,
    before_json JSON NULL,
    after_json JSON NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_timestamp (timestamp),
    INDEX idx_audit_actor (actor_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.2 `stayease_property_db` (Property Service)
```sql
CREATE TABLE IF NOT EXISTS hotels (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    destination VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    contact_email VARCHAR(150) NOT NULL,
    contact_phone VARCHAR(20) NOT NULL,
    description TEXT NULL,
    star_rating INT NOT NULL DEFAULT 3,
    published_rating DECIMAL(3,2) NOT NULL DEFAULT 0.00,
    review_count INT NOT NULL DEFAULT 0,
    image_url VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_hotel_dest (destination),
    INDEX idx_hotel_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS amenities (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    icon VARCHAR(50) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hotel_amenities (
    hotel_id BIGINT NOT NULL,
    amenity_id BIGINT NOT NULL,
    PRIMARY KEY (hotel_id, amenity_id),
    FOREIGN KEY (hotel_id) REFERENCES hotels(id) ON DELETE CASCADE,
    FOREIGN KEY (amenity_id) REFERENCES amenities(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS room_types (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hotel_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    max_guests INT NOT NULL DEFAULT 2,
    base_nightly_rate DECIMAL(10,2) NOT NULL,
    total_physical_rooms INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (hotel_id) REFERENCES hotels(id) ON DELETE CASCADE,
    INDEX idx_rt_hotel (hotel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS physical_rooms (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hotel_id BIGINT NOT NULL,
    room_type_id BIGINT NOT NULL,
    room_number VARCHAR(20) NOT NULL,
    housekeeping_status VARCHAR(20) NOT NULL DEFAULT 'CLEAN',
    operational_status VARCHAR(20) NOT NULL DEFAULT 'OPERATIONAL',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uq_hotel_room_number (hotel_id, room_number),
    FOREIGN KEY (hotel_id) REFERENCES hotels(id) ON DELETE CASCADE,
    FOREIGN KEY (room_type_id) REFERENCES room_types(id) ON DELETE CASCADE,
    INDEX idx_room_readiness (room_type_id, housekeeping_status, operational_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rate_overrides (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_type_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    override_type VARCHAR(20) NOT NULL, -- 'WEEKEND', 'SEASONAL'
    nightly_rate DECIMAL(10,2) NOT NULL,
    FOREIGN KEY (room_type_id) REFERENCES room_types(id) ON DELETE CASCADE,
    INDEX idx_rate_lookup (room_type_id, start_date, end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS property_audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id BIGINT NOT NULL,
    actor_role VARCHAR(20) NOT NULL,
    event_category VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    description VARCHAR(255) NOT NULL,
    before_json JSON NULL,
    after_json JSON NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.3 `stayease_reservation_db` (Reservation Service)
```sql
CREATE TABLE IF NOT EXISTS room_type_inventory (
    room_type_id BIGINT NOT NULL,
    stay_date DATE NOT NULL,
    total_capacity INT NOT NULL,
    reserved_count INT NOT NULL DEFAULT 0,
    hold_count INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (room_type_id, stay_date),
    INDEX idx_inv_date (stay_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS reservations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    public_reference VARCHAR(30) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    hotel_id BIGINT NOT NULL,
    room_type_id BIGINT NOT NULL,
    check_in_date DATE NOT NULL,
    check_out_date DATE NOT NULL,
    nights INT NOT NULL,
    guest_count INT NOT NULL,
    reservation_status VARCHAR(30) NOT NULL, -- PENDING_PAYMENT, CONFIRMED, CHECKED_IN, CHECKED_OUT, CANCELLED, NO_SHOW, PAYMENT_FAILED, RECONCILIATION_REQUIRED
    payment_policy VARCHAR(20) NOT NULL,    -- PREPAID, PAY_LATER
    payment_status VARCHAR(20) NOT NULL,    -- PENDING, PROCESSING, PAID, FAILED, REFUNDED
    hold_expires_at TIMESTAMP NULL,
    payment_due_at TIMESTAMP NULL,
    lodging_subtotal DECIMAL(10,2) NOT NULL,
    tax_percentage DECIMAL(5,2) NOT NULL,
    tax_amount DECIMAL(10,2) NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    price_snapshot_json JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP NULL,
    cancelled_at TIMESTAMP NULL,
    cancellation_reason VARCHAR(100) NULL,
    penalty_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    refund_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    INDEX idx_res_user (user_id),
    INDEX idx_res_hotel_dates (hotel_id, check_in_date, check_out_date),
    INDEX idx_res_status (reservation_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payment_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    transaction_reference VARCHAR(100) NOT NULL UNIQUE,
    payment_method VARCHAR(30) NOT NULL, -- CARD, UPI, NETBANKING
    amount DECIMAL(10,2) NOT NULL,
    payment_status VARCHAR(20) NOT NULL, -- PROCESSING, PAID, FAILED, REFUNDED
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    simulated_outcome VARCHAR(30) NOT NULL,
    FOREIGN KEY (reservation_id) REFERENCES reservations(id) ON DELETE CASCADE,
    INDEX idx_payment_res (reservation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS room_assignments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT NOT NULL UNIQUE,
    physical_room_id BIGINT NOT NULL,
    physical_room_number VARCHAR(20) NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    checked_in_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    checked_out_at TIMESTAMP NULL,
    FOREIGN KEY (reservation_id) REFERENCES reservations(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS reservation_audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id BIGINT NOT NULL,
    actor_role VARCHAR(20) NOT NULL,
    event_category VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    description VARCHAR(255) NOT NULL,
    before_json JSON NULL,
    after_json JSON NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 3.4 `stayease_review_db` (Review Service)
```sql
CREATE TABLE IF NOT EXISTS reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    hotel_id BIGINT NOT NULL,
    guest_name VARCHAR(100) NOT NULL,
    rating INT NOT NULL,
    comment TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED', -- PUBLISHED, REMOVED
    removed_by BIGINT NULL,
    removed_at TIMESTAMP NULL,
    removal_reason VARCHAR(255) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_review_hotel (hotel_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS review_audit_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    actor_id BIGINT NOT NULL,
    actor_role VARCHAR(20) NOT NULL,
    event_category VARCHAR(50) NOT NULL,
    action VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    description VARCHAR(255) NOT NULL,
    before_json JSON NULL,
    after_json JSON NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 4. Inventory Ledger Locking & Consistency Algorithm

To guarantee zero double-booking under concurrent load, Reservation Service implements pessimistic row-level locking:

```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public Reservation createHoldOrReservation(CreateReservationCommand cmd) {
    // 1. Fetch room type sellable capacity from Property Service
    int sellableCapacity = propertyClient.getSellableCapacity(cmd.getRoomTypeId());

    // 2. Lock inventory rows in strict date ASC order to eliminate deadlocks
    List<RoomTypeInventory> ledger = inventoryRepo.findAndLockForDates(
        cmd.getRoomTypeId(), cmd.getCheckInDate(), cmd.getCheckOutDate()
    );

    // 3. For any missing dates in range, initialize row with sellableCapacity
    ensureLedgerCoverage(ledger, cmd.getRoomTypeId(), cmd.getCheckInDate(), cmd.getCheckOutDate(), sellableCapacity);

    // 4. Verify capacity invariant for every night
    for (RoomTypeInventory night : ledger) {
        if (night.getReservedCount() + night.getHoldCount() + cmd.getRoomCount() > night.getTotalCapacity()) {
            throw new InsufficientInventoryException("Sold out for date: " + night.getStayDate());
        }
    }

    // 5. Increment counter based on payment policy
    if (cmd.getPaymentPolicy() == PaymentPolicy.PREPAID) {
        ledger.forEach(night -> night.setHoldCount(night.getHoldCount() + cmd.getRoomCount()));
    } else {
        ledger.forEach(night -> night.setReservedCount(night.getReservedCount() + cmd.getRoomCount()));
    }
    inventoryRepo.saveAll(ledger);

    // 6. Persist Reservation entity with immutable price snapshot
    return reservationRepo.save(buildReservationEntity(cmd, ledger));
}
```

---

## 5. Inter-Service Communication & Failure Recovery Matrix

| Invocation Path | Synchronous Call Purpose | Success Action | Failure / Timeout Action |
|---|---|---|---|
| `Reservation` $\rightarrow$ `Property` | Validate room-type, fetch rates, fetch sellable capacity | Calculate pricing snapshot, reserve inventory | **Fail closed:** Abort reservation creation; return HTTP 503 (`Property Service unavailable`) |
| `Reservation` $\rightarrow$ `Property` | Assign clean room at check-in | Assign physical room number; reservation $\rightarrow$ `CHECKED_IN` | **Fail closed:** Reject check-in (HTTP 503); reservation remains `CONFIRMED` |
| `Reservation` $\rightarrow$ `Property` | Mark physical room `DIRTY` at checkout | Room marked dirty; reservation $\rightarrow$ `CHECKED_OUT` | Retry via local background scheduler if Property Service temporarily down; complete checkout locally |
| `Review` $\rightarrow$ `Reservation` | Verify reservation ownership & checkout status | Permit review creation; publish immediately | **Fail closed:** Reject review submission (HTTP 503); client must retry |
| Any Service $\rightarrow$ `User` | Validate `tokenVersion` for JWT bearer token | Allow execution if tokenVersion matches | **Fail closed:** Deny request with HTTP 401 / 503; do not allow bypass |
| Angular UI $\rightarrow$ Gateway $\rightarrow$ 4 Services | Aggregated Admin Audit Trail | Render unified sorted audit records | **Partial degradation:** Render available logs with warning banner identifying unreachable services |

---

## 6. Dependency-Ordered Implementation Plan

### Stage 1: Reference Application Build Sequence
1. **Phase 0 — Repository Audit & Contract Freeze (Current):**
   - Audit repository, toolchain, requirements baseline.
   - Reconcile architectural discrepancies (`decisions.md`, `api-contract.md`, `implementation-blueprint.md`, `test-plan.md`).
   - Freeze all state machines, DTO schemas, locking models, and traceability matrix.
2. **Phase 1 — Project Skeleton, Maven Multi-Module & SQL Schemas:**
   - Initialize root `pom.xml`, `stayease-common` module, and 5 service directories.
   - Configure Spring Cloud Gateway, Consul client dependencies, MySQL JDBC, and JPA.
   - Generate service-specific `schema.sql` and `data.sql` scripts in `database/`.
   - Implement `AdminBootstrapRunner` for secure administrator seeding without plaintext passwords.
3. **Phase 2 — User Service Implementation & Security Baseline:**
   - Implement entities, repositories, authentication controllers, BCrypt password hashing.
   - Implement JWT provider, token validation filters, and `tokenVersion` increment logic.
   - Implement internal `token-version` endpoint and user audit trail.
   - Execute unit and security tests.
4. **Phase 3 — Property Service Implementation:**
   - Implement Hotel, RoomType, PhysicalRoom, Amenity, and RateOverride entities.
   - Implement catalog search with pagination and attribute filtering.
   - Implement physical room housekeeping and operational state transitions.
   - Implement internal capacity and rate calculation endpoints.
   - Execute unit and repository tests.
5. **Phase 4 — Reservation Service & Core Financial Workflows:**
   - Implement `room_type_inventory` ledger with pessimistic row locking.
   - Implement availability calculation endpoints avoiding circular dependencies.
   - Implement prepaid 10-minute hold and pay-later deadline schedulers.
   - Implement payment simulator with idempotency key enforcement.
   - Implement cancellation policy, penalty calculations, amendments, and price snapshots.
   - Execute concurrency, inventory locking, and payment tests.
6. **Phase 5 — Stay Operations, Invoices & Operational Dashboard:**
   - Implement self-check-in with clean room assignment.
   - Implement self-checkout with automatic room dirty transition.
   - Implement admin no-show handling.
   - Implement on-demand invoice API from stored price snapshot.
   - Implement operational dashboard aggregation.
7. **Phase 6 — Review Service Implementation:**
   - Implement verified reviews with 30-day post-checkout window.
   - Implement inter-service eligibility validation via WebClient.
   - Implement admin review removal and review audit events.
8. **Phase 7 — Gateway Integration & Resilience Hardening:**
   - Configure Spring Cloud Gateway routes, CORS filter, and correlation ID filter.
   - Implement Resilience4j circuit breakers, timeouts, and centralized error envelopes.
   - Test end-to-end routing via Consul service discovery.
9. **Phase 8 — Angular UI Implementation (Port 4200):**
   - Set up Angular 16.2.x workspace with Bootstrap 5.
   - Implement auth interceptor, route guards, guest booking portal, printable invoice.
   - Implement admin dashboard, room management, and aggregated audit history with partial-failure warning.
10. **Phase 9 — Full End-to-End Verification & Defect Triage:**
    - Execute automated test suite across all services.
    - Run Postman collection against gateway for all 19 core acceptance scenarios.
    - Run SonarQube static code analysis.

### Stage 2: Trainee Starter-Code Derivation (Strictly Post-Verification)
11. **Phase 10 — Trainee Starter-Code Derivation:**
    - Derive starter branch from verified reference implementation.
    - Insert structured `// TODO: Trainee Task` placeholders in designated exercises.
    - Package trainee lab guide, educator reference manual, and test harnesses.

---

## 7. Manual Infrastructure Runbook

1. **MySQL Management:**
   - The user manually creates the 4 databases:
     `stayease_user_db`, `stayease_property_db`, `stayease_reservation_db`, `stayease_review_db`.
   - The user executes `schema.sql` and `data.sql` for each service.
   - The agent must never attempt to start, stop, or drop MySQL databases automatically.
2. **Consul Management:**
   - The user manually starts Consul agent on port 8500:
     `consul agent -dev`
   - Services automatically register upon boot and deregister upon shutdown.
3. **Application Boot Sequence:**
   1. User Service (Port 8081)
   2. Property Service (Port 8082)
   3. Reservation Service (Port 8083)
   4. Review Service (Port 8084)
   5. Gateway Service (Port 8080)
   6. Angular Client (Port 4200)
