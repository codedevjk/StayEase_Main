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
