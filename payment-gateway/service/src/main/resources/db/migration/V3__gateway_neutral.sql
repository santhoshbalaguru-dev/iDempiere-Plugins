-- payment_link: neutral column names + which gateway made the link
ALTER TABLE payment_link RENAME COLUMN razorpay_link_id TO gateway_link_id;
ALTER TABLE payment_link RENAME COLUMN razorpay_payment_id TO gateway_payment_id;

ALTER TABLE payment_link ADD COLUMN gateway VARCHAR(30);
UPDATE payment_link SET gateway = 'razorpay';
ALTER TABLE payment_link ALTER COLUMN gateway SET NOT NULL;

-- uniqueness is now per gateway, not global
ALTER TABLE payment_link DROP CONSTRAINT payment_link_razorpay_link_id_key;
ALTER TABLE payment_link DROP CONSTRAINT payment_link_razorpay_payment_id_key;
ALTER TABLE payment_link ADD CONSTRAINT uq_payment_link_gateway_link
    UNIQUE (gateway, gateway_link_id);
ALTER TABLE payment_link ADD CONSTRAINT uq_payment_link_gateway_payment
    UNIQUE (gateway, gateway_payment_id);

-- processed_webhook_event: event IDs are only unique within one gateway
ALTER TABLE processed_webhook_event ADD COLUMN gateway VARCHAR(30);
UPDATE processed_webhook_event SET gateway = 'razorpay';
ALTER TABLE processed_webhook_event ALTER COLUMN gateway SET NOT NULL;
ALTER TABLE processed_webhook_event DROP CONSTRAINT processed_webhook_event_event_id_key;
ALTER TABLE processed_webhook_event ADD CONSTRAINT uq_webhook_gateway_event
    UNIQUE (gateway, event_id);