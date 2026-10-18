-- ═══════════════════════════════════════════════════════════════════════════
-- stayease_user_db / data.sql
-- User Service — Reference seed data
--
-- IMPORTANT — ADMIN ACCOUNT:
--   No administrator account is seeded here. The initial ADMIN account
--   must be created via the AdminBootstrapRunner (user-service Phase 2).
--   Seeding an admin with a plaintext or reusable password in this file
--   is explicitly prohibited per docs/decisions.md §7.
--
-- MANUAL EXECUTION (after schema.sql):
--   mysql -u root -p stayease_user_db < database/stayease_user_db/data.sql
-- ═══════════════════════════════════════════════════════════════════════════

SET NAMES utf8mb4;

-- No seed rows required in Phase 1.
-- AdminBootstrapRunner handles the ADMIN seed in Phase 2.
-- Guest accounts are created via the POST /api/auth/register endpoint.

SELECT 'stayease_user_db seed data applied (no rows — use AdminBootstrapRunner for admin account).' AS status;
