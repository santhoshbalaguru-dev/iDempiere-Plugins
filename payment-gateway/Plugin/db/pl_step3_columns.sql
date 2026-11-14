-- Step 3: elements and column definitions for the payment link plugin (entity type PL).
-- Same approach as einvoice_setup.sql: AD_Element rows, then AD_Column rows inserted directly.
-- "Create Columns from DB" in the UI is no longer needed.
-- Safe to re-run. It also REPAIRS columns that already exist (IsUpdateable, references, lengths...),
-- so run it again on the database where the old step 3 was used.
-- If a step fails partway, run ROLLBACK; before trying again.

BEGIN;

DO $$
BEGIN
    IF (SELECT COUNT(*) FROM AD_Table WHERE TableName IN ('PL_Config', 'PL_InvoicePaymentLink')) < 2 THEN
        RAISE EXCEPTION 'Run pl_step2_register.sql first (AD_Table rows missing)';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'c_invoice' AND column_name = 'paymentlinkurl') THEN
        RAISE EXCEPTION 'Run pl_step1_tables.sql first (C_Invoice.PaymentLinkURL missing)';
    END IF;
END $$;


-- =====================================================================
-- 3.1 AD_ELEMENT (only the ones that do not exist yet; core elements such as
--     Amount, C_Invoice_ID, C_BankAccount_ID are reused)
-- =====================================================================

INSERT INTO AD_Element
    (AD_Element_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     ColumnName, EntityType, Name, PrintName, Description, Help,
     PO_Name, PO_PrintName, PO_Description, PO_Help, AD_Element_UU, Placeholder)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Element')::integer, 'N'::varchar), 0, 0, 'Y', NOW(), 100, NOW(), 100,
       v.columnname, 'PL', v.name, v.printname, v.description, v.help,
       NULL, NULL, NULL, NULL, gen_random_uuid(), NULL
  FROM (VALUES
    ('PL_Config_ID',  'Payment Gateway Configuration', 'Gateway Configuration', 'Payment service settings used to create payment links', 'Read only key of the configuration record.'),
    ('PL_Config_UU',  'PL_Config_UU', 'PL_Config_UU', 'Immutable universally unique identifier', 'Immutable universally unique identifier'),
    ('Gateway',       'Gateway', 'Gateway', 'Payment gateway used for the link', 'Name of the gateway in the payment service, for example razorpay.'),
    ('ServiceUrl',    'Service URL', 'Service URL', 'Base URL of the payment service', 'Address of the payment service that creates the links.'),
    ('ServiceApiKey', 'Service API Key', 'API Key', 'API key for the payment service', 'Sent with every call to the payment service. Encrypted in the database.'),
    ('ExpiryDays',    'Expiry Days', 'Expiry Days', 'Days until a new payment link expires', 'Applied when a new link is created.'),
    ('PL_InvoicePaymentLink_ID', 'Invoice Payment Link', 'Payment Link', 'A payment link created for an invoice', 'Created by the Send Invoice Payment Link process. Read only.'),
    ('PL_InvoicePaymentLink_UU', 'PL_InvoicePaymentLink_UU', 'PL_InvoicePaymentLink_UU', 'Immutable universally unique identifier', 'Immutable universally unique identifier'),
    ('GatewayLinkRef',    'Gateway Link Ref', 'Link Ref', 'Link reference at the gateway', 'Reference returned by the gateway for this link. Read only.'),
    ('LinkURL',           'Link URL', 'Link URL', 'Address the customer opens to pay', 'Read only.'),
    ('ExpireBy',          'Expires', 'Expires', 'Date and time the link expires', 'Read only.'),
    ('LinkStatus',        'Link Status', 'Status', 'Status of the payment link', 'CREATED, PAID, EXPIRED, CANCELLED, FAILED or AMOUNT_MISMATCH. Read only.'),
    ('PaidDate',          'Paid Date', 'Paid Date', 'Date and time the link was paid', 'Read only.'),
    ('GatewayPaymentRef', 'Gateway Payment Ref', 'Payment Ref', 'Payment reference at the gateway', 'Read only.'),
    ('ServiceLinkNo',     'Service Link No', 'Service Link No', 'Link number in the payment service', 'Each service link is recorded in iDempiere only once. Read only.'),
    ('ErrorMessage',      'Error Message', 'Error Message', 'Error reported for this link', 'Read only.'),
    ('PaymentLinkURL',    'Payment Link URL', 'Payment Link URL', 'Current payment link of the invoice', 'Available in mail templates as @PaymentLinkURL@. Read only.'),
    ('Resend',            'Resend / Regenerate Link', 'Resend', 'Also handle invoices that already have an active link', 'Yes: the service reuses the active link, or replaces it when the open amount changed.'),
    ('DueDate',           'Due Date', 'Due Date', 'Due date of the invoice', 'Invoices falling due within the range are selected.')
  ) AS v(columnname, name, printname, description, help)
 WHERE NOT EXISTS (SELECT 1 FROM AD_Element e WHERE UPPER(e.ColumnName) = UPPER(v.columnname));


