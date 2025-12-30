CREATE TABLE IF NOT EXISTS outbox_events (
  id            UUID PRIMARY KEY,
  aggregate_type VARCHAR(100) NOT NULL,
  aggregate_id   UUID NOT NULL,
  event_type     VARCHAR(100) NOT NULL,

  -- store JSON as text (simple + portable)
  payload        TEXT NOT NULL,

  -- helpful metadata
  correlation_id VARCHAR(100),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  sent_at        TIMESTAMPTZ NULL,

  attempts       INT NOT NULL DEFAULT 0,
  last_error     TEXT NULL
);

CREATE INDEX IF NOT EXISTS idx_outbox_unsent_created
  ON outbox_events (sent_at, created_at);

CREATE INDEX IF NOT EXISTS idx_outbox_aggregate
  ON outbox_events (aggregate_type, aggregate_id);
