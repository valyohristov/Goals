-- Some databases record Flyway version 6 without this script being present on the classpath.
-- Repair/sync fixes checksums; schema is ensured idempotently in V7.
SELECT 1;
