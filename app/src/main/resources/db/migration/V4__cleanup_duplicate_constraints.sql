-- Cleanup duplicate constraints/indexes created earlier.
-- Goal: keep one canonical constraint per rule.

-- 1) Customers: drop old/duplicate unique constraint if present
DO $$
BEGIN
    -- if uk_customers_email exists, drop it (keep uq_customers_email)
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_customers_email') THEN
        ALTER TABLE customers DROP CONSTRAINT uk_customers_email;
    END IF;
END $$;

-- 2) Tasks: drop auto-generated duplicate FK if present
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tasks_customer_id_fkey') THEN
        ALTER TABLE tasks DROP CONSTRAINT tasks_customer_id_fkey;
    END IF;
END $$;

-- 3) Notes: drop auto-generated duplicate FK if present
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'notes_task_id_fkey') THEN
        ALTER TABLE notes DROP CONSTRAINT notes_task_id_fkey;
    END IF;
END $$;