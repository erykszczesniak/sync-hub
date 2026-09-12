-- System B: the destination tables the hub keeps in sync. Each row carries the source's version clock
-- (source_updated_at), a fingerprint of its business content, a hub-side version counter and a soft
-- delete marker, which together make loading idempotent and late-arrival safe.

CREATE TABLE b_customer (
    id                  UUID                     NOT NULL,
    business_key        VARCHAR(128)             NOT NULL,
    email_address       VARCHAR(320)             NOT NULL,
    full_name           VARCHAR(200)             NOT NULL,
    given_name          VARCHAR(100)             NOT NULL,
    family_name         VARCHAR(100)             NOT NULL,
    lifecycle_code      VARCHAR(1)               NOT NULL,
    tier_rank           INTEGER                  NOT NULL,
    address_line        VARCHAR(300)             NOT NULL,
    city                VARCHAR(100)             NOT NULL,
    postal_code         VARCHAR(20)              NOT NULL,
    country_code        VARCHAR(2)               NOT NULL,
    tags_csv            VARCHAR(500)             NOT NULL,
    source_created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    source_updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    content_fingerprint VARCHAR(64)              NOT NULL,
    version             INTEGER                  NOT NULL,
    deleted_at          TIMESTAMP WITH TIME ZONE,
    first_synced_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    last_synced_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_b_customer PRIMARY KEY (id),
    CONSTRAINT ux_b_customer_key UNIQUE (business_key)
);
CREATE INDEX ix_b_customer_updated ON b_customer (source_updated_at);

CREATE TABLE b_order (
    id                  UUID                     NOT NULL,
    business_key        VARCHAR(128)             NOT NULL,
    customer_key        VARCHAR(128)             NOT NULL,
    state_code          VARCHAR(3)               NOT NULL,
    total_minor         BIGINT                   NOT NULL,
    currency            VARCHAR(3)               NOT NULL,
    line_count          INTEGER                  NOT NULL,
    lines_json          TEXT                     NOT NULL,
    placed_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    source_updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    content_fingerprint VARCHAR(64)              NOT NULL,
    version             INTEGER                  NOT NULL,
    deleted_at          TIMESTAMP WITH TIME ZONE,
    first_synced_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    last_synced_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_b_order PRIMARY KEY (id),
    CONSTRAINT ux_b_order_key UNIQUE (business_key)
);
CREATE INDEX ix_b_order_customer ON b_order (customer_key);
CREATE INDEX ix_b_order_updated ON b_order (source_updated_at);
