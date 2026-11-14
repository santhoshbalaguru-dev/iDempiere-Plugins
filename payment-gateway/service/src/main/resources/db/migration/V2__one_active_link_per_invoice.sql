CREATE UNIQUE INDEX uq_payment_link_active_invoice
    ON payment_link (invoice_id)
    WHERE status IN ('CREATING', 'CREATED');
