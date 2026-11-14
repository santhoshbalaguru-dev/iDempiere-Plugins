-- Step 6: read-only checks. Nothing is changed.

-- tabs and field counts (expect: configuration tab 10, Payment Links tab 17)
SELECT w.Name AS window, tb.Name AS tab, tb.TabLevel, tb.IsReadOnly, COUNT(f.AD_Field_ID) AS fields
  FROM AD_Tab tb
  JOIN AD_Window w ON w.AD_Window_ID = tb.AD_Window_ID
  LEFT JOIN AD_Field f ON f.AD_Tab_ID = tb.AD_Tab_ID
 WHERE tb.EntityType = 'PL'
 GROUP BY w.Name, tb.Name, tb.TabLevel, tb.IsReadOnly;

-- PaymentLinkURL on the invoice header (expect 1 row, read only)
SELECT f.Name, f.IsDisplayed, f.IsReadOnly, f.SeqNo
  FROM AD_Field f JOIN AD_Column c ON c.AD_Column_ID = f.AD_Column_ID
 WHERE c.ColumnName = 'PaymentLinkURL' AND f.EntityType = 'PL';

-- menu entry (expect 1 row)
SELECT m.Name, n.AD_Tree_ID, n.Parent_ID, n.SeqNo
  FROM AD_Menu m JOIN AD_TreeNodeMM n ON n.Node_ID = m.AD_Menu_ID
 WHERE m.EntityType = 'PL';

-- roles that can open the window
SELECT r.Name AS role, wa.IsReadWrite
  FROM AD_Window_Access wa
  JOIN AD_Role r ON r.AD_Role_ID = wa.AD_Role_ID
  JOIN AD_Window w ON w.AD_Window_ID = wa.AD_Window_ID
 WHERE w.EntityType = 'PL' ORDER BY r.Name;
