-- Tasks belong to a customer
CREATE TABLE tasks (
  id UUID PRIMARY KEY,
  customer_id UUID NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
  title VARCHAR(300) NOT NULL,
  status VARCHAR(30) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

-- Notes belong to a task
CREATE TABLE notes (
  id UUID PRIMARY KEY,
  task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  body TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL
);

-- Indexes to match our queries (customer -> tasks, task -> notes)
CREATE INDEX idx_tasks_customer_created_at ON tasks(customer_id, created_at DESC);
CREATE INDEX idx_notes_task_created_at ON notes(task_id, created_at DESC);