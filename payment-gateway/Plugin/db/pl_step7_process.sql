-- Step 7: Send Invoice Payment Link process (class com.santhoshbalaguru.paymentlink.process.SendInvoicePaymentLink)
-- Registers the process, its 8 parameters, a menu entry and role access, E-Invoice style.
-- Fix: ShowHelp is now 'Y' (same value the E-Invoice processes use), so the process window opens.
-- Safe to re-run; an existing process and its parameters are repaired.
-- Run pl_step3_columns.sql first (it creates the Resend and DueDate elements).
-- If a step fails partway, run ROLLBACK; before trying again.

BEGIN;

DO $$
BEGIN
    IF (SELECT COUNT(DISTINCT UPPER(ColumnName)) FROM AD_Element
         WHERE UPPER(ColumnName) IN ('C_INVOICE_ID', 'DATEINVOICED', 'C_BPARTNER_ID', 'C_DOCTYPE_ID',
                                     'AD_ORG_ID', 'DUEDATE', 'RESEND', 'R_MAILTEXT_ID')) < 8 THEN
        RAISE EXCEPTION 'Elements missing: run pl_step3_columns.sql first';
    END IF;
END $$;

-- 7.1 process
INSERT INTO AD_Process
    (AD_Process_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     Value, Name, Description, Help, AccessLevel, EntityType, Classname, IsReport, IsDirectPrint,
     IsBetaFunctionality, IsServerProcess, ShowHelp, AD_Process_UU)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Process')::integer, 'N'::varchar), 0, 0, 'Y', NOW(), 100, NOW(), 100,
       'PL_SendInvoicePaymentLink', 'Send Invoice Payment Link',
       'Creates a payment link for the open amount of customer invoices and e-mails the invoice with the link',
       'Choose at least one filter. Invoices that are paid, have nothing open, are credit memos, or have no contact e-mail are skipped. '
       || 'Invoices that already have an active link are skipped unless Resend is set.',
       '3', 'PL', 'com.santhoshbalaguru.paymentlink.process.SendInvoicePaymentLink',
       'N', 'N', 'N', 'N', 'Y', gen_random_uuid()
 WHERE NOT EXISTS (SELECT 1 FROM AD_Process WHERE Value = 'PL_SendInvoicePaymentLink');

-- repair for a process created by the old script (ShowHelp was S)
UPDATE AD_Process
   SET ShowHelp = 'Y', IsServerProcess = 'N'
 WHERE Value = 'PL_SendInvoicePaymentLink'
   AND (ShowHelp IS DISTINCT FROM 'Y' OR IsServerProcess IS DISTINCT FROM 'N');

-- 7.2 parameters
INSERT INTO AD_Process_Para
    (AD_Process_Para_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     Name, Description, AD_Process_ID, SeqNo, AD_Reference_ID, ColumnName, IsCentrallyMaintained,
     FieldLength, IsMandatory, IsRange, DefaultValue, AD_Element_ID, EntityType, AD_Process_Para_UU)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Process_Para')::integer, 'N'::varchar), 0, 0, 'Y', NOW(), 100, NOW(), 100,
       v.name, v.descr, p.AD_Process_ID, v.seq, v.ref, v.col, 'N',
       v.len, v.mand, v.range, v.def, e.AD_Element_ID, 'PL', gen_random_uuid()
  FROM AD_Process p
 CROSS JOIN (VALUES
    (10, 'C_Invoice_ID',  'Invoice',           'Send for this one invoice only (the other filters are then ignored)', 30, 'N', 'N', NULL::text, 10),
    (20, 'DateInvoiced',  'Invoice Date',      'Invoices dated within this range',                                    15, 'Y', 'N', NULL,       7),
    (30, 'C_BPartner_ID', 'Business Partner',  'Invoices of this customer',                                           30, 'N', 'N', NULL,       10),
    (40, 'C_DocType_ID',  'Document Type',     'Invoices of this document type',                                      19, 'N', 'N', NULL,       10),
    (50, 'AD_Org_ID',     'Organization',      'Invoices of this organization',                                       19, 'N', 'N', NULL,       10),
    (60, 'DueDate',       'Due Date',          'Invoices falling due within this range',                              15, 'Y', 'N', NULL,       7),
    (70, 'Resend',        'Resend / Regenerate Link',
         'Yes: also handle invoices that already have an active link (the service reuses it, or replaces it when the open amount changed)',
                                                                                                                      20, 'N', 'Y', 'N',        1),
    (80, 'R_MailText_ID', 'Mail Template',     'Overrides the default template from the Payment Gateway Configuration', 19, 'N', 'N', NULL,      10)
 ) AS v(seq, col, name, descr, ref, range, mand, def, len)
  JOIN AD_Element e ON e.AD_Element_ID = (SELECT MIN(x.AD_Element_ID) FROM AD_Element x
                                           WHERE UPPER(x.ColumnName) = UPPER(v.col))
 WHERE p.Value = 'PL_SendInvoicePaymentLink'
   AND NOT EXISTS (SELECT 1 FROM AD_Process_Para x
                    WHERE x.AD_Process_ID = p.AD_Process_ID AND x.ColumnName = v.col);

