ALTER TABLE payment_link ADD COLUMN paid_at        TIMESTAMPTZ;
ALTER TABLE payment_link ADD COLUMN erp_posted_at  TIMESTAMPTZ;
ALTER TABLE payment_link ADD COLUMN erp_payment_id INTEGER;

CREATE INDEX idx_payment_link_awaiting_posting
    ON payment_link (ad_client_id, id)
    WHERE status = 'PAID' AND erp_posted_at IS NULL;