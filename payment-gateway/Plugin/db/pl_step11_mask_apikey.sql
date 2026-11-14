-- Step 11: show the Service API Key as a password field on the Payment Gateway Configuration window.
-- The column is already encrypted in the database; this only changes how the field is displayed.
-- Safe to re-run.

BEGIN;

UPDATE AD_Field f
   SET IsEncrypted = 'Y'
  FROM AD_Column c
  JOIN AD_Table t ON t.AD_Table_ID = c.AD_Table_ID
 WHERE f.AD_Column_ID = c.AD_Column_ID
   AND t.TableName = 'PL_Config'
   AND c.ColumnName = 'ServiceApiKey'
   AND f.IsEncrypted IS DISTINCT FROM 'Y';

COMMIT;

-- check (read only): expect 1 row, field IsEncrypted = Y, column IsEncrypted = Y
SELECT f.Name AS field, f.IsEncrypted AS field_encrypted, c.IsEncrypted AS column_encrypted
  FROM AD_Field f
  JOIN AD_Column c ON c.AD_Column_ID = f.AD_Column_ID
  JOIN AD_Table t ON t.AD_Table_ID = c.AD_Table_ID
 WHERE t.TableName = 'PL_Config' AND c.ColumnName = 'ServiceApiKey';

-- NEXT: Cache Reset, log in again, open Payment Gateway Configuration.