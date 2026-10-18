-- ═══════════════════════════════════════════════════════════════════════════
-- stayease_property_db / data.sql
-- Property Service — Reference seed data (training environment)
--
-- MANUAL EXECUTION (after schema.sql):
--   mysql -u root -p stayease_property_db < database/stayease_property_db/data.sql
-- ═══════════════════════════════════════════════════════════════════════════

SET NAMES utf8mb4;
SET foreign_key_checks = 0;

-- ── Amenities ─────────────────────────────────────────────────────────────
INSERT IGNORE INTO amenities (id, name, icon) VALUES
    (1,  'Free Wi-Fi',           'bi-wifi'),
    (2,  'Swimming Pool',        'bi-water'),
    (3,  'Free Breakfast',       'bi-cup-hot'),
    (4,  'Spa & Wellness',       'bi-heart-pulse'),
    (5,  'Airport Shuttle',      'bi-bus-front'),
    (6,  'Parking',              'bi-p-square'),
    (7,  'Air Conditioning',     'bi-thermometer-snow'),
    (8,  'Gym & Fitness Center', 'bi-trophy'),
    (9,  'Restaurant',           'bi-fork-knife'),
    (10, 'Pet Friendly',         'bi-heart'),
    (11, 'Room Service',         'bi-bell'),
    (12, 'Bar & Lounge',         'bi-cup-straw');

-- ── Hotels ────────────────────────────────────────────────────────────────
INSERT IGNORE INTO hotels (id, name, destination, address, contact_email, contact_phone, description, star_rating, published_rating, review_count) VALUES
    (1,
     'StayEase Grand Goa',
     'Goa',
     '100 Calangute Beach Road, North Goa, Goa - 403516',
     'grand.goa@stayease.local',
     '08322234567',
     'A luxury beachfront property with panoramic Arabian Sea views. Features world-class dining, spa, and direct beach access.',
     5, 0.00, 0),

    (2,
     'StayEase Hillside Retreat Ooty',
     'Ooty',
     '45 Doddabetta Road, Udhagamandalam, Ooty, Tamil Nadu - 643001',
     'hillside.ooty@stayease.local',
     '04232244456',
     'A serene Nilgiri mountain retreat nestled among tea gardens. Offers bonfire evenings, nature trails, and cozy cottage stays.',
     4, 0.00, 0),

    (3,
     'StayEase Heritage Palace Jaipur',
     'Jaipur',
     '12 Civil Lines, Near Jal Mahal, Jaipur, Rajasthan - 302006',
     'palace.jaipur@stayease.local',
     '01412278900',
     'A converted heritage palace blending royal Rajasthani architecture with modern amenities. Walking distance to major landmarks.',
     5, 0.00, 0);

-- ── Hotel Amenities ───────────────────────────────────────────────────────
-- Goa: Wi-Fi, Pool, Breakfast, Spa, Parking, AC, Restaurant, Bar
INSERT IGNORE INTO hotel_amenities (hotel_id, amenity_id) VALUES
    (1, 1), (1, 2), (1, 3), (1, 4), (1, 6), (1, 7), (1, 9), (1, 12);

-- Ooty: Wi-Fi, Breakfast, Parking, AC, Restaurant
INSERT IGNORE INTO hotel_amenities (hotel_id, amenity_id) VALUES
    (2, 1), (2, 3), (2, 6), (2, 7), (2, 9);

-- Jaipur: Wi-Fi, Pool, Breakfast, Spa, Airport Shuttle, Parking, AC, Gym, Restaurant, Room Service
INSERT IGNORE INTO hotel_amenities (hotel_id, amenity_id) VALUES
    (3, 1), (3, 2), (3, 3), (3, 4), (3, 5), (3, 6), (3, 7), (3, 8), (3, 9), (3, 11);

