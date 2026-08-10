-- ============================================================
-- RTO Management System
-- 99_verify.sql
-- Sanity checks after running the schema. Not part of the build.
--
-- Usage (PowerShell has no "<" input redirection — pipe instead):
--     Get-Content 99_verify.sql | mysql -u root -p
-- ============================================================

USE rto_management;

-- Expect: 63
SELECT COUNT(*) AS total_tables
FROM information_schema.tables
WHERE table_schema = 'rto_management' AND table_type = 'BASE TABLE';

-- Every table, with its row count estimate and engine (expect InnoDB throughout)
SELECT table_name, engine, table_comment
FROM information_schema.tables
WHERE table_schema = 'rto_management' AND table_type = 'BASE TABLE'
ORDER BY table_name;

-- Expect: the three generated-column uniqueness guards
--   employee_postings.current_flag / learner_licences.active_flag
--   vehicle_ownerships.current_flag
SELECT table_name, column_name, generation_expression
FROM information_schema.columns
WHERE table_schema = 'rto_management' AND extra LIKE '%GENERATED%';

-- Foreign key inventory
SELECT COUNT(*) AS total_foreign_keys
FROM information_schema.table_constraints
WHERE table_schema = 'rto_management' AND constraint_type = 'FOREIGN KEY';

-- CHECK constraint inventory (date-ordering and slot-capacity guards)
SELECT constraint_name, check_clause
FROM information_schema.check_constraints
WHERE constraint_schema = 'rto_management';
