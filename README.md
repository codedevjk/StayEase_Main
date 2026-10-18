# StayEase Reference Implementation

Training-focused multi-hotel reservation system built with Java 21, Spring Boot 3, Angular 16.2.x, MySQL 8, Spring Cloud Gateway, and Consul.

## Delivery stages
1. Reference application implementation.
2. Verification and defect fixing.
3. Trainee starter-code derivation only after verification.

## Services
| Component | Port | Database |
|---|---:|---|
| API Gateway | 8080 | — |
| User Service | 8081 | stayease_user_db |
| Property Service | 8082 | stayease_property_db |
| Reservation Service | 8083 | stayease_reservation_db |
| Review Service | 8084 | stayease_review_db |
| Consul | 8500 | — |
| Angular UI | 4200 | — |

## Manual infrastructure rule
Do not automatically start or stop MySQL or Consul. The user must create databases, execute the service-specific schema/data SQL scripts, start MySQL, and start Consul manually. Whenever SQL scripts change, notify the user to reinitialize the database before integration testing.

## Documents
- `docs/StayEase_SRS_v1.1.pdf` and `.docx` — requirements baseline.
- `docs/decisions.md` — code-ready technical decisions.
- `docs/implementation-blueprint.md` — architecture and build sequence.
- `docs/api-contract.md` — endpoint contract working draft.
- `docs/test-plan.md` — verification checklist.

## Current state
This repository begins the reference-implementation work. A scaffold or generated source is not considered a verified complete application until the full test and integration gate passes.
