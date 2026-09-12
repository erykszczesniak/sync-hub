-- Hub state: per-feed watermarks, the run log, quarantined records and schema-drift events.
-- Written to run unchanged on PostgreSQL and on H2 in PostgreSQL mode (tests / dev profile).

CREATE TABLE feed_watermark (
    feed            VARCHAR(64)              NOT NULL,
    watermark_ts    TIMESTAMP WITH TIME ZONE NOT NULL,
    watermark_key   VARCHAR(128),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_feed_watermark PRIMARY KEY (feed)
);

CREATE TABLE sync_run (
    id               UUID                     NOT NULL,
    feed             VARCHAR(64)              NOT NULL,
    mode             VARCHAR(16)              NOT NULL,
    trigger_type     VARCHAR(16)              NOT NULL,
    status           VARCHAR(16)              NOT NULL,
    started_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at      TIMESTAMP WITH TIME ZONE,
    extracted        INTEGER                  NOT NULL DEFAULT 0,
    transformed      INTEGER                  NOT NULL DEFAULT 0,
    loaded           INTEGER                  NOT NULL DEFAULT 0,
    skipped          INTEGER                  NOT NULL DEFAULT 0,
    quarantined      INTEGER                  NOT NULL DEFAULT 0,
    failed           INTEGER                  NOT NULL DEFAULT 0,
    drift_detected   BOOLEAN                  NOT NULL DEFAULT FALSE,
    watermark_before TIMESTAMP WITH TIME ZONE,
    watermark_after  TIMESTAMP WITH TIME ZONE,
    backfill_from    TIMESTAMP WITH TIME ZONE,
    backfill_to      TIMESTAMP WITH TIME ZONE,
    max_lag_seconds  BIGINT,
    error_message    VARCHAR(2000),
    CONSTRAINT pk_sync_run PRIMARY KEY (id)
);
CREATE INDEX ix_sync_run_feed_started ON sync_run (feed, started_at DESC);
CREATE INDEX ix_sync_run_status ON sync_run (status);

CREATE TABLE quarantined_record (
    id                UUID                     NOT NULL,
    feed              VARCHAR(64)              NOT NULL,
    business_key      VARCHAR(128)             NOT NULL,
    run_id            UUID                     NOT NULL,
    reason            VARCHAR(16)              NOT NULL,
    details           VARCHAR(2000)            NOT NULL,
    payload           TEXT                     NOT NULL,
    source_updated_at TIMESTAMP WITH TIME ZONE,
    quarantined_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    status            VARCHAR(16)              NOT NULL,
    resolved_at       TIMESTAMP WITH TIME ZONE,
    resolution_note   VARCHAR(500),
    CONSTRAINT pk_quarantined_record PRIMARY KEY (id)
);
CREATE INDEX ix_quarantine_feed_status ON quarantined_record (feed, status);
CREATE INDEX ix_quarantine_key ON quarantined_record (feed, business_key);

CREATE TABLE drift_event (
    id                 UUID                     NOT NULL,
    feed               VARCHAR(64)              NOT NULL,
    fingerprint        VARCHAR(256)             NOT NULL,
    kind               VARCHAR(32)              NOT NULL,
    field              VARCHAR(128)             NOT NULL,
    expected           VARCHAR(256),
    actual             VARCHAR(256),
    details            VARCHAR(1000)            NOT NULL,
    affected_records   INTEGER                  NOT NULL DEFAULT 0,
    first_run_id       UUID                     NOT NULL,
    last_run_id        UUID                     NOT NULL,
    source_changed_at  TIMESTAMP WITH TIME ZONE,
    detected_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    last_seen_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    status             VARCHAR(16)              NOT NULL,
    resolved_at        TIMESTAMP WITH TIME ZONE,
    resolution_note    VARCHAR(500),
    CONSTRAINT pk_drift_event PRIMARY KEY (id)
);
CREATE INDEX ix_drift_feed_status ON drift_event (feed, status);
CREATE UNIQUE INDEX ux_drift_open_fingerprint ON drift_event (feed, fingerprint, status, detected_at);
