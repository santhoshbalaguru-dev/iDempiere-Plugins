-- Step 4: Payment Gateway Configuration window, the Payment Links tab on window 167
-- (Invoice (Customer)), all fields, and PaymentLinkURL on the invoice header. Safe to re-run.
-- Change against the old step 4: the Payment Links tab now has the link column (C_Invoice_ID) and the
-- where clause, as the E-Invoice Response tab has. An existing tab is updated.
-- If a step fails partway, run ROLLBACK; before trying again.

BEGIN;

DO $$
DECLARE
    v_name TEXT;
BEGIN
    SELECT Name INTO v_name FROM AD_Window WHERE AD_Window_ID = 167;
    IF v_name IS NULL THEN
        RAISE EXCEPTION 'Window 167 not found';
    END IF;
    RAISE NOTICE 'Invoice window 167 is: %', v_name;
END $$;

-- window "Payment Gateway Configuration"
INSERT INTO AD_Window
    (AD_Window_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     Name, Description, WindowType, IsSOTrx, EntityType, IsDefault, IsBetaFunctionality, AD_Window_UU)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Window')::integer, 'N'::varchar), 0, 0, 'Y', NOW(), 100, NOW(), 100,
       'Payment Gateway Configuration', 'Payment service settings used to create and post payment links',
       'M', 'Y', 'PL', 'N', 'N', gen_random_uuid()
 WHERE NOT EXISTS (SELECT 1 FROM AD_Window WHERE Name = 'Payment Gateway Configuration' AND EntityType = 'PL');

-- tabs: the configuration tab, and "Payment Links" as a read-only child tab of window 167
INSERT INTO AD_Tab
    (AD_Tab_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     Name, Description, AD_Table_ID, AD_Window_ID, SeqNo, TabLevel, IsSingleRow, IsReadOnly,
     OrderByClause, AD_Column_ID, WhereClause, EntityType, AD_Tab_UU)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Tab')::integer, 'N'::varchar), 0, 0, 'Y', NOW(), 100, NOW(), 100,
       v.name, v.descr, t.AD_Table_ID, v.win,
       CASE WHEN v.lvl = 0 THEN 10
            ELSE COALESCE((SELECT MAX(SeqNo) FROM AD_Tab WHERE AD_Window_ID = v.win), 0) + 10 END,
       v.lvl, v.single, v.ro, v.orderby,
       (SELECT c.AD_Column_ID FROM AD_Column c
         WHERE c.AD_Table_ID = t.AD_Table_ID AND c.ColumnName = 'C_Invoice_ID'),
       v.wherec, 'PL', gen_random_uuid()
  FROM (VALUES
        ('PL_Config',
         (SELECT AD_Window_ID FROM AD_Window WHERE Name = 'Payment Gateway Configuration' AND EntityType = 'PL'),
         'Payment Gateway Configuration', 'Settings used to create and post payment links',
         0, 'Y', 'N', NULL::text, NULL::text),
        ('PL_InvoicePaymentLink',
         167,
         'Payment Links', 'Payment links sent for this invoice',
         1, 'N', 'Y', 'PL_InvoicePaymentLink.Created DESC',
         'PL_InvoicePaymentLink.C_Invoice_ID=@C_Invoice_ID@')
       ) AS v(tbl, win, name, descr, lvl, single, ro, orderby, wherec)
  JOIN AD_Table t ON t.TableName = v.tbl
 WHERE NOT EXISTS (SELECT 1 FROM AD_Tab x WHERE x.AD_Window_ID = v.win AND x.AD_Table_ID = t.AD_Table_ID);

-- repair for a Payment Links tab created by the old script
UPDATE AD_Tab tb
   SET AD_Column_ID = (SELECT c.AD_Column_ID FROM AD_Column c
                        WHERE c.AD_Table_ID = tb.AD_Table_ID AND c.ColumnName = 'C_Invoice_ID'),
       WhereClause  = 'PL_InvoicePaymentLink.C_Invoice_ID=@C_Invoice_ID@'
 WHERE tb.AD_Window_ID = 167
   AND tb.EntityType = 'PL'
   AND tb.AD_Table_ID = (SELECT AD_Table_ID FROM AD_Table WHERE TableName = 'PL_InvoicePaymentLink');

