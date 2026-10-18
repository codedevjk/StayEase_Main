CREATE DATABASE IF NOT EXISTS stayease_review_db;
USE stayease_review_db;

-- ═══════════════════════════════════════════════════════════════════════════
-- stayease_review_db / schema.sql
-- Review Service — DDL for all tables
--
-- MANUAL EXECUTION:
--   mysql -u root -p stayease_review_db < database/stayease_review_db/schema.sql
-- ═══════════════════════════════════════════════════════════════════════════

SET NAMES utf8mb4;
SET time_zone = '+00:00';
SET foreign_key_checks = 0;

-- ── reviews ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS reviews (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    reservation_id  BIGINT       NOT NULL COMMENT 'Cross-service ID from reservation_service.reservations.id',
    user_id         BIGINT       NOT NULL COMMENT 'Cross-service ID from user_service.users.id',
    hotel_id        BIGINT       NOT NULL COMMENT 'Cross-service ID from property_service.hotels.id',
    guest_name      VARCHAR(100) NOT NULL COMMENT 'Snapshot of guest name at review creation',
    rating          INT          NOT NULL COMMENT 'Integer 1–5 (inclusive)',
    comment         TEXT         NOT NULL COMMENT 'Review text; 10–1000 characters validated at API layer',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHED' COMMENT 'PUBLISHED | REMOVED',
    removed_by      BIGINT           NULL COMMENT 'Admin user_id who removed the review',
    removed_at      TIMESTAMP        NULL,
    removal_reason  VARCHAR(255)     NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_review_reservation (reservation_id) COMMENT 'One review per reservation enforced at DB level',
    INDEX idx_review_hotel_status (hotel_id, status),
    INDEX idx_review_user (user_id),
    CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Verified guest reviews; one per CHECKED_OUT reservation within 30 days';

-- ── review_audit_events ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS review_audit_events (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    actor_id        BIGINT       NOT NULL,
    actor_role      VARCHAR(20)  NOT NULL,
    event_category  VARCHAR(50)  NOT NULL COMMENT 'REVIEW_MODERATION | REVIEW_CREATION',
    action          VARCHAR(100) NOT NULL COMMENT 'REVIEW_PUBLISHED | REVIEW_REMOVED',
    entity_id       VARCHAR(100) NOT NULL,
    description     VARCHAR(255) NOT NULL,
    before_json     JSON             NULL,
    after_json      JSON             NULL,
    timestamp       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_rev_audit_timestamp (timestamp),
    INDEX idx_rev_audit_actor (actor_id),
    INDEX idx_rev_audit_category (event_category)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Review creation and moderation audit trail owned by Review Service';

SET foreign_key_checks = 1;

-- ═══════════════════════════════════════════════════════════════════════════
-- stayease_review_db / data.sql
-- Review Service — Reference seed data
--
-- MANUAL EXECUTION (after schema.sql):
--   mysql -u root -p stayease_review_db < database/stayease_review_db/data.sql
-- ═══════════════════════════════════════════════════════════════════════════

SET NAMES utf8mb4;

-- Reviews are created at runtime through the verified POST /api/reviews endpoint.
-- No sample reviews seeded to preserve clean test scenarios.
-- Reviews require a CHECKED_OUT reservation within 30 days; test data is
-- generated through the full E2E booking workflow during verification (Phase 9).

SELECT 'stayease_review_db seed data applied (no rows — reviews created at runtime via API).' AS status;