-- ── Room Types ────────────────────────────────────────────────────────────
INSERT IGNORE INTO room_types (id, hotel_id, name, description, max_guests, base_nightly_rate, total_physical_rooms) VALUES
    -- Goa
    (1, 1, 'Deluxe Ocean View',   'King bed with ocean-facing balcony and en-suite bathroom.',         2, 4500.00, 15),
    (2, 1, 'Superior Garden View','Queen bed with garden terrace; pool access included.',               2, 3200.00, 20),
    (3, 1, 'Family Suite',        'Two adjoining rooms with bunk beds and pull-out sofa. Sleeps 4.',    4, 7800.00,  8),

    -- Ooty
    (4, 2, 'Valley View Cottage', 'Private standalone cottage with fireplace and valley views.',        2, 3800.00, 10),
    (5, 2, 'Standard Room',       'Comfortable room with Nilgiri mountain views.',                      2, 2200.00, 20),

    -- Jaipur
    (6, 3, 'Royal Suite',         'Opulent suite with original palace frescoes and private courtyard.', 2, 9500.00,  5),
    (7, 3, 'Deluxe Heritage Room','Heritage room with antique furnishings and modern amenities.',        2, 5200.00, 12),
    (8, 3, 'Standard Heritage',   'Cozy heritage room overlooking the inner courtyard.',                2, 3100.00, 18);

-- ── Physical Rooms ────────────────────────────────────────────────────────
-- Goa — Deluxe Ocean View (room_type_id=1): 15 rooms
INSERT IGNORE INTO physical_rooms (id, hotel_id, room_type_id, room_number, housekeeping_status, operational_status) VALUES
    (101, 1, 1, '101',  'CLEAN', 'OPERATIONAL'),
    (102, 1, 1, '102',  'CLEAN', 'OPERATIONAL'),
    (103, 1, 1, '103',  'CLEAN', 'OPERATIONAL'),
    (104, 1, 1, '104',  'CLEAN', 'OPERATIONAL'),
    (105, 1, 1, '105',  'DIRTY', 'OPERATIONAL'),
    (106, 1, 1, '106',  'CLEAN', 'OPERATIONAL'),
    (107, 1, 1, '107',  'CLEAN', 'OPERATIONAL'),
    (108, 1, 1, '108',  'CLEAN', 'OPERATIONAL'),
    (109, 1, 1, '109',  'CLEAN', 'OPERATIONAL'),
    (110, 1, 1, '110',  'CLEAN', 'OPERATIONAL'),
    (111, 1, 1, '111',  'CLEAN', 'OPERATIONAL'),
    (112, 1, 1, '112',  'CLEAN', 'OPERATIONAL'),
    (113, 1, 1, '113',  'INSPECTING', 'OPERATIONAL'),
    (114, 1, 1, '114',  'CLEAN', 'OPERATIONAL'),
    (115, 1, 1, '115',  'CLEAN', 'OUT_OF_SERVICE');  -- Reduces sellable capacity to 14

-- Goa — Superior Garden View (room_type_id=2): 20 rooms
INSERT IGNORE INTO physical_rooms (id, hotel_id, room_type_id, room_number, housekeeping_status, operational_status) VALUES
    (201, 1, 2, '201', 'CLEAN', 'OPERATIONAL'),
    (202, 1, 2, '202', 'CLEAN', 'OPERATIONAL'),
    (203, 1, 2, '203', 'CLEAN', 'OPERATIONAL'),
    (204, 1, 2, '204', 'DIRTY', 'OPERATIONAL'),
    (205, 1, 2, '205', 'CLEAN', 'OPERATIONAL');
-- (remaining 15 rooms abbreviated for brevity; complete rooms added in Phase 3 data enrichment)

-- Goa — Family Suite (room_type_id=3): 8 rooms
INSERT IGNORE INTO physical_rooms (id, hotel_id, room_type_id, room_number, housekeeping_status, operational_status) VALUES
    (301, 1, 3, '301-S', 'CLEAN', 'OPERATIONAL'),
    (302, 1, 3, '302-S', 'CLEAN', 'OPERATIONAL'),
    (303, 1, 3, '303-S', 'DIRTY', 'OPERATIONAL');