-- fields for the two new tabs
WITH f(tbl, col, seq, shown) AS (VALUES
    ('PL_Config', 'PL_Config_ID',     0,   'N'),
    ('PL_Config', 'AD_Client_ID',     10,  'Y'),
    ('PL_Config', 'AD_Org_ID',        20,  'Y'),
    ('PL_Config', 'Gateway',          30,  'Y'),
    ('PL_Config', 'ServiceUrl',       40,  'Y'),
    ('PL_Config', 'ServiceApiKey',    50,  'Y'),
    ('PL_Config', 'C_BankAccount_ID', 60,  'Y'),
    ('PL_Config', 'R_MailText_ID',    70,  'Y'),
    ('PL_Config', 'ExpiryDays',       80,  'Y'),
    ('PL_Config', 'IsActive',         999, 'Y'),
    ('PL_InvoicePaymentLink', 'PL_InvoicePaymentLink_ID', 0,   'N'),
    ('PL_InvoicePaymentLink', 'AD_Client_ID',     10,  'Y'),
    ('PL_InvoicePaymentLink', 'AD_Org_ID',        20,  'Y'),
    ('PL_InvoicePaymentLink', 'GatewayLinkRef',   30,  'Y'),
    ('PL_InvoicePaymentLink', 'Gateway',          40,  'Y'),
    ('PL_InvoicePaymentLink', 'LinkURL',          50,  'Y'),
    ('PL_InvoicePaymentLink', 'Amount',           60,  'Y'),
    ('PL_InvoicePaymentLink', 'C_Currency_ID',    70,  'Y'),
    ('PL_InvoicePaymentLink', 'ExpireBy',         80,  'Y'),
    ('PL_InvoicePaymentLink', 'LinkStatus',       90,  'Y'),
    ('PL_InvoicePaymentLink', 'PaidDate',         100, 'Y'),
    ('PL_InvoicePaymentLink', 'GatewayPaymentRef',110, 'Y'),
    ('PL_InvoicePaymentLink', 'C_Payment_ID',     120, 'Y'),
    ('PL_InvoicePaymentLink', 'ServiceLinkNo',    130, 'Y'),
    ('PL_InvoicePaymentLink', 'ErrorMessage',     140, 'Y'),
    ('PL_InvoicePaymentLink', 'C_Invoice_ID',     150, 'N'),
    ('PL_InvoicePaymentLink', 'IsActive',         999, 'Y')
)
INSERT INTO AD_Field
    (AD_Field_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     Name, Description, Help, IsCentrallyMaintained, AD_Tab_ID, AD_Column_ID, IsDisplayed,
     DisplayLength, IsReadOnly, SeqNo, IsSameLine, IsHeading, IsFieldOnly, IsEncrypted,
     EntityType, IsDisplayedGrid, SeqNoGrid, AD_Field_UU)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Field')::integer, 'N'::varchar), 0, 0, 'Y', NOW(), 100, NOW(), 100,
       c.Name, c.Description, c.Help, 'Y', tb.AD_Tab_ID, c.AD_Column_ID, f.shown,
       LEAST(GREATEST(c.FieldLength, 1), 60), 'N', f.seq, 'N', 'N', 'N', 'N',
       'PL', f.shown, f.seq, gen_random_uuid()
  FROM f
  JOIN AD_Table t  ON t.TableName = f.tbl
  JOIN AD_Column c ON c.AD_Table_ID = t.AD_Table_ID AND c.ColumnName = f.col
  JOIN AD_Tab tb   ON tb.AD_Table_ID = t.AD_Table_ID AND tb.EntityType = 'PL'
 WHERE NOT EXISTS (SELECT 1 FROM AD_Field x
                    WHERE x.AD_Tab_ID = tb.AD_Tab_ID AND x.AD_Column_ID = c.AD_Column_ID)
 ORDER BY f.tbl, f.seq;

-- read-only PaymentLinkURL on the header tab of window 167
INSERT INTO AD_Field
    (AD_Field_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     Name, Description, Help, IsCentrallyMaintained, AD_Tab_ID, AD_Column_ID, IsDisplayed,
     DisplayLength, IsReadOnly, SeqNo, IsSameLine, IsHeading, IsFieldOnly, IsEncrypted,
     EntityType, IsDisplayedGrid, SeqNoGrid, AD_Field_UU)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Field')::integer, 'N'::varchar), 0, 0, 'Y', NOW(), 100, NOW(), 100,
       c.Name, c.Description, c.Help, 'Y', tb.AD_Tab_ID, c.AD_Column_ID, 'Y',
       60, 'Y',
       COALESCE((SELECT MAX(SeqNo) FROM AD_Field WHERE AD_Tab_ID = tb.AD_Tab_ID), 0) + 10,
       'N', 'N', 'N', 'N', 'PL', 'N', 0, gen_random_uuid()
  FROM AD_Table t
  JOIN AD_Tab tb   ON tb.AD_Table_ID = t.AD_Table_ID AND tb.TabLevel = 0 AND tb.AD_Window_ID = 167
  JOIN AD_Column c ON c.AD_Table_ID = t.AD_Table_ID AND c.ColumnName = 'PaymentLinkURL'
 WHERE t.TableName = 'C_Invoice'
   AND NOT EXISTS (SELECT 1 FROM AD_Field x
                    WHERE x.AD_Tab_ID = tb.AD_Tab_ID AND x.AD_Column_ID = c.AD_Column_ID);

COMMIT;
