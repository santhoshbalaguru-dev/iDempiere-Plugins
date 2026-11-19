CREATE TABLE payment_link (
    id                   BIGSERIAL    PRIMARY KEY,
    gateway              VARCHAR(30)  NOT NULL,
    ad_client_id         INTEGER      NOT NULL,
    ad_org_id            INTEGER      NOT NULL,
    invoice_id           INTEGER      NOT NULL,
    invoice_document_no  VARCHAR(60)  NOT NULL,
    gateway_link_id      VARCHAR(60),
    short_url            VARCHAR(255),
    amount_paise         BIGINT       NOT NULL CHECK (amount_paise > 0),
    currency             VARCHAR(3)   NOT NULL,
    status               VARCHAR(20)  NOT NULL,
    expire_by            TIMESTAMPTZ,
    gateway_payment_id   VARCHAR(60),
    error_message        TEXT,
    paid_at              TIMESTAMPTZ,
    erp_posted_at        TIMESTAMPTZ,
    erp_payment_id       INTEGER,
    erp_synced_status    VARCHAR(20),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- uniqueness is per gateway, not global
    CONSTRAINT uq_payment_link_gateway_link    UNIQUE (gateway, gateway_link_id),
    CONSTRAINT uq_payment_link_gateway_payment UNIQUE (gateway, gateway_payment_id)
);

CREATE INDEX idx_payment_link_invoice ON payment_link (invoice_id, status);

-- one active link per invoice
CREATE UNIQUE INDEX uq_payment_link_active_invoice
    ON payment_link (invoice_id)
    WHERE status IN ('CREATING', 'CREATED');

-- links whose status iDempiere has not confirmed yet (GET /payment-links/unsynced)
CREATE INDEX idx_payment_link_unsynced
    ON payment_link (ad_client_id, id)
    WHERE status IN ('PAID', 'AMOUNT_MISMATCH', 'EXPIRED', 'CANCELLED');

CREATE TABLE processed_webhook_event (
    id            BIGSERIAL    PRIMARY KEY,
    gateway       VARCHAR(30)  NOT NULL,
    event_id      VARCHAR(100) NOT NULL,
    event_type    VARCHAR(60)  NOT NULL,
    received_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- event IDs are only unique within one gateway
    CONSTRAINT uq_webhook_gateway_event UNIQUE (gateway, event_id)
);