-- Ooty — Valley View Cottage (room_type_id=4): 10 rooms
INSERT IGNORE INTO physical_rooms (id, hotel_id, room_type_id, room_number, housekeeping_status, operational_status) VALUES
    (401, 2, 4, 'C-01', 'CLEAN', 'OPERATIONAL'),
    (402, 2, 4, 'C-02', 'CLEAN', 'OPERATIONAL'),
    (403, 2, 4, 'C-03', 'DIRTY', 'OPERATIONAL'),
    (404, 2, 4, 'C-04', 'CLEAN', 'OPERATIONAL'),
    (405, 2, 4, 'C-05', 'CLEAN', 'OPERATIONAL');

-- Ooty — Standard Room (room_type_id=5): 20 rooms
INSERT IGNORE INTO physical_rooms (id, hotel_id, room_type_id, room_number, housekeeping_status, operational_status) VALUES
    (501, 2, 5, 'S-01', 'CLEAN', 'OPERATIONAL'),
    (502, 2, 5, 'S-02', 'CLEAN', 'OPERATIONAL'),
    (503, 2, 5, 'S-03', 'CLEAN', 'OPERATIONAL');

-- Jaipur — Royal Suite (room_type_id=6): 5 rooms
INSERT IGNORE INTO physical_rooms (id, hotel_id, room_type_id, room_number, housekeeping_status, operational_status) VALUES
    (601, 3, 6, 'RS-01', 'CLEAN', 'OPERATIONAL'),
    (602, 3, 6, 'RS-02', 'CLEAN', 'OPERATIONAL'),
    (603, 3, 6, 'RS-03', 'CLEAN', 'OPERATIONAL');

-- Jaipur — Deluxe Heritage Room (room_type_id=7): 12 rooms
INSERT IGNORE INTO physical_rooms (id, hotel_id, room_type_id, room_number, housekeeping_status, operational_status) VALUES
    (701, 3, 7, 'DH-01', 'CLEAN', 'OPERATIONAL'),
    (702, 3, 7, 'DH-02', 'CLEAN', 'OPERATIONAL'),
    (703, 3, 7, 'DH-03', 'DIRTY', 'OPERATIONAL');

-- Jaipur — Standard Heritage (room_type_id=8): 18 rooms
INSERT IGNORE INTO physical_rooms (id, hotel_id, room_type_id, room_number, housekeeping_status, operational_status) VALUES
    (801, 3, 8, 'SH-01', 'CLEAN', 'OPERATIONAL'),
    (802, 3, 8, 'SH-02', 'CLEAN', 'OPERATIONAL'),
    (803, 3, 8, 'SH-03', 'CLEAN', 'OPERATIONAL');

-- ── Rate Overrides ────────────────────────────────────────────────────────
-- Goa Christmas / New Year Seasonal Override (room_type_id=1)
INSERT IGNORE INTO rate_overrides (id, room_type_id, start_date, end_date, override_type, nightly_rate) VALUES
    (1, 1, '2026-12-20', '2027-01-05', 'SEASONAL', 8500.00),
    (2, 2, '2026-12-20', '2027-01-05', 'SEASONAL', 6000.00),
    (3, 3, '2026-12-20', '2027-01-05', 'SEASONAL', 14000.00);

-- Goa Weekend Override (room_type_id=1,2 — Fri/Sat premium)
INSERT IGNORE INTO rate_overrides (id, room_type_id, start_date, end_date, override_type, nightly_rate) VALUES
    (4, 1, '2026-01-01', '2027-12-31', 'WEEKEND', 5500.00),
    (5, 2, '2026-01-01', '2027-12-31', 'WEEKEND', 4000.00);

-- Jaipur Royal Suite Weekend
INSERT IGNORE INTO rate_overrides (id, room_type_id, start_date, end_date, override_type, nightly_rate) VALUES
    (6, 6, '2026-01-01', '2027-12-31', 'WEEKEND', 11000.00);

SET foreign_key_checks = 1;

SELECT 'stayease_property_db seed data applied successfully.' AS status;
