-- ============================================================
-- RTO Management System
-- 00_run_all.sql
-- Runs every schema script in dependency order (parent -> child).
--
-- Usage (from inside the database/ folder).
-- PowerShell has no "<" input redirection, so pipe the file instead:
--     Get-Content 00_run_all.sql | mysql -u root -p
--
-- SOURCE resolves paths relative to the shell's working directory, so run
-- this from the database/ folder or the SOURCE lines will not be found.
--
-- Schema only: no triggers, no procedures, no sample data yet.
-- ============================================================

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
