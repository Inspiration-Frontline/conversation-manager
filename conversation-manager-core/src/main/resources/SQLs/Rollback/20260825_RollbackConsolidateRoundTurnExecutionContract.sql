-- The contract migration removes historical tables and the empty legacy message table.
-- Restore the verified pre-cutover PostgreSQL backup before redeploying the old application.
DO $$
BEGIN
    RAISE EXCEPTION 'Contract rollback requires restoring the verified pre-consolidation database backup.';
END;
$$;
