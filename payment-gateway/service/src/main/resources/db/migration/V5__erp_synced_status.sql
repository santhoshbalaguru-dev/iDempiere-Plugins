ALTER TABLE payment_link ADD COLUMN erp_synced_status VARCHAR(20);

CREATE INDEX idx_payment_link_unsynced
    ON payment_link (ad_client_id, id)
    WHERE status IN ('PAID', 'AMOUNT_MISMATCH', 'EXPIRED', 'CANCELLED');