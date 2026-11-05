-- =========================================================================
-- USER SERVICE DATABASE (MySQL)
-- Pattern aligned with CourseCart reference architecture
--
-- MANUAL EXECUTION:
--   mysql -u root -p < backend/user-service/src/main/resources/user_db.sql
-- =========================================================================
CREATE DATABASE IF NOT EXISTS stayease_user_db;
USE stayease_user_db;

SET NAMES utf8mb4;
SET time_zone = '+00:00';
SET foreign_key_checks = 0;

-- ── users ────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    full_name       VARCHAR(100) NOT NULL,
    email           VARCHAR(150) NOT NULL,
    phone           VARCHAR(20)  NOT NULL,
    address         VARCHAR(255)     NULL,
    password_hash   VARCHAR(255) NOT NULL  COMMENT 'BCrypt-hashed password; never store plaintext',
    role            VARCHAR(20)  NOT NULL DEFAULT 'ROLE_USER'
                    COMMENT 'ROLE_USER or ROLE_ADMIN; public registration always assigns ROLE_USER',
    token_version   BIGINT       NOT NULL DEFAULT 1
                    COMMENT 'Incremented on password reset and email change to invalidate existing JWTs',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_user_email (email),
    INDEX idx_user_role (role)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Guest and administrator accounts';

-- ── user_audit_events ────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS user_audit_events (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    actor_id        BIGINT       NOT NULL COMMENT 'User ID of the actor; 0 for SYSTEM events',
    actor_role      VARCHAR(20)  NOT NULL COMMENT 'USER | ADMIN | SYSTEM',
    event_category  VARCHAR(50)  NOT NULL COMMENT 'e.g. AUTH, ACCOUNT',
    action          VARCHAR(100) NOT NULL COMMENT 'e.g. USER_REGISTERED, PASSWORD_RESET',
    entity_id       VARCHAR(100) NOT NULL COMMENT 'Primary entity involved (usually user ID)',
    description     VARCHAR(255) NOT NULL,
    before_json     JSON             NULL COMMENT 'State snapshot before the change; null for creation events',
    after_json      JSON             NULL COMMENT 'State snapshot after the change; null for deletion events',
    timestamp       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_audit_user_timestamp (timestamp),
    INDEX idx_audit_user_actor (actor_id),
    INDEX idx_audit_user_category (event_category)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Security and account audit trail owned by User Service';

SET foreign_key_checks = 1;

-- ── Seed Data Note ───────────────────────────────────────────────────────
-- Administrator account is initialized via AdminBootstrapRunner on first launch.
-- Guest accounts are created via POST /api/auth/register.
SELECT 'stayease_user_db schema and initialization applied successfully.' AS status;
