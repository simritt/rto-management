-- ============================================================
-- RTO Management System
-- 00_run_all.sql
-- Runs every schema script in dependency order (parent -> child).
-- Updated to include enhancement files (15-22).
--
-- Usage (from inside the database/ folder):
--
--   PowerShell (no "<" redirection; pipe instead):
--     Get-Content 00_run_all.sql | mysql -u root -p rto_management
--
--   Linux/macOS:
--     mysql -u root -p rto_management < 00_run_all.sql
--
-- SOURCE resolves paths relative to the MySQL working directory.
-- Run this from the database/ folder, or use the full path.
--
-- Run order:
--   Base schema (01-14) → Enhancements (15-20) → [optional seed 22]
-- ============================================================

-- ── Base Schema (original files) ──────────────────────────
SOURCE 01_create_database.sql;
SOURCE 02_reference_tables.sql;
SOURCE 03_identity.sql;
SOURCE 04_office_structure.sql;
SOURCE 05_employees.sql;
SOURCE 06_rbac.sql;
SOURCE 07_applications.sql;
SOURCE 08_licence.sql;
SOURCE 09_vehicles.sql;
SOURCE 10_compliance.sql;
SOURCE 11_permits.sql;
SOURCE 12_violations.sql;
SOURCE 13_payments.sql;
SOURCE 14_support.sql;

-- ── Database Enhancements ─────────────────────────────────
SOURCE constraints/15_constraints.sql;
SOURCE indexes/16_indexes.sql;
SOURCE views/17_views.sql;
SOURCE triggers/18_triggers.sql;
SOURCE procedures/20_procedures.sql;

-- ── Transaction examples (documentation only — not a schema file)
-- SOURCE transactions/19_locking_examples.sql;  -- Run manually

-- ── Advanced queries (documentation only — not a schema file)
-- SOURCE queries/21_advanced_queries.sql;        -- Run manually

-- ── Sample / seed data (optional — for testing only) ──────
-- SOURCE seed/22_sample_data.sql;               -- Uncomment to load

-- ── Verification ──────────────────────────────────────────
SOURCE 99_verify.sql;
