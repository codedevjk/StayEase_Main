-- ═══════════════════════════════════════════════════════════════════════════
-- stayease_property_db / schema.sql
-- Property Service — DDL for all tables in stayease_property_db
--
-- MANUAL EXECUTION:
--   mysql -u root -p stayease_property_db < database/stayease_property_db/schema.sql
-- ═══════════════════════════════════════════════════════════════════════════

SET NAMES utf8mb4;
SET time_zone = '+00:00';
SET foreign_key_checks = 0;

-- ── hotels ───────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS hotels (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    name             VARCHAR(150)  NOT NULL,
    destination      VARCHAR(100)  NOT NULL,
    address          VARCHAR(255)  NOT NULL,
    contact_email    VARCHAR(150)  NOT NULL,
    contact_phone    VARCHAR(20)   NOT NULL,
    description      TEXT              NULL,
    star_rating      INT           NOT NULL DEFAULT 3,
    published_rating DECIMAL(3,2)  NOT NULL DEFAULT 0.00
                     COMMENT 'Cached average from review_service; updated asynchronously',
    review_count     INT           NOT NULL DEFAULT 0,
    image_url        VARCHAR(255)      NULL,
    created_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_hotel_destination (destination),
    INDEX idx_hotel_name (name),
    INDEX idx_hotel_rating (published_rating)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Hotel properties managed by Property Service';

-- ── amenities ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS amenities (
    id   BIGINT       NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    icon VARCHAR(50)      NULL COMMENT 'Bootstrap icon class or custom icon reference',

    PRIMARY KEY (id),
    UNIQUE KEY uq_amenity_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ── hotel_amenities ───────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS hotel_amenities (
    hotel_id   BIGINT NOT NULL,
    amenity_id BIGINT NOT NULL,

    PRIMARY KEY (hotel_id, amenity_id),
    FOREIGN KEY fk_ha_hotel   (hotel_id)   REFERENCES hotels   (id) ON DELETE CASCADE,
    FOREIGN KEY fk_ha_amenity (amenity_id) REFERENCES amenities(id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ── room_types ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS room_types (
    id                  BIGINT        NOT NULL AUTO_INCREMENT,
    hotel_id            BIGINT        NOT NULL,
    name                VARCHAR(100)  NOT NULL,
    description         TEXT              NULL,
    max_guests          INT           NOT NULL DEFAULT 2,
    base_nightly_rate   DECIMAL(10,2) NOT NULL COMMENT 'INR; BigDecimal with 2dp HALF_UP',
    total_physical_rooms INT          NOT NULL DEFAULT 0
                         COMMENT 'Count of OPERATIONAL physical rooms of this type; drives sellable capacity',
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    FOREIGN KEY fk_rt_hotel (hotel_id) REFERENCES hotels(id) ON DELETE CASCADE,
    INDEX idx_rt_hotel (hotel_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Room type catalog; each type has multiple physical rooms';

-- ── physical_rooms ────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS physical_rooms (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    hotel_id            BIGINT      NOT NULL,
    room_type_id        BIGINT      NOT NULL,
    room_number         VARCHAR(20) NOT NULL COMMENT 'Unique identifier within the hotel (e.g. 101-A)',
    housekeeping_status VARCHAR(20) NOT NULL DEFAULT 'CLEAN'
                        COMMENT 'CLEAN | DIRTY | INSPECTING — affects check-in eligibility only',
    operational_status  VARCHAR(20) NOT NULL DEFAULT 'OPERATIONAL'
                        COMMENT 'OPERATIONAL | OUT_OF_SERVICE — OUT_OF_SERVICE reduces sellable capacity',
    created_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_hotel_room_number (hotel_id, room_number),
    FOREIGN KEY fk_pr_hotel     (hotel_id)     REFERENCES hotels     (id) ON DELETE CASCADE,
    FOREIGN KEY fk_pr_room_type (room_type_id) REFERENCES room_types (id) ON DELETE CASCADE,
    -- Index used for fast clean-room lookup at check-in time
    INDEX idx_room_eligibility (room_type_id, housekeeping_status, operational_status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Individual physical rooms; assigned to guests at check-in (not at booking)';

-- ── rate_overrides ────────────────────────────────────────────────────────
-- Rate precedence (docs/decisions.md §5):
--   SEASONAL override > WEEKEND override > base_nightly_rate
CREATE TABLE IF NOT EXISTS rate_overrides (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    room_type_id  BIGINT        NOT NULL,
    start_date    DATE          NOT NULL,
    end_date      DATE          NOT NULL COMMENT 'Inclusive end date of the override range',
    override_type VARCHAR(20)   NOT NULL COMMENT 'WEEKEND | SEASONAL',
    nightly_rate  DECIMAL(10,2) NOT NULL COMMENT 'INR effective rate for dates in [start_date, end_date]',

    PRIMARY KEY (id),
    FOREIGN KEY fk_ro_room_type (room_type_id) REFERENCES room_types(id) ON DELETE CASCADE,
    -- Index supports efficient per-date rate lookup during pricing calculations
    INDEX idx_rate_lookup (room_type_id, start_date, end_date),
    CONSTRAINT chk_rate_dates CHECK (end_date >= start_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Weekend and seasonal nightly rate overrides per room type';

-- ── property_audit_events ─────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS property_audit_events (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    actor_id        BIGINT       NOT NULL,
    actor_role      VARCHAR(20)  NOT NULL,
    event_category  VARCHAR(50)  NOT NULL COMMENT 'e.g. HOTEL_OPERATION, HOUSEKEEPING, ROOM_OPERATION',
    action          VARCHAR(100) NOT NULL COMMENT 'e.g. HOTEL_CREATED, ROOM_STATUS_CHANGED',
    entity_id       VARCHAR(100) NOT NULL,
    description     VARCHAR(255) NOT NULL,
    before_json     JSON             NULL,
    after_json      JSON             NULL,
    timestamp       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_prop_audit_timestamp (timestamp),
    INDEX idx_prop_audit_actor (actor_id),
    INDEX idx_prop_audit_category (event_category)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Hotel and room operations audit trail owned by Property Service';

SET foreign_key_checks = 1;