-- repair for parameters created by the old script (no element)
UPDATE AD_Process_Para pa
   SET AD_Element_ID = (SELECT MIN(x.AD_Element_ID) FROM AD_Element x
                         WHERE UPPER(x.ColumnName) = UPPER(pa.ColumnName)),
       EntityType = 'PL'
  FROM AD_Process p
 WHERE pa.AD_Process_ID = p.AD_Process_ID
   AND p.Value = 'PL_SendInvoicePaymentLink'
   AND pa.AD_Element_ID IS NULL;

-- 7.3 menu entry
INSERT INTO AD_Menu
    (AD_Menu_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     Name, Description, IsSummary, IsSOTrx, IsReadOnly, Action, AD_Process_ID, EntityType, AD_Menu_UU)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Menu')::integer, 'N'::varchar), 0, 0, 'Y', NOW(), 100, NOW(), 100,
       'Send Invoice Payment Link', 'Create payment links for customer invoices and e-mail them',
       'N', 'Y', 'N', 'P', p.AD_Process_ID, 'PL', gen_random_uuid()
  FROM AD_Process p
 WHERE p.Value = 'PL_SendInvoicePaymentLink'
   AND NOT EXISTS (SELECT 1 FROM AD_Menu m WHERE m.AD_Process_ID = p.AD_Process_ID);

-- 7.4 menu tree: every client's menu tree, in the same folder as the Payment Gateway Configuration entry
INSERT INTO AD_TreeNodeMM
    (AD_Tree_ID, Node_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     Parent_ID, SeqNo, AD_TreeNodeMM_UU)
SELECT t.AD_Tree_ID, m.AD_Menu_ID, t.AD_Client_ID, 0, 'Y', NOW(), 100, NOW(), 100,
       COALESCE((SELECT n.Parent_ID
                   FROM AD_TreeNodeMM n JOIN AD_Menu em ON em.AD_Menu_ID = n.Node_ID
                  WHERE n.AD_Tree_ID = t.AD_Tree_ID
                    AND em.Name = 'Payment Gateway Configuration' AND em.EntityType = 'PL'
                  LIMIT 1), 0),
       999, gen_random_uuid()
  FROM AD_Menu m
  JOIN AD_Tree t ON t.TreeType = 'MM' AND t.IsActive = 'Y' AND t.IsAllNodes = 'Y'
 WHERE m.Name = 'Send Invoice Payment Link' AND m.EntityType = 'PL'
   AND NOT EXISTS (SELECT 1 FROM AD_TreeNodeMM x
                    WHERE x.AD_Tree_ID = t.AD_Tree_ID AND x.Node_ID = m.AD_Menu_ID);

-- 7.5 role access: the roles that can open the Payment Gateway Configuration window
INSERT INTO AD_Process_Access
    (AD_Process_ID, AD_Role_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     IsReadWrite, AD_Process_Access_UU)
SELECT p.AD_Process_ID, wa.AD_Role_ID, wa.AD_Client_ID, 0, 'Y', NOW(), 100, NOW(), 100, 'Y', gen_random_uuid()
  FROM AD_Process p
  JOIN AD_Window w ON w.Name = 'Payment Gateway Configuration' AND w.EntityType = 'PL'
  JOIN AD_Window_Access wa ON wa.AD_Window_ID = w.AD_Window_ID
 WHERE p.Value = 'PL_SendInvoicePaymentLink'
   AND NOT EXISTS (SELECT 1 FROM AD_Process_Access a
                    WHERE a.AD_Process_ID = p.AD_Process_ID AND a.AD_Role_ID = wa.AD_Role_ID);

COMMIT;

-- checks (read only)
-- process (expect 1 row, ShowHelp = Y) and its parameters (expect 8, all with an element)
SELECT p.Value, p.Name, p.Classname, p.ShowHelp,
       (SELECT COUNT(*) FROM AD_Process_Para x WHERE x.AD_Process_ID = p.AD_Process_ID) AS parameters,
       (SELECT COUNT(*) FROM AD_Process_Para x
         WHERE x.AD_Process_ID = p.AD_Process_ID AND x.AD_Element_ID IS NOT NULL) AS with_element
  FROM AD_Process p WHERE p.Value = 'PL_SendInvoicePaymentLink';

-- menu entry (expect one row per client menu tree)
SELECT m.Name, m.Action, n.AD_Tree_ID, n.Parent_ID
  FROM AD_Menu m JOIN AD_TreeNodeMM n ON n.Node_ID = m.AD_Menu_ID
 WHERE m.Name = 'Send Invoice Payment Link' AND m.EntityType = 'PL';

-- roles that can run it
SELECT r.Name AS role, a.IsReadWrite
  FROM AD_Process_Access a
  JOIN AD_Role r ON r.AD_Role_ID = a.AD_Role_ID
  JOIN AD_Process p ON p.AD_Process_ID = a.AD_Process_ID
 WHERE p.Value = 'PL_SendInvoicePaymentLink' ORDER BY r.Name;

-- NEXT: Cache Reset, log in again, open the process from the menu.
