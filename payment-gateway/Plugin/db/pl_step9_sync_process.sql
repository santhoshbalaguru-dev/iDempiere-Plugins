-- Step 9: Sync Payment Links process (class com.santhoshbalaguru.paymentlink.process.SyncPaymentLinks).
-- No parameters. Menu entry goes in the same folder as Send Invoice Payment Link, and the same roles get access.
-- Run after pl_step7_process.sql. Safe to re-run.
-- If a step fails partway, run ROLLBACK; before trying again.

BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM AD_Process WHERE Value = 'PL_SendInvoicePaymentLink') THEN
        RAISE EXCEPTION 'Run pl_step7_process.sql first';
    END IF;
END $$;

INSERT INTO AD_Process
    (AD_Process_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     Value, Name, Description, Help, AccessLevel, EntityType, Classname, IsReport, IsDirectPrint,
     IsBetaFunctionality, IsServerProcess, ShowHelp, AD_Process_UU)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Process')::integer, 'N'::varchar), 0, 0, 'Y', NOW(), 100, NOW(), 100,
       'PL_SyncPaymentLinks', 'Sync Payment Links',
       'Updates payment links from the payment service and posts received payments as customer payments',
       'Updates status, paid date and payment reference of each link in the Payment Links tab. '
       || 'Creates and completes a payment for every paid link. Links paid with a different amount, or for an invoice that is already paid, '
       || 'are not posted and are marked for review. Safe to run again at any time.',
       '3', 'PL', 'com.santhoshbalaguru.paymentlink.process.SyncPaymentLinks',
       'N', 'N', 'N', 'N', 'Y', gen_random_uuid()
 WHERE NOT EXISTS (SELECT 1 FROM AD_Process WHERE Value = 'PL_SyncPaymentLinks');

INSERT INTO AD_Menu
    (AD_Menu_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     Name, Description, IsSummary, IsSOTrx, IsReadOnly, Action, AD_Process_ID, EntityType, AD_Menu_UU)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Menu')::integer, 'N'::varchar), 0, 0, 'Y', NOW(), 100, NOW(), 100,
       'Sync Payment Links', 'Update payment links from the payment service and post received payments',
       'N', 'Y', 'N', 'P', p.AD_Process_ID, 'PL', gen_random_uuid()
  FROM AD_Process p
 WHERE p.Value = 'PL_SyncPaymentLinks'
   AND NOT EXISTS (SELECT 1 FROM AD_Menu m WHERE m.AD_Process_ID = p.AD_Process_ID);

INSERT INTO AD_TreeNodeMM
    (AD_Tree_ID, Node_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     Parent_ID, SeqNo, AD_TreeNodeMM_UU)
SELECT t.AD_Tree_ID, m.AD_Menu_ID, t.AD_Client_ID, 0, 'Y', NOW(), 100, NOW(), 100,
       COALESCE((SELECT n.Parent_ID
                   FROM AD_TreeNodeMM n JOIN AD_Menu em ON em.AD_Menu_ID = n.Node_ID
                  WHERE n.AD_Tree_ID = t.AD_Tree_ID
                    AND em.Name = 'Send Invoice Payment Link' AND em.EntityType = 'PL'
                  LIMIT 1), 0),
       999, gen_random_uuid()
  FROM AD_Menu m
  JOIN AD_Tree t ON t.TreeType = 'MM' AND t.IsActive = 'Y' AND t.IsAllNodes = 'Y'
 WHERE m.Name = 'Sync Payment Links' AND m.EntityType = 'PL'
   AND NOT EXISTS (SELECT 1 FROM AD_TreeNodeMM x
                    WHERE x.AD_Tree_ID = t.AD_Tree_ID AND x.Node_ID = m.AD_Menu_ID);

INSERT INTO AD_Process_Access
    (AD_Process_ID, AD_Role_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     IsReadWrite, AD_Process_Access_UU)
SELECT p.AD_Process_ID, a.AD_Role_ID, a.AD_Client_ID, 0, 'Y', NOW(), 100, NOW(), 100, 'Y', gen_random_uuid()
  FROM AD_Process p
  JOIN AD_Process sp ON sp.Value = 'PL_SendInvoicePaymentLink'
  JOIN AD_Process_Access a ON a.AD_Process_ID = sp.AD_Process_ID
 WHERE p.Value = 'PL_SyncPaymentLinks'
   AND NOT EXISTS (SELECT 1 FROM AD_Process_Access x
                    WHERE x.AD_Process_ID = p.AD_Process_ID AND x.AD_Role_ID = a.AD_Role_ID);

COMMIT;

-- checks (read only)
-- process (expect 1 row, ShowHelp = Y)
SELECT Value, Name, Classname, ShowHelp FROM AD_Process WHERE Value = 'PL_SyncPaymentLinks';

-- menu entry (expect one row per client menu tree)
SELECT m.Name, m.Action, n.AD_Tree_ID, n.Parent_ID
  FROM AD_Menu m JOIN AD_TreeNodeMM n ON n.Node_ID = m.AD_Menu_ID
 WHERE m.Name = 'Sync Payment Links' AND m.EntityType = 'PL';

-- roles that can run it
SELECT r.Name AS role, a.IsReadWrite
  FROM AD_Process_Access a
  JOIN AD_Role r ON r.AD_Role_ID = a.AD_Role_ID
  JOIN AD_Process p ON p.AD_Process_ID = a.AD_Process_ID
 WHERE p.Value = 'PL_SyncPaymentLinks' ORDER BY r.Name;

-- NEXT: Cache Reset, log in again, open Sync Payment Links from the menu.