-- =====================================================================
-- 3.2 COLUMN DEFINITIONS (one row per column; used for both insert and repair)
-- =====================================================================

DROP TABLE IF EXISTS pg_temp.pl_cols;
CREATE TEMP TABLE pl_cols (
    tbl text, col text, ref int, len int, mand char(1), def text, upd char(1),
    is_key char(1), is_parent char(1), enc char(1), ident char(1), seq int, refval text
);

-- standard columns, for both tables
INSERT INTO pl_cols (tbl, col, ref, len, mand, def, upd, is_key, is_parent, enc, ident, seq, refval)
SELECT t.tbl, s.col, s.ref, s.len, s.mand, s.def, s.upd, 'N', 'N', 'N', 'N', 0, NULL
  FROM (VALUES ('PL_Config'), ('PL_InvoicePaymentLink')) AS t(tbl)
 CROSS JOIN (VALUES
    ('AD_Client_ID', 19, 10, 'Y', '@#AD_Client_ID@', 'N'),
    ('AD_Org_ID',    19, 10, 'Y', '@#AD_Org_ID@',    'Y'),
    ('IsActive',     20,  1, 'Y', 'Y',               'Y'),
    ('Created',      16,  7, 'Y', NULL,              'N'),
    ('CreatedBy',    18, 10, 'Y', NULL,              'N'),
    ('Updated',      16,  7, 'Y', NULL,              'N'),
    ('UpdatedBy',    18, 10, 'Y', NULL,              'N')
 ) AS s(col, ref, len, mand, def, upd);

