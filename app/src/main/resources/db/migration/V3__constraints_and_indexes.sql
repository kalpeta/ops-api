-- V3: constraints + indexes (production-shaped hardening)
-- Mental model:
-- 1) Constraints = correctness (DB is the ultimate guardrail)
-- 2) Indexes = speed (match our most common WHERE + ORDER BY patterns)

-- =========================
-- CUSTOMERS
-- =========================

-- Enforce required fields at DB level
ALTER TABLE customers
    ALTER COLUMN name SET NOT NULL,
    ALTER COLUMN email SET NOT NULL;

-- Unique email: prevents duplicate customer identities
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM   pg_constraint
        WHERE  conname = 'uq_customers_email'
    ) THEN
        ALTER TABLE customers
            ADD CONSTRAINT uq_customers_email UNIQUE (email);
    END IF;
END $$;

-- =========================
-- TASKS
-- =========================

ALTER TABLE tasks
    ALTER COLUMN customer_id SET NOT NULL,
    ALTER COLUMN title SET NOT NULL,
    ALTER COLUMN status SET NOT NULL;

-- FK to customers (if not already present)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM   pg_constraint
        WHERE  conname = 'fk_tasks_customer'
    ) THEN
        ALTER TABLE tasks
            ADD CONSTRAINT fk_tasks_customer
            FOREIGN KEY (customer_id) REFERENCES customers(id)
            ON DELETE CASCADE;
    END IF;
END $$;

-- Index supports:
-- GET /customers/{id}/tasks ordered by created_at desc
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE indexname = 'idx_tasks_customer_created_at'
    ) THEN
        CREATE INDEX idx_tasks_customer_created_at
            ON tasks (customer_id, created_at DESC);
    END IF;
END $$;

-- =========================
-- NOTES
-- =========================

ALTER TABLE notes
    ALTER COLUMN task_id SET NOT NULL,
    ALTER COLUMN body SET NOT NULL;

-- FK to tasks (if not already present)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM   pg_constraint
        WHERE  conname = 'fk_notes_task'
    ) THEN
        ALTER TABLE notes
            ADD CONSTRAINT fk_notes_task
            FOREIGN KEY (task_id) REFERENCES tasks(id)
            ON DELETE CASCADE;
    END IF;
END $$;

-- Index supports:
-- GET /tasks/{taskId}/notes newest-first
-- and helps summary query find latest note per task efficiently
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_indexes
        WHERE indexname = 'idx_notes_task_created_at'
    ) THEN
        CREATE INDEX idx_notes_task_created_at
            ON notes (task_id, created_at DESC);
    END IF;
END $$;