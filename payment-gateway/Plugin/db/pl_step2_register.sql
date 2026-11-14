-- Step 2: dictionary registration (entity type PL, PL_LinkStatus list, table records, ID sequences).
-- Safe to re-run. If a step fails partway, run ROLLBACK; before trying again.

BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM AD_SysConfig
                WHERE Name ILIKE '%NATIVE_SEQUENCE%' AND UPPER(Value) = 'Y') THEN
        RAISE EXCEPTION 'Native sequences are enabled; this script does not support them';
    END IF;
END $$;

-- entity type
INSERT INTO AD_EntityType
    (AD_EntityType_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     Name, Description, EntityType, Version, AD_EntityType_UU)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_EntityType')::integer, 'N'::varchar), 0, 0, 'Y', NOW(), 100, NOW(), 100,
       'Payment Link', 'Invoice payment links through an external payment gateway',
       'PL', '1.0.0', gen_random_uuid()
 WHERE NOT EXISTS (SELECT 1 FROM AD_EntityType WHERE EntityType = 'PL');

-- status list (values match the payment service's statuses exactly)
INSERT INTO AD_Reference
    (AD_Reference_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     Name, Description, ValidationType, EntityType, AD_Reference_UU)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Reference')::integer, 'N'::varchar), 0, 0, 'Y', NOW(), 100, NOW(), 100,
       'PL_LinkStatus', 'Status of a payment link', 'L', 'PL', gen_random_uuid()
 WHERE NOT EXISTS (SELECT 1 FROM AD_Reference WHERE Name = 'PL_LinkStatus');

INSERT INTO AD_Ref_List
    (AD_Ref_List_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     Value, Name, AD_Reference_ID, EntityType, AD_Ref_List_UU)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Ref_List')::integer, 'N'::varchar), 0, 0, 'Y', NOW(), 100, NOW(), 100,
       v.val, v.name, r.AD_Reference_ID, 'PL', gen_random_uuid()
  FROM AD_Reference r
 CROSS JOIN (VALUES
        ('CREATED',         'Created'),
        ('PAID',            'Paid'),
        ('EXPIRED',         'Expired'),
        ('CANCELLED',       'Cancelled'),
        ('FAILED',          'Failed'),
        ('AMOUNT_MISMATCH', 'Amount Mismatch')) AS v(val, name)
 WHERE r.Name = 'PL_LinkStatus'
   AND NOT EXISTS (SELECT 1 FROM AD_Ref_List l
                    WHERE l.AD_Reference_ID = r.AD_Reference_ID AND l.Value = v.val);

-- table records (PL_Config copies EI_Config's access level)
INSERT INTO AD_Table
    (AD_Table_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     Name, Description, TableName, IsView, AccessLevel, EntityType, IsSecurityEnabled,
     IsDeleteable, IsHighVolume, ImportTable, IsChangeLog, ReplicationType, LoadSeq,
     IsCentrallyMaintained, AD_Table_UU)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Table')::integer, 'N'::varchar), 0, 0, 'Y', NOW(), 100, NOW(), 100,
       v.name, v.descr, v.tbl, 'N',
       CASE WHEN v.tbl = 'PL_Config'
            THEN COALESCE((SELECT AccessLevel FROM AD_Table WHERE TableName = 'EI_Config'), '3')
            ELSE '3' END,
       'PL', 'N', 'Y', 'N', 'N', 'N', 'L', 0, 'Y', gen_random_uuid()
  FROM (VALUES
        ('PL_Config',             'Payment Gateway Configuration', 'Payment service settings per client and organization'),
        ('PL_InvoicePaymentLink', 'Invoice Payment Link',          'Payment links created for invoices')
       ) AS v(tbl, name, descr)
 WHERE NOT EXISTS (SELECT 1 FROM AD_Table t WHERE t.TableName = v.tbl);

-- ID sequences for the two tables
INSERT INTO AD_Sequence
    (AD_Sequence_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     Name, Description, IsAutoSequence, IncrementNo, StartNo, CurrentNext, CurrentNextSys,
     IsTableID, IsAudited, StartNewYear, AD_Sequence_UU)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Sequence')::integer, 'N'::varchar), 0, 0, 'Y', NOW(), 100, NOW(), 100,
       t.TableName, 'Table ' || t.TableName, 'Y', 1, 1000000, 1000000, 50000,
       'Y', 'N', 'N', gen_random_uuid()
  FROM AD_Table t
 WHERE t.TableName IN ('PL_Config', 'PL_InvoicePaymentLink')
   AND NOT EXISTS (SELECT 1 FROM AD_Sequence s WHERE s.Name = t.TableName);

COMMIT;

-- check: expect 1 / 6 / 2 / 2
SELECT (SELECT COUNT(*) FROM AD_EntityType WHERE EntityType = 'PL') AS entity_types,
       (SELECT COUNT(*) FROM AD_Ref_List l JOIN AD_Reference r ON r.AD_Reference_ID = l.AD_Reference_ID
         WHERE r.Name = 'PL_LinkStatus') AS status_values,
       (SELECT COUNT(*) FROM AD_Table WHERE TableName IN ('PL_Config', 'PL_InvoicePaymentLink')) AS tables,
       (SELECT COUNT(*) FROM AD_Sequence WHERE Name IN ('PL_Config', 'PL_InvoicePaymentLink')) AS sequences;

-- NEXT (UI): Table and Column -> run "Create Columns from DB" on PL_Config,
-- PL_InvoicePaymentLink and C_Invoice, then run pl_step3_columns.sql.
