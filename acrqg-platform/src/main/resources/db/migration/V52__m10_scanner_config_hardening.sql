-- =====================================================================
-- V52__m10_scanner_config_hardening.sql
--
-- Keep earlier Flyway migrations immutable and apply scanner hardening as
-- a forward-only data migration. Static scanners now require a real checked
-- out workdir; disable the built-in command templates until checkout support
-- is explicitly configured and validated by an administrator.
-- =====================================================================

UPDATE scanner_config
SET enabled = FALSE
WHERE name IN ('checkstyle', 'eslint', 'pylint', 'semgrep');

-- end V52__m10_scanner_config_hardening.sql