-- plugin columns:  tbl, col, ref, len, mand, def, upd, key, parent, enc, ident, seq, refval
INSERT INTO pl_cols (tbl, col, ref, len, mand, def, upd, is_key, is_parent, enc, ident, seq, refval) VALUES
 ('PL_Config', 'PL_Config_ID',     13, 22,  'Y', NULL,       'N', 'Y', 'N', 'N', 'N', 0,  NULL),
 ('PL_Config', 'PL_Config_UU',     10, 36,  'N', NULL,       'N', 'N', 'N', 'N', 'N', 0,  NULL),
 ('PL_Config', 'Gateway',          10, 30,  'Y', 'razorpay', 'Y', 'N', 'N', 'N', 'Y', 10, NULL),
 ('PL_Config', 'ServiceUrl',       10, 200, 'Y', NULL,       'Y', 'N', 'N', 'N', 'N', 0,  NULL),
 ('PL_Config', 'ServiceApiKey',    10, 255, 'Y', NULL,       'Y', 'N', 'N', 'Y', 'N', 0,  NULL),
 ('PL_Config', 'C_BankAccount_ID', 19, 10,  'Y', NULL,       'Y', 'N', 'N', 'N', 'N', 0,  NULL),
 ('PL_Config', 'R_MailText_ID',    19, 10,  'N', NULL,       'Y', 'N', 'N', 'N', 'N', 0,  NULL),
 ('PL_Config', 'ExpiryDays',       11, 10,  'Y', '15',       'Y', 'N', 'N', 'N', 'N', 0,  NULL),
 ('PL_InvoicePaymentLink', 'PL_InvoicePaymentLink_ID', 13, 22,  'Y', NULL, 'N', 'Y', 'N', 'N', 'N', 0,  NULL),
 ('PL_InvoicePaymentLink', 'PL_InvoicePaymentLink_UU', 10, 36,  'N', NULL, 'N', 'N', 'N', 'N', 'N', 0,  NULL),
 ('PL_InvoicePaymentLink', 'C_Invoice_ID',      19, 10,   'Y', NULL, 'N', 'N', 'Y', 'N', 'N', 0,  NULL),
 ('PL_InvoicePaymentLink', 'Gateway',           10, 30,   'Y', NULL, 'Y', 'N', 'N', 'N', 'N', 0,  NULL),
 ('PL_InvoicePaymentLink', 'GatewayLinkRef',    10, 60,   'N', NULL, 'Y', 'N', 'N', 'N', 'Y', 10, NULL),
 ('PL_InvoicePaymentLink', 'LinkURL',           10, 255,  'N', NULL, 'Y', 'N', 'N', 'N', 'N', 0,  NULL),
 ('PL_InvoicePaymentLink', 'Amount',            12, 22,   'Y', NULL, 'Y', 'N', 'N', 'N', 'N', 0,  NULL),
 ('PL_InvoicePaymentLink', 'C_Currency_ID',     19, 10,   'Y', NULL, 'Y', 'N', 'N', 'N', 'N', 0,  NULL),
 ('PL_InvoicePaymentLink', 'ExpireBy',          16, 7,    'N', NULL, 'Y', 'N', 'N', 'N', 'N', 0,  NULL),
 ('PL_InvoicePaymentLink', 'LinkStatus',        17, 20,   'Y', NULL, 'Y', 'N', 'N', 'N', 'N', 0,  'PL_LinkStatus'),
 ('PL_InvoicePaymentLink', 'PaidDate',          16, 7,    'N', NULL, 'Y', 'N', 'N', 'N', 'N', 0,  NULL),
 ('PL_InvoicePaymentLink', 'GatewayPaymentRef', 10, 60,   'N', NULL, 'Y', 'N', 'N', 'N', 'N', 0,  NULL),
 ('PL_InvoicePaymentLink', 'C_Payment_ID',      19, 10,   'N', NULL, 'Y', 'N', 'N', 'N', 'N', 0,  NULL),
 ('PL_InvoicePaymentLink', 'ServiceLinkNo',     11, 10,   'Y', NULL, 'Y', 'N', 'N', 'N', 'N', 0,  NULL),
 ('PL_InvoicePaymentLink', 'ErrorMessage',      14, 2000, 'N', NULL, 'Y', 'N', 'N', 'N', 'N', 0,  NULL),
 ('C_Invoice',             'PaymentLinkURL',    10, 255,  'N', NULL, 'Y', 'N', 'N', 'N', 'N', 0,  NULL);


-- =====================================================================
-- 3.3 AD_COLUMN: insert the ones that do not exist yet
-- =====================================================================

INSERT INTO AD_Column
    (AD_Column_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     Name, Description, Help, Version, EntityType, ColumnName, AD_Table_ID, AD_Reference_ID, AD_Reference_Value_ID,
     FieldLength, DefaultValue, IsKey, IsParent, IsMandatory, IsUpdateable, IsIdentifier, SeqNo, IsTranslated, IsEncrypted,
     IsSelectionColumn, AD_Element_ID, IsSyncDatabase, IsAlwaysUpdateable, IsAllowLogging, IsAllowCopy, AD_Column_UU)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Column')::integer, 'N'::varchar), 0, 0, 'Y', NOW(), 100, NOW(), 100,
       e.Name, e.Description, e.Help, 0, 'PL', m.col, t.AD_Table_ID, m.ref,
       CASE WHEN m.col IN ('CreatedBy', 'UpdatedBy') THEN 110
            ELSE (SELECT MIN(r.AD_Reference_ID) FROM AD_Reference r
                   WHERE r.Name = m.refval AND r.EntityType = 'PL') END,
       m.len, m.def, m.is_key, m.is_parent, m.mand, m.upd, m.ident, m.seq, 'N', m.enc,
       'N', e.AD_Element_ID, 'N', 'N',
       CASE WHEN m.tbl = 'C_Invoice' THEN 'Y' ELSE 'N' END, 'N', gen_random_uuid()
  FROM pl_cols m
  JOIN AD_Table t   ON UPPER(t.TableName) = UPPER(m.tbl)
  JOIN AD_Element e ON e.AD_Element_ID = (SELECT MIN(x.AD_Element_ID) FROM AD_Element x
                                           WHERE UPPER(x.ColumnName) = UPPER(m.col))
 WHERE NOT EXISTS (SELECT 1 FROM AD_Column c
                    WHERE c.AD_Table_ID = t.AD_Table_ID AND UPPER(c.ColumnName) = UPPER(m.col));


