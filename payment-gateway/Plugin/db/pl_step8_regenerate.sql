-- Step 8: separate Regenerate parameter on Send Invoice Payment Link; Resend keeps its meaning.
BEGIN;

INSERT INTO AD_Element
    (AD_Element_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     ColumnName, EntityType, Name, PrintName, Description, Help,
     PO_Name, PO_PrintName, PO_Description, PO_Help, AD_Element_UU, Placeholder)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Element')::integer, 'N'::varchar), 0, 0, 'Y', NOW(), 100, NOW(), 100,
       'Regenerate', 'PL', 'Regenerate Link', 'Regenerate',
       'Cancel the active link and create a new one',
       'Yes: the active link is cancelled at the gateway and a new link is created. The old link stays in the Payment Links tab as Cancelled.',
       NULL, NULL, NULL, NULL, gen_random_uuid(), NULL
 WHERE NOT EXISTS (SELECT 1 FROM AD_Element WHERE UPPER(ColumnName) = 'REGENERATE');

-- Resend is now only "send again"
UPDATE AD_Element
   SET Name = 'Resend Link', PrintName = 'Resend',
       Description = 'Also handle invoices that already have an active link',
       Help = 'Yes: the service reuses the active link (or replaces it when the open amount changed or it expired) and the e-mail is sent again.'
 WHERE UPPER(ColumnName) = 'RESEND' AND EntityType = 'PL';

UPDATE AD_Process_Para pa
   SET Name = 'Resend Link',
       Description = 'Yes: also handle invoices that already have an active link (the service reuses it, or replaces it when the open amount changed)'
  FROM AD_Process p
 WHERE pa.AD_Process_ID = p.AD_Process_ID
   AND p.Value = 'PL_SendInvoicePaymentLink' AND pa.ColumnName = 'Resend';

INSERT INTO AD_Process_Para
    (AD_Process_Para_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     Name, Description, AD_Process_ID, SeqNo, AD_Reference_ID, ColumnName, IsCentrallyMaintained,
     FieldLength, IsMandatory, IsRange, DefaultValue, AD_Element_ID, EntityType, AD_Process_Para_UU)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Process_Para')::integer, 'N'::varchar), 0, 0, 'Y', NOW(), 100, NOW(), 100,
       'Regenerate Link',
       'Yes: cancel the active link and create a new one. The old link stays in the Payment Links tab as Cancelled.',
       p.AD_Process_ID, 75, 20, 'Regenerate', 'N',
       1, 'Y', 'N', 'N',
       (SELECT MIN(x.AD_Element_ID) FROM AD_Element x WHERE UPPER(x.ColumnName) = 'REGENERATE'),
       'PL', gen_random_uuid()
  FROM AD_Process p
 WHERE p.Value = 'PL_SendInvoicePaymentLink'
   AND NOT EXISTS (SELECT 1 FROM AD_Process_Para x
                    WHERE x.AD_Process_ID = p.AD_Process_ID AND x.ColumnName = 'Regenerate');

COMMIT;

-- check: expect 9 parameters, all with an element
SELECT pa.SeqNo, pa.ColumnName, pa.Name, pa.AD_Element_ID IS NOT NULL AS has_element
  FROM AD_Process_Para pa JOIN AD_Process p ON p.AD_Process_ID = pa.AD_Process_ID
 WHERE p.Value = 'PL_SendInvoicePaymentLink' ORDER BY pa.SeqNo;