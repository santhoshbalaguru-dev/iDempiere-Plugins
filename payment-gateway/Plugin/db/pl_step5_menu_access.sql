-- Step 5: menu entry for the Payment Gateway Configuration window (placed in the same folder as the
-- E-Invoice configuration entry when there is one) and role access. Safe to re-run.
-- Change against the old step 5: the menu entry now goes into every client's menu tree (with its UU),
-- as in einvoice_setup.sql, not only the System tree.

BEGIN;

INSERT INTO AD_Menu
    (AD_Menu_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     Name, Description, IsSummary, IsSOTrx, IsReadOnly, Action, AD_Window_ID, EntityType, AD_Menu_UU)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Menu')::integer, 'N'::varchar), 0, 0, 'Y', NOW(), 100, NOW(), 100,
       'Payment Gateway Configuration', 'Payment service settings for payment links',
       'N', 'N', 'N', 'W', w.AD_Window_ID, 'PL', gen_random_uuid()
  FROM AD_Window w
 WHERE w.Name = 'Payment Gateway Configuration' AND w.EntityType = 'PL'
   AND NOT EXISTS (SELECT 1 FROM AD_Menu m WHERE m.AD_Window_ID = w.AD_Window_ID);

-- menu tree: every client's menu tree; same folder as the E-Invoice configuration entry, else the root
INSERT INTO AD_TreeNodeMM
    (AD_Tree_ID, Node_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     Parent_ID, SeqNo, AD_TreeNodeMM_UU)
SELECT t.AD_Tree_ID, m.AD_Menu_ID, t.AD_Client_ID, 0, 'Y', NOW(), 100, NOW(), 100,
       COALESCE((SELECT n.Parent_ID
                   FROM AD_TreeNodeMM n JOIN AD_Menu em ON em.AD_Menu_ID = n.Node_ID
                  WHERE n.AD_Tree_ID = t.AD_Tree_ID AND em.Name ILIKE '%E-Invoice%Config%'
                  LIMIT 1), 0),
       999, gen_random_uuid()
  FROM AD_Menu m
  JOIN AD_Tree t ON t.TreeType = 'MM' AND t.IsActive = 'Y' AND t.IsAllNodes = 'Y'
 WHERE m.Name = 'Payment Gateway Configuration' AND m.EntityType = 'PL'
   AND NOT EXISTS (SELECT 1 FROM AD_TreeNodeMM x
                    WHERE x.AD_Tree_ID = t.AD_Tree_ID AND x.Node_ID = m.AD_Menu_ID);

-- roles that can open the E-Invoice configuration window, plus System Administrator
INSERT INTO AD_Window_Access
    (AD_Window_ID, AD_Role_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     IsReadWrite, AD_Window_Access_UU)
SELECT w.AD_Window_ID, r.AD_Role_ID, r.AD_Client_ID, 0, 'Y', NOW(), 100, NOW(), 100, 'Y', gen_random_uuid()
  FROM AD_Window w
  JOIN (SELECT wa.AD_Role_ID
          FROM AD_Window_Access wa JOIN AD_Window ew ON ew.AD_Window_ID = wa.AD_Window_ID
         WHERE ew.Name ILIKE '%E-Invoice%Config%'
        UNION
        SELECT AD_Role_ID FROM AD_Role WHERE Name = 'System Administrator') x ON TRUE
  JOIN AD_Role r ON r.AD_Role_ID = x.AD_Role_ID
 WHERE w.Name = 'Payment Gateway Configuration' AND w.EntityType = 'PL'
   AND NOT EXISTS (SELECT 1 FROM AD_Window_Access a
                    WHERE a.AD_Window_ID = w.AD_Window_ID AND a.AD_Role_ID = r.AD_Role_ID);

COMMIT;
