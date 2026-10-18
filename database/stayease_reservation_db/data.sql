-- ═══════════════════════════════════════════════════════════════════════════
-- stayease_reservation_db / data.sql
-- Reservation Service — Reference seed data
--
-- MANUAL EXECUTION (after schema.sql):
--   mysql -u root -p stayease_reservation_db < database/stayease_reservation_db/data.sql
-- ═══════════════════════════════════════════════════════════════════════════

SET NAMES utf8mb4;

-- The room_type_inventory table is populated lazily by Reservation Service
-- the first time availability is queried or a booking is placed.
-- No seed data is needed in the inventory ledger.

-- Reservation lifecycle records are created at runtime through the API.
-- No sample reservations are seeded to keep the reference database clean
-- for verified end-to-end test scenarios.

SELECT 'stayease_reservation_db seed data applied (no rows — inventory and reservations created at runtime).' AS status;
