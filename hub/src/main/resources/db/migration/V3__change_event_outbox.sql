-- Transactional outbox for change events. A row is written in the same transaction as the System B
-- change it describes, and marked published only after Kafka acknowledged it. This gives at-least-once
-- delivery without a distributed transaction; consumers deduplicate on event_id.

CREATE TABLE change_event_outbox (
    id            UUID                     NOT NULL,
    feed          VARCHAR(64)              NOT NULL,
    business_key  VARCHAR(128)             NOT NULL,
    event_type    VARCHAR(16)              NOT NULL,
    payload       TEXT                     NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at  TIMESTAMP WITH TIME ZONE,
    attempts      INTEGER                  NOT NULL DEFAULT 0,
    last_error    VARCHAR(1000),
    CONSTRAINT pk_change_event_outbox PRIMARY KEY (id)
);
CREATE INDEX ix_outbox_pending ON change_event_outbox (published_at, created_at);