-- =====================================================================
-- 3.4 AD_COLUMN: repair columns that already exist (for example created by
--     "Create Columns from DB"). This is what makes Amount (and the other
--     link columns) updateable.
-- =====================================================================

UPDATE AD_Column c
   SET ColumnName            = m.col,
       AD_Reference_ID       = m.ref,
       FieldLength           = m.len,
       IsMandatory           = m.mand,
       DefaultValue          = m.def,
       IsUpdateable          = m.upd,
       IsKey                 = m.is_key,
       IsParent              = m.is_parent,
       IsEncrypted           = m.enc,
       IsIdentifier          = m.ident,
       SeqNo                 = CASE WHEN m.ident = 'Y' THEN 10 ELSE c.SeqNo END,
       AD_Reference_Value_ID = CASE WHEN m.col IN ('CreatedBy', 'UpdatedBy') THEN 110
                                    ELSE COALESCE((SELECT MIN(r.AD_Reference_ID) FROM AD_Reference r
                                                    WHERE r.Name = m.refval AND r.EntityType = 'PL'),
                                                  c.AD_Reference_Value_ID) END,
       EntityType            = 'PL'
  FROM pl_cols m
  JOIN AD_Table t ON UPPER(t.TableName) = UPPER(m.tbl)
 WHERE c.AD_Table_ID = t.AD_Table_ID
   AND UPPER(c.ColumnName) = UPPER(m.col);

COMMIT;


-- =====================================================================
-- 3.5 CHECKS (read only)
-- =====================================================================

-- columns per table. Expect PL_Config 15, PL_InvoicePaymentLink 22, C_Invoice 1
SELECT t.TableName, COUNT(*) AS columns
  FROM AD_Column c JOIN AD_Table t ON t.AD_Table_ID = c.AD_Table_ID
 WHERE c.EntityType = 'PL' GROUP BY t.TableName ORDER BY t.TableName;

-- Amount and the other link columns must show IsUpdateable = Y
SELECT t.TableName, c.ColumnName, r.Name AS RefType, c.FieldLength,
       c.IsMandatory, c.IsUpdateable, c.IsEncrypted
  FROM AD_Column c
  JOIN AD_Table t ON t.AD_Table_ID = c.AD_Table_ID
  JOIN AD_Reference r ON r.AD_Reference_ID = c.AD_Reference_ID
 WHERE c.EntityType = 'PL'
 ORDER BY t.TableName, c.ColumnName;

-- every column has a database column. Expect 0 rows
SELECT t.TableName, c.ColumnName AS missing_in_database
  FROM AD_Column c
  JOIN AD_Table t ON t.AD_Table_ID = c.AD_Table_ID
 WHERE c.EntityType = 'PL'
   AND NOT EXISTS (SELECT 1 FROM information_schema.columns ic
                    WHERE ic.table_name = LOWER(t.TableName) AND ic.column_name = LOWER(c.ColumnName));

-- NEXT: run pl_step4_windows.sql, pl_step5_menu_access.sql, pl_step7_process.sql
-- then System Admin > General Rules > Sequence Check, Cache Reset, and log in again.
