CREATE TABLE IF NOT EXISTS processed_events (
  event_id       VARCHAR(100) PRIMARY KEY,
  event_type     VARCHAR(100) NOT NULL,
  correlation_id VARCHAR(100),
  processed_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at
  ON processed_events (processed_at);
