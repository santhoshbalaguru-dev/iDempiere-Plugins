CREATE TABLE payment_link (
    id                   BIGSERIAL PRIMARY KEY,
    ad_client_id         INTEGER      NOT NULL,
    ad_org_id            INTEGER      NOT NULL,
    invoice_id           INTEGER      NOT NULL,
    invoice_document_no  VARCHAR(60)  NOT NULL,
    razorpay_link_id     VARCHAR(60)  UNIQUE,
    short_url            VARCHAR(255),
    amount_paise         BIGINT       NOT NULL CHECK (amount_paise > 0),
    currency             VARCHAR(3)   NOT NULL,
    status               VARCHAR(20)  NOT NULL,
    expire_by            TIMESTAMPTZ,
    razorpay_payment_id  VARCHAR(60)  UNIQUE,
    error_message        TEXT,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_payment_link_invoice ON payment_link (invoice_id, status);

CREATE TABLE processed_webhook_event (
    id            BIGSERIAL PRIMARY KEY,
    event_id      VARCHAR(100) NOT NULL UNIQUE,
    event_type    VARCHAR(60)  NOT NULL,
    received_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);
