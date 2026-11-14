-- =====================================================================
-- E-Invoice plugin: complete database setup (PostgreSQL, iDempiere 13)
-- Run as one script, top to bottom. Every statement is re-runnable.
--
-- Changes against the eight original scripts:
--   * physical tables/columns are now created here (Part 1)
--   * EI_Config has both IrpBaseURL and IrpIpAddress (separate things)
--   * both menu entries go into every client's menu tree
--   * C_Region insert is guarded, updates existing states, adds 97
--   * EI_InvoiceNotRegistered rule simplified
--   * verify queries no longer count EI_Config / EI_InvoiceResponse rows
--
-- After the script (in the iDempiere UI):
--   1. System Admin > General Rules > Sequence Check
--      (creates the ID sequences for EI_Config AND EI_InvoiceResponse)
--   2. Cache Reset, then log in again
--   3. Edit the role name in Part 4 (GardenWorld Admin) if needed
-- =====================================================================


-- =====================================================================
-- PART 0. PRE-FLIGHT CHECKS (read only, nothing is changed)
-- =====================================================================

-- 0.1 Hard-coded tab IDs must belong to the expected tables. Expect 0 rows.
SELECT m.tablename AS expected_table, m.ad_tab_id, t.tablename AS actual_table
FROM (VALUES
  ('C_Invoice',263),('C_InvoiceLine',270),('M_Product',180),('C_Charge',237),
  ('M_InOut',257),('AD_OrgInfo',170),('M_AttributeSetInstance',758),('C_Region',136)
) AS m(tablename, ad_tab_id)
LEFT JOIN ad_tab tab ON tab.ad_tab_id = m.ad_tab_id
LEFT JOIN ad_table t ON t.ad_table_id = tab.ad_table_id
WHERE t.tablename IS DISTINCT FROM m.tablename;

-- 0.2 Window 167 must be the customer invoice window. Expect 1 row.
SELECT ad_window_id, "name" FROM ad_window WHERE ad_window_id = 167;

-- 0.3 Country 208 must be India. Expect 1 row with IN.
SELECT c_country_id, "name", countrycode FROM c_country WHERE c_country_id = 208;

-- 0.4 Entity type EI. If this returns 0 rows, see the optional block below.
SELECT entitytype, "name" FROM ad_entitytype WHERE entitytype = 'SBEINV';

-- Optional: only if 0.4 returned nothing.
INSERT INTO ad_entitytype (ad_entitytype_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby,
  "name", description, entitytype, version, ad_entitytype_uu)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_EntityType')::integer, 'N'::varchar),
       0,0,'Y',now(),100,now(),100,'E-Invoice','E-Invoice plugin','SBEINV','1.0.0',gen_random_uuid()
WHERE NOT EXISTS (SELECT 1 FROM ad_entitytype WHERE entitytype = 'SBEINV');


-- =====================================================================
-- PART 1. PHYSICAL TABLES AND COLUMNS
-- =====================================================================

CREATE TABLE IF NOT EXISTS ei_config (
  ei_config_id numeric(10,0) NOT NULL,
  ad_client_id numeric(10,0) NOT NULL,
  ad_org_id numeric(10,0) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp DEFAULT now() NOT NULL,
  createdby numeric(10,0) NOT NULL,
  updated timestamp DEFAULT now() NOT NULL,
  updatedby numeric(10,0) NOT NULL,
  ei_config_uu varchar(36) DEFAULT NULL,
  irpbaseurl varchar(200) NOT NULL,
  irpipaddress varchar(45) NULL,
  irpusername varchar(100) NOT NULL,
  irppassword varchar(2000) NOT NULL,   -- encrypted values are much longer than the plain text
  irpclientid varchar(2000) NOT NULL,
  irpclientsecret varchar(2000) NOT NULL,
  irpemail varchar(100) NOT NULL,
  CONSTRAINT ei_config_pkey PRIMARY KEY (ei_config_id),
  CONSTRAINT ei_config_org UNIQUE (ad_client_id, ad_org_id)
);

CREATE TABLE IF NOT EXISTS ei_invoiceresponse (
  ei_invoiceresponse_id numeric(10,0) NOT NULL,
  ad_client_id numeric(10,0) NOT NULL,
  ad_org_id numeric(10,0) NOT NULL,
  isactive char(1) DEFAULT 'Y' NOT NULL,
  created timestamp DEFAULT now() NOT NULL,
  createdby numeric(10,0) NOT NULL,
  updated timestamp DEFAULT now() NOT NULL,
  updatedby numeric(10,0) NOT NULL,
  ei_invoiceresponse_uu varchar(36) DEFAULT NULL,
  c_invoice_id numeric(10,0) NOT NULL,
  issuccess char(1) DEFAULT 'N' NOT NULL,
  irn varchar(64),
  ackno varchar(20),
  ackdt varchar(20),
  irnstatus varchar(10),
  signedinvoice text,
  signedqrcode text,
  ewbno varchar(20),
  ewbdt varchar(20),
  ewbvalidtill varchar(20),
  errormsg varchar(2000),
  rawresponse text,
  CONSTRAINT ei_invoiceresponse_pkey PRIMARY KEY (ei_invoiceresponse_id),
  CONSTRAINT ei_invoiceresponse_invoice FOREIGN KEY (c_invoice_id) REFERENCES c_invoice (c_invoice_id) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX IF NOT EXISTS ei_invoiceresponse_invoice_idx ON ei_invoiceresponse (c_invoice_id);

-- Custom columns on core tables (AD_Column rows are created with IsSyncDatabase = N, so add them here)
ALTER TABLE c_invoice
  ADD COLUMN IF NOT EXISTS suptyp varchar(10),
  ADD COLUMN IF NOT EXISTS regrev char(1) DEFAULT 'N',
  ADD COLUMN IF NOT EXISTS ecmgstin varchar(15),
  ADD COLUMN IF NOT EXISTS igstonintra char(1) DEFAULT 'N',
  ADD COLUMN IF NOT EXISTS payinstr varchar(100),
  ADD COLUMN IF NOT EXISTS crtrn varchar(100),
  ADD COLUMN IF NOT EXISTS dirdr varchar(100),
  ADD COLUMN IF NOT EXISTS invstdt timestamp,
  ADD COLUMN IF NOT EXISTS invenddt timestamp,
  ADD COLUMN IF NOT EXISTS invno varchar(16),
  ADD COLUMN IF NOT EXISTS othrefno varchar(20),
  ADD COLUMN IF NOT EXISTS tendrefr varchar(20),
  ADD COLUMN IF NOT EXISTS contrrefr varchar(20),
  ADD COLUMN IF NOT EXISTS extrefr varchar(20),
  ADD COLUMN IF NOT EXISTS refclm char(1) DEFAULT 'N',
  ADD COLUMN IF NOT EXISTS expduty numeric,
  ADD COLUMN IF NOT EXISTS shipport varchar(10),
  ADD COLUMN IF NOT EXISTS shipbno varchar(20),
  ADD COLUMN IF NOT EXISTS shipbdt timestamp,
  ADD COLUMN IF NOT EXISTS irn varchar(64),
  ADD COLUMN IF NOT EXISTS ackno varchar(20),
  ADD COLUMN IF NOT EXISTS ackdt varchar(20),
  ADD COLUMN IF NOT EXISTS irnstatus varchar(10);

ALTER TABLE c_invoiceline ADD COLUMN IF NOT EXISTS freeqty numeric;

ALTER TABLE m_product
  ADD COLUMN IF NOT EXISTS hsncd varchar(8),
  ADD COLUMN IF NOT EXISTS c_country_id numeric(10,0);

ALTER TABLE c_charge ADD COLUMN IF NOT EXISTS hsncd varchar(8);

ALTER TABLE m_inout
  ADD COLUMN IF NOT EXISTS transmode varchar(10),
  ADD COLUMN IF NOT EXISTS distance numeric(10,0),
  ADD COLUMN IF NOT EXISTS vehno varchar(20),
  ADD COLUMN IF NOT EXISTS vehtype varchar(10);

ALTER TABLE ad_orginfo ADD COLUMN IF NOT EXISTS trdnm varchar(100);
ALTER TABLE m_attributesetinstance ADD COLUMN IF NOT EXISTS wrdt timestamp;
ALTER TABLE c_region ADD COLUMN IF NOT EXISTS gststatecode varchar(2);


-- =====================================================================
-- PART 2. AD_ELEMENT
-- =====================================================================

INSERT INTO ad_element (ad_element_id,ad_client_id,ad_org_id,isactive,created,createdby,updated,updatedby,columnname,entitytype,"name",printname,description,help,po_name,po_printname,po_description,po_help,ad_element_uu,placeholder)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Element')::integer, 'N'::varchar),
       0,0,'Y',now(),100,now(),100,
       v.columnname,'SBEINV',v.name,v.printname,v.description,v.help,
       NULL,NULL,NULL,NULL,
       v.uu::uuid,
       NULL
FROM (VALUES
  -- ordinal, columnname, name, printname, description, help, uuid
  -- ===== C_Invoice: header columns (transaction, payment, references, export) =====
  (1,'SupTyp','SupTyp','SupTyp','Type of Supply','Length 3 to 10; allowed values: `B2B` (Business to Business), `SEZWP` (SEZ with payment), `SEZWOP` (SEZ without payment), `EXPWP` (Export with payment), `EXPWOP` (Export without payment), `DEXP` (Deemed export). Uppercase values only.','01a0c7ab-4954-789b-b94a-755edf8036de'),
  (2,'RegRev','RegRev','RegRev','Whether the tax liability is payable under reverse charge','Length 1; allowed values: `Y` or `N`','01a0c7ac-1635-71c4-a229-a5af87fefc52'),
  (3,'EcmGstin','EcmGstin','EcmGstin','GSTIN of e-Commerce operator','Length 15; GSTIN format','01a0c7ac-85b7-7eaf-a4d2-68d5658d177b'),
  (4,'IgstOnIntra','IgstOnIntra','IgstOnIntra','`Y` indicates the supply is intra-state but chargeable to IGST','Length 1; allowed values: `Y` or `N`','01a0c7ad-229d-7aa0-8287-f846081d215c'),
  (5,'PayInstr','PayInstr','PayInstr','Payment instruction','Length 1 to 100; no double quotes','01a0c7ae-1dfa-79a4-afed-8d307b4b1b18'),
  (6,'CrTrn','CrTrn','CrTrn','Credit transfer','Length 1 to 100; no double quotes','01a0c7ae-da76-74f4-abd0-9bd586e0efec'),
  (7,'DirDr','DirDr','DirDr','Direct debit','Length 1 to 100; no double quotes','01a0c7af-3300-783a-896c-dbbdbf4a1e5a'),
  (8,'InvEndDt','InvEndDt','InvEndDt','Invoice period end date','Length 10; Date format','01a0c7b3-aa49-707e-bae6-a44d8166652c'),
  (9,'InvNo','InvNo','InvNo','Reference of the original invoice, if any','Declared length 1 to 16; pattern requires 2 to 16 characters: first is a letter or digit 1 to 9, the rest are letters, digits, `/` or `-`','01a0c7b6-a666-7574-9b8b-5533f7d0ef2f'),
  (10,'OthRefNo','OthRefNo','OthRefNo','Other reference','Length 1 to 20; no double quotes','01a0c7b7-52f4-7dd9-8442-918f4fdd0120'),
  (11,'TendRefr','TendRefr','TendRefr','Lot / batch reference number','Length 1 to 20; no double quotes','01a0c7b8-3577-714e-bece-f781de5be620'),
  (12,'ContrRefr','ContrRefr','ContrRefr','Contract reference number','Length 1 to 20; no double quotes','01a0c7b8-7c48-7b5b-a7b2-4ad02813b2c8'),
  (13,'ExtRefr','ExtRefr','ExtRefr','Any other reference','Length 1 to 20; no double quotes','01a0c7b8-c397-75da-97cf-6fb91e2e5823'),
  (14,'RefClm','RefClm','RefClm','Claiming refund: `Y` / `N`','Length 1; `Y` or `N`','01a0c7ba-2a49-7d14-86ee-23df93f6b332'),
  (15,'ExpDuty','ExpDuty','ExpDuty','Export duty','Min 0; max 999999999999.99','01a0c7ba-7368-75a0-ab99-764cb501b1b8'),
  (16,'ShipPort','ShipPort','ShipPort','Shipping Port Number','Length 2 to 10; letters and digits only','01a0c857-e053-7350-a598-fcafa0e67e16'),
  (17,'ShipBNo','Shipping Bill No','Shipping Bill No','Shipping bill number (exports)','Length 1 to 20; no double quotes','5b8e2f41-7c3a-4d19-9a06-e1f4b7c25d83'),
  (18,'ShipBDt','Shipping Bill Date','Shipping Bill Date','Shipping bill date (exports)','Length 10; Date format','c2a97d60-18fb-4e35-b4c1-6d0e93a7f528'),
  -- ===== IRN result elements: Irn, AckNo, AckDt, IrnStatus on C_Invoice (summary); all of them on EI_InvoiceResponse =====
  (19,'Irn','IRN','IRN','Invoice Reference Number returned by the IRP','64 character hash generated by the IRP for the invoice. Read only. On the invoice it is copied from the latest successful response.','9f13c8b2-4a67-4e0d-8c52-3b7ad1e60f94'),
  (20,'AckNo','Acknowledgement No','Ack No','IRP acknowledgement number','Acknowledgement number returned by the IRP. Read only.','e47b05d9-2c81-4f6a-a3d8-90c1f5b72e16'),
  (21,'AckDt','Acknowledgement Date','Ack Date','IRP acknowledgement date and time','Format yyyy-MM-dd HH:mm:ss as returned by the IRP. Read only.','18d6a3fc-b952-4c70-9e4b-7a25c0d8f361'),
  (22,'IrnStatus','IRN Status','IRN Status','Status of the IRN','ACT = active, CNL = cancelled. Read only.','a05c7e19-63d4-4b82-b7f0-2e9183ac4d57'),
  -- ===== Elements used only by EI_InvoiceResponse =====
  (23,'SignedQRCode','Signed QR Code','Signed QR Code','Signed QR code returned by the IRP','Signed JWT used to print the QR code on the invoice. Read only. Stored in the E-Invoice Response tab.','73e2b8a4-d1f6-4095-8a3c-c46d0b97e215'),
  (24,'SignedInvoice','Signed Invoice','Signed Invoice','Signed invoice returned by the IRP','Signed JWT containing the registered invoice data. Read only. Stored in the E-Invoice Response tab.','f8b4136d-0a97-4c2e-9d51-5e3a72c8b049'),
  (25,'EwbNo','E-Way Bill No','E-Way Bill No','E-way bill number','Returned by the IRP when the e-way bill is generated together with the IRN. Read only. Stored in the E-Invoice Response tab.','2c69d0e7-85a3-4f1b-a6c4-b1f8037e5d92'),
  (26,'EwbDt','E-Way Bill Date','E-Way Bill Date','E-way bill generation date','Format yyyy-MM-dd HH:mm:ss as returned by the IRP. Read only. Stored in the E-Invoice Response tab.','64a1f9c3-7e08-4d5b-b293-0c8e5d1a76f4'),
  (27,'EwbValidTill','E-Way Bill Valid Till','Valid Till','E-way bill validity','Format yyyy-MM-dd HH:mm:ss as returned by the IRP. Read only. Stored in the E-Invoice Response tab.','d3057b8e-9c42-4a61-8f7d-e26a14b9c035'),
  -- ===== C_InvoiceLine =====
  (28,'FreeQty','FreeQty','FreeQty','Free quantity','Min 0; max 9999999999.999','01a0c8d5-152e-7660-8355-ba18c88b5dea'),
  -- ===== M_Product (the same HsnCd element is also used on C_Charge) =====
  (29,'HsnCd','HsnCd','HsnCd','HSN code','Declared length 4 to 8; must be exactly 4, 6 or 8 digits; cannot be all zeros','01a0cd51-dc28-7e0e-8400-20d181daa007'),
  -- ===== M_InOut: e-way bill part A =====
  (30,'TransMode','TransMode','TransMode','Mode of transport','Length 1; allowed values: `1` (Road), `2` (Rail), `3` (Air), `4` (Ship)','01a0d7d2-57cc-71a5-be43-307ef9a6d650'),
  (31,'Distance','Distance','Distance','Distance between source and destination PIN codes','Min 0; max 4000','01a0d7d2-b2f7-77ff-ac07-d0bb6ed72717'),
  (32,'VehNo','VehNo','VehNo','Vehicle number','Length 4 to 20; letters and digits only','01a0d7d3-11da-7d4f-9728-8f09f114513f'),
  (33,'VehType','VehType','VehType','Vehicle type: `O` = ODC (over-dimensional cargo), `R` = Regular','Length 1; allowed values: `O` or `R`','01a0d7d3-5bc0-7ede-9310-fcd9fbac15b4'),
  -- ===== AD_OrgInfo: seller =====
  (34,'TrdNm','TrdNm','TrdNm','Trade name','Length 3 to 100; no double quotes','01a0de18-ec13-7fb6-bcca-6f587990d633'),
  -- ===== Custom columns the mapper reads =====
  (35,'InvStDt','Invoice Period Start Date','Period Start Date','Invoice period start date','Length 10; Date format','4fe83a15-2d6b-4b09-9c37-a1d5f08e72c4'),
  (36,'WrDt','Warranty Date','Warranty Date','Batch warranty date','Length 10; Date format','b71c06d2-e958-4a3f-8e21-6c4b93f0a7d8'),
  (37,'GstStateCode','GST State Code','GST State Code','GST state code of the region','1 or 2 digits, not all zeros; refer to the GST state master','0a5d9c73-b4e1-4f82-b619-d83e72a15c60'),
  -- ===== EI_InvoiceResponse: new elements (ErrorMsg and IsSuccess are skipped if core already has them) =====
  (38,'EI_InvoiceResponse_ID','E-Invoice Response','E-Invoice Response','One IRP request attempt and its response for an invoice','Created by the e-invoice process. Read only.','c5e1a7d3-9b24-4f68-a0c1-3e8d72b5f496'),
  (39,'EI_InvoiceResponse_UU','EI_InvoiceResponse_UU','EI_InvoiceResponse_UU','Immutable universally unique identifier','Immutable universally unique identifier','7d0b3f92-e4a8-4c15-b6d7-91a2c8e5034f'),
  (40,'IsSuccess','Success','Success','The IRP accepted the request','Y when the IRP returned an IRN for this attempt.','a93e6c18-5f7b-4d02-8e49-b0d1f3a62c75'),
  (41,'RawResponse','Raw Response','Raw Response','Full response body returned by the IRP','Kept as audit evidence. Read only.','3b8f40d6-c2e9-4a71-9d35-e7a60b1c8f24'),
  (42,'ErrorMsg','Error Msg','Error Msg','Error message','Reason given by the IRP when the request was rejected.','e1c75a29-0d83-4b6f-a4e2-58f9b3d7c016')
) AS v(ord,columnname,name,printname,description,help,uu)
WHERE NOT EXISTS (SELECT 1 FROM ad_element e WHERE e.columnname = v.columnname)
ORDER BY v.ord;

-- EI_Config elements (IrpBaseURL and IrpIpAddress are different things)
INSERT INTO ad_element (ad_element_id,ad_client_id,ad_org_id,isactive,created,createdby,updated,updatedby,columnname,entitytype,"name",printname,description,help,po_name,po_printname,po_description,po_help,ad_element_uu,placeholder)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Element')::integer, 'N'::varchar),
       0,0,'Y',now(),100,now(),100,
       v.columnname,'SBEINV',v.name,v.printname,v.description,v.help,NULL,NULL,NULL,NULL,gen_random_uuid(),NULL
FROM (VALUES
  (1,'EI_Config_ID','E-Invoice Configuration','E-Invoice Configuration','IRP connection settings','One row per client, optionally one per organization. An organization row overrides the client-wide row (organization *).'),
  (2,'EI_Config_UU','EI_Config_UU','EI_Config_UU','Immutable universally unique identifier','Immutable universally unique identifier'),
  (3,'IrpBaseURL','IRP Base URL','Base URL','Base URL of the IRP / GSP API','Must start with https:// and end with /. Example: https://apisandbox.whitebooks.in/'),
  (4,'IrpUserName','IRP User Name','User Name','API user name','Stored in plain text.'),
  (5,'IrpPassword','IRP Password','Password','API password','Encrypted in the database.'),
  (6,'IrpClientID','IRP Client ID','Client ID','API client id','Encrypted in the database.'),
  (7,'IrpClientSecret','IRP Client Secret','Client Secret','API client secret','Encrypted in the database.'),
  (8,'IrpEmail','IRP Email','Email','Email registered with the API provider','Sent with every API call.'),
  (9,'IrpIpAddress','IRP IP Address','IP Address','Value sent in the ip_address header','Optional. Leave blank to use the address detected on the server.')
) AS v(ord,columnname,name,printname,description,help)
WHERE NOT EXISTS (SELECT 1 FROM ad_element e WHERE e.columnname = v.columnname)
ORDER BY v.ord;


-- =====================================================================
-- PART 3. AD_REFERENCE AND AD_REF_LIST
-- =====================================================================

INSERT INTO ad_reference (ad_reference_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby,
  "name", description, help, validationtype, entitytype, isorderbyvalue, ad_reference_uu)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Reference')::integer, 'N'::varchar),
       0, 0, 'Y', now(), 100, now(), 100,
       v."name", v.description, v.help, 'L', 'SBEINV', 'N', gen_random_uuid()
FROM (VALUES
  ('EI_SupTyp',    'E-Invoice Type of Supply',  'IRP SupTyp. Values map to B2B, SEZWP, SEZWOP, EXPWP, EXPWOP, DEXP in MSBInvoice.getSupTypCode().'),
  ('EI_TransMode', 'E-Invoice Transport Mode',  'IRP EwbDtls.TransMode. Value is sent to the IRP as is: 1 Road, 2 Rail, 3 Air, 4 Ship.'),
  ('EI_VehType',   'E-Invoice Vehicle Type',    'IRP EwbDtls.VehType. Value is sent to the IRP as is: O = ODC, R = Regular.'),
  ('EI_IrnStatus', 'E-Invoice IRN Status',      'Status returned by the IRP: ACT = active, CNL = cancelled.')
) AS v("name", description, help)
WHERE NOT EXISTS (SELECT 1 FROM ad_reference r WHERE r."name" = v."name");

INSERT INTO ad_ref_list (ad_ref_list_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby,
  value, "name", description, ad_reference_id, entitytype, ad_ref_list_uu)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Ref_List')::integer, 'N'::varchar),
       0, 0, 'Y', now(), 100, now(), 100,
       v.value, v."name", v.description, r.ad_reference_id, 'SBEINV', gen_random_uuid()
FROM (VALUES
  ('EI_SupTyp',    'B2B',    'Business to Business',    'Business to Business'),
  ('EI_SupTyp',    'SEZWP',  'SEZ with payment',        'SEZ with payment'),
  ('EI_SupTyp',    'SEZWOP', 'SEZ without payment',     'SEZ without payment'),
  ('EI_SupTyp',    'EXPWP',  'Export with payment',     'Export with payment'),
  ('EI_SupTyp',    'EXPWOP', 'Export without payment',  'Export without payment'),
  ('EI_SupTyp',    'DEXP',   'Deemed export',           'Deemed export'),
  ('EI_TransMode', '1', 'Road',  'Transport by road'),
  ('EI_TransMode', '2', 'Rail',  'Transport by rail'),
  ('EI_TransMode', '3', 'Air',   'Transport by air'),
  ('EI_TransMode', '4', 'Ship',  'Transport by ship'),
  ('EI_VehType',   'O', 'ODC',     'Over-dimensional cargo'),
  ('EI_VehType',   'R', 'Regular', 'Regular vehicle'),
  ('EI_IrnStatus', 'ACT', 'Active',    'IRN is active'),
  ('EI_IrnStatus', 'CNL', 'Cancelled', 'IRN has been cancelled')
) AS v(refname, value, "name", description)
JOIN ad_reference r ON r."name" = v.refname AND r.entitytype = 'SBEINV'
WHERE NOT EXISTS (SELECT 1 FROM ad_ref_list l WHERE l.ad_reference_id = r.ad_reference_id AND l.value = v.value);


-- =====================================================================
-- PART 4. E-INVOICE CONFIGURATION WINDOW (EI_Config)
-- =====================================================================

-- 4.1 AD_Table (ischangelog = N so secrets never reach AD_ChangeLog)
INSERT INTO ad_table (ad_table_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby,
  "name", description, tablename, isview, accesslevel, entitytype, isdeleteable, ishighvolume, importtable, ischangelog, replicationtype, issecurityenabled, ad_table_uu)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Table')::integer, 'N'::varchar),
       0,0,'Y',now(),100,now(),100,
       'E-Invoice Configuration','IRP connection settings per client or organization','EI_Config','N','3','SBEINV','Y','N','N','N','L','N',gen_random_uuid()
WHERE NOT EXISTS (SELECT 1 FROM ad_table WHERE tablename = 'EI_Config');

-- 4.2 AD_Column (IsEncrypted = Y on password, client id, client secret)
INSERT INTO ad_column (ad_column_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby,
  "name", description, help, "version", entitytype, columnname, ad_table_id, ad_reference_id, ad_reference_value_id,
  fieldlength, defaultvalue, iskey, isparent, ismandatory, isupdateable, isidentifier, seqno, istranslated, isencrypted,
  isselectioncolumn, ad_element_id, issyncdatabase, isalwaysupdateable, isallowlogging, isallowcopy, ad_column_uu)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Column')::integer, 'N'::varchar),
       0,0,'Y',now(),100,now(),100,
       e."name", e.description, e.help, 0, 'SBEINV', v.columnname, t.ad_table_id, v.ref_id, v.refval_id,
       v.len, v.defval, v.iskey, v.isparent, v.mand, v.upd, v.ident, v.seq, 'N', v.enc,
       'N', e.ad_element_id, 'N','N','N','N', gen_random_uuid()
FROM (VALUES
  -- columnname, ref_id, refval_id, len, defval, iskey, isparent, mand, upd, ident, seq, enc
  ('EI_Config_ID',13,NULL,22,NULL,'Y','N','Y','N','N',0,'N'),
  ('AD_Client_ID',19,NULL,10,'@#AD_Client_ID@','N','N','Y','N','N',0,'N'),
  ('AD_Org_ID',19,NULL,10,'@#AD_Org_ID@','N','N','Y','Y','N',0,'N'),
  ('IsActive',20,NULL,1,'Y','N','N','Y','Y','N',0,'N'),
  ('Created',16,NULL,7,NULL,'N','N','Y','N','N',0,'N'),
  ('CreatedBy',18,110,10,NULL,'N','N','Y','N','N',0,'N'),
  ('Updated',16,NULL,7,NULL,'N','N','Y','N','N',0,'N'),
  ('UpdatedBy',18,110,10,NULL,'N','N','Y','N','N',0,'N'),
  ('EI_Config_UU',10,NULL,36,NULL,'N','N','N','N','N',0,'N'),
  ('IrpBaseURL',10,NULL,200,NULL,'N','N','Y','Y','Y',10,'N'),
  ('IrpIpAddress',10,NULL,45,NULL,'N','N','N','Y','N',0,'N'),
  ('IrpUserName',10,NULL,100,NULL,'N','N','Y','Y','N',0,'N'),
  ('IrpPassword',10,NULL,100,NULL,'N','N','Y','Y','N',0,'Y'),
  ('IrpClientID',10,NULL,100,NULL,'N','N','Y','Y','N',0,'Y'),
  ('IrpClientSecret',10,NULL,100,NULL,'N','N','Y','Y','N',0,'Y'),
  ('IrpEmail',10,NULL,100,NULL,'N','N','Y','Y','N',0,'N')
) AS v(columnname, ref_id, refval_id, len, defval, iskey, isparent, mand, upd, ident, seq, enc)
JOIN ad_table t ON t.tablename = 'EI_Config'
JOIN ad_element e ON e.columnname = v.columnname
WHERE NOT EXISTS (SELECT 1 FROM ad_column c WHERE c.ad_table_id = t.ad_table_id AND c.columnname = v.columnname);

-- 4.3 AD_Window
INSERT INTO ad_window (ad_window_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby,
  "name", description, windowtype, issotrx, entitytype, isdefault, isbetafunctionality, ad_window_uu)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Window')::integer, 'N'::varchar),
       0,0,'Y',now(),100,now(),100,
       'E-Invoice Configuration','IRP connection settings per client or organization','M','N','SBEINV','N','N',gen_random_uuid()
WHERE NOT EXISTS (SELECT 1 FROM ad_window WHERE "name" = 'E-Invoice Configuration');

-- 4.4 AD_Tab
INSERT INTO ad_tab (ad_tab_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby,
  "name", description, ad_table_id, ad_window_id, seqno, tablevel, issinglerow, isinfotab, istranslationtab, isreadonly,
  hastree, processing, importfields, isinsertrecord, issorttab, entitytype, ad_tab_uu)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Tab')::integer, 'N'::varchar),
       0,0,'Y',now(),100,now(),100,
       'E-Invoice Configuration','IRP connection settings', t.ad_table_id, w.ad_window_id, 10, 0,'Y','N','N','N',
       'N','N','N','Y','N','SBEINV', gen_random_uuid()
FROM ad_table t JOIN ad_window w ON w."name" = 'E-Invoice Configuration'
WHERE t.tablename = 'EI_Config'
  AND NOT EXISTS (SELECT 1 FROM ad_tab x WHERE x.ad_table_id = t.ad_table_id AND x.ad_window_id = w.ad_window_id);

-- 4.5 AD_Field
   INSERT INTO ad_field (ad_field_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby,
  "name", description, help, iscentrallymaintained, ad_tab_id, ad_column_id, isdisplayed, displaylength,
  isreadonly, seqno, issameline, isheading, isfieldonly, isencrypted, entitytype, isallowcopy,
  seqnogrid, isdisplayedgrid, xposition, numlines, columnspan, isquickentry, isadvancedfield, isdefaultfocus, ad_field_uu)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Field')::integer, 'N'::varchar),
       0,0,'Y',now(),100,now(),100,
       c."name", c.description, c.help, 'Y', tab.ad_tab_id, c.ad_column_id,
       CASE WHEN o.columnname = 'EI_Config_ID' THEN 'N' ELSE 'Y' END,
       LEAST(GREATEST(c.fieldlength,1),60),
       'N', o.seq, 'N','N','N', c.isencrypted,'SBEINV','N',
       0,'N',1,1,2,'N','N','N', gen_random_uuid()
FROM (VALUES
  ('EI_Config_ID',5),
  ('AD_Client_ID',10),('AD_Org_ID',20),('IrpBaseURL',30),('IrpIpAddress',35),('IrpUserName',40),('IrpPassword',50),
  ('IrpClientID',60),('IrpClientSecret',70),('IrpEmail',80),('IsActive',90)
) AS o(columnname, seq)
JOIN ad_table t ON t.tablename = 'EI_Config'
JOIN ad_column c ON c.ad_table_id = t.ad_table_id AND c.columnname = o.columnname
JOIN ad_window w ON w."name" = 'E-Invoice Configuration'
JOIN ad_tab tab ON tab.ad_table_id = t.ad_table_id AND tab.ad_window_id = w.ad_window_id
WHERE NOT EXISTS (SELECT 1 FROM ad_field f WHERE f.ad_tab_id = tab.ad_tab_id AND f.ad_column_id = c.ad_column_id);

-- 4.6 Menu
INSERT INTO ad_menu (ad_menu_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby,
  "name", description, issummary, issotrx, isreadonly, action, ad_window_id, entitytype, ad_menu_uu)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Menu')::integer, 'N'::varchar),
       0,0,'Y',now(),100,now(),100,
       'E-Invoice Configuration','IRP connection settings','N','N','N','W', w.ad_window_id,'SBEINV',gen_random_uuid()
FROM ad_window w
WHERE w."name" = 'E-Invoice Configuration'
  AND NOT EXISTS (SELECT 1 FROM ad_menu m WHERE m.ad_window_id = w.ad_window_id);

-- 4.7 Menu tree: every client's menu tree, not only the System one
INSERT INTO ad_treenodemm (ad_tree_id, node_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, parent_id, seqno, ad_treenodemm_uu)
SELECT t.ad_tree_id, m.ad_menu_id, t.ad_client_id, 0,'Y',now(),100,now(),100, 0, 999, gen_random_uuid()
FROM ad_menu m
JOIN ad_window w ON w.ad_window_id = m.ad_window_id
JOIN ad_tree t ON t.treetype = 'MM' AND t.isactive = 'Y' AND t.isallnodes = 'Y'
WHERE w."name" = 'E-Invoice Configuration'
  AND NOT EXISTS (SELECT 1 FROM ad_treenodemm n WHERE n.ad_tree_id = t.ad_tree_id AND n.node_id = m.ad_menu_id);

-- 4.8 Window access. Deliberately NOT everyone with the invoice window. EDIT the role list.
INSERT INTO ad_window_access (ad_window_id, ad_role_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, isreadwrite, ad_window_access_uu)
SELECT w.ad_window_id, r.ad_role_id, r.ad_client_id, 0, 'Y', now(), 100, now(), 100, 'Y', gen_random_uuid()
FROM ad_window w
JOIN ad_role r ON r."name" IN ('GardenWorld Admin')   -- <-- your client admin role(s) only
WHERE w."name" = 'E-Invoice Configuration'
  AND NOT EXISTS (SELECT 1 FROM ad_window_access x WHERE x.ad_window_id = w.ad_window_id AND x.ad_role_id = r.ad_role_id);


-- =====================================================================
-- PART 5. E-INVOICE RESPONSE TAB (EI_InvoiceResponse, under the invoice window)
-- =====================================================================

-- 5.1 AD_Table
INSERT INTO ad_table (ad_table_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby,
  "name", description, tablename, isview, accesslevel, entitytype, isdeleteable, ishighvolume, importtable, ischangelog, replicationtype, issecurityenabled, ad_table_uu)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Table')::integer, 'N'::varchar),
       0,0,'Y',now(),100,now(),100,
       'E-Invoice Response','IRP request and response log for e-invoices','EI_InvoiceResponse','N','3','SBEINV','N','N','N','N','L','N',gen_random_uuid()
WHERE NOT EXISTS (SELECT 1 FROM ad_table WHERE tablename = 'EI_InvoiceResponse');

-- 5.2 AD_Column
INSERT INTO ad_column (ad_column_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby,
  "name", description, help, "version", entitytype, columnname, ad_table_id, ad_reference_id, ad_reference_value_id,
  fieldlength, defaultvalue, iskey, isparent, ismandatory, isupdateable, isidentifier, seqno, istranslated, isencrypted,
  isselectioncolumn, ad_element_id, issyncdatabase, isalwaysupdateable, isallowlogging, isallowcopy, ad_column_uu)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Column')::integer, 'N'::varchar),
       0,0,'Y',now(),100,now(),100,
       e."name", e.description, e.help, 0, 'SBEINV', v.columnname, t.ad_table_id, v.ref_id, COALESCE(rv.ad_reference_id, v.refval_id),
       v.len, v.defval, v.iskey, v.isparent, v.mand, v.upd, v.ident, v.seq, 'N','N',
       'N', e.ad_element_id, 'N','N','N','N', gen_random_uuid()
FROM (VALUES
  -- columnname, ref_id, refname, refval_id, len, defval, iskey, isparent, mand, upd, ident, seq
  ('EI_InvoiceResponse_ID',13,NULL,NULL,22,NULL,'Y','N','Y','N','N',0),
  ('AD_Client_ID',19,NULL,NULL,10,'@#AD_Client_ID@','N','N','Y','N','N',0),
  ('AD_Org_ID',19,NULL,NULL,10,'@#AD_Org_ID@','N','N','Y','Y','N',0),
  ('IsActive',20,NULL,NULL,1,'Y','N','N','Y','Y','N',0),
  ('Created',16,NULL,NULL,7,NULL,'N','N','Y','N','N',0),
  ('CreatedBy',18,NULL,110,10,NULL,'N','N','Y','N','N',0),
  ('Updated',16,NULL,NULL,7,NULL,'N','N','Y','N','N',0),
  ('UpdatedBy',18,NULL,110,10,NULL,'N','N','Y','N','N',0),
  ('EI_InvoiceResponse_UU',10,NULL,NULL,36,NULL,'N','N','N','N','N',0),
  ('C_Invoice_ID',19,NULL,NULL,10,NULL,'N','Y','Y','N','N',0),
  ('IsSuccess',20,NULL,NULL,1,'N','N','N','Y','N','N',0),
  ('IrnStatus',17,'EI_IrnStatus',NULL,10,NULL,'N','N','N','N','N',0),
  ('Irn',10,NULL,NULL,64,NULL,'N','N','N','N','Y',10),
  ('AckNo',10,NULL,NULL,20,NULL,'N','N','N','N','N',0),
  ('AckDt',10,NULL,NULL,20,NULL,'N','N','N','N','N',0),
  ('SignedInvoice',34,NULL,NULL,0,NULL,'N','N','N','N','N',0),
  ('SignedQRCode',34,NULL,NULL,0,NULL,'N','N','N','N','N',0),
  ('EwbNo',10,NULL,NULL,20,NULL,'N','N','N','N','N',0),
  ('EwbDt',10,NULL,NULL,20,NULL,'N','N','N','N','N',0),
  ('EwbValidTill',10,NULL,NULL,20,NULL,'N','N','N','N','N',0),
  ('ErrorMsg',10,NULL,NULL,2000,NULL,'N','N','N','N','N',0),
  ('RawResponse',34,NULL,NULL,0,NULL,'N','N','N','N','N',0)
) AS v(columnname, ref_id, refname, refval_id, len, defval, iskey, isparent, mand, upd, ident, seq)
JOIN ad_table t ON t.tablename = 'EI_InvoiceResponse'
JOIN ad_element e ON e.columnname = v.columnname
LEFT JOIN ad_reference rv ON rv."name" = v.refname AND rv.entitytype = 'SBEINV'
WHERE NOT EXISTS (SELECT 1 FROM ad_column c WHERE c.ad_table_id = t.ad_table_id AND c.columnname = v.columnname);

-- 5.3 AD_Tab (child tab of the customer invoice window 167)
INSERT INTO ad_tab (ad_tab_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby,
  "name", description, ad_table_id, ad_window_id, seqno, tablevel, issinglerow, isinfotab, istranslationtab, isreadonly,
  ad_column_id, hastree, orderbyclause, processing, importfields, isinsertrecord, issorttab, entitytype, ad_tab_uu, whereclause)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Tab')::integer, 'N'::varchar),
       0,0,'Y',now(),100,now(),100,
       'E-Invoice Response','IRP request and response log for this invoice', t.ad_table_id, 167,
       (SELECT COALESCE(MAX(seqno),0)+10 FROM ad_tab WHERE ad_window_id = 167),
       1,'N','N','N','Y',
       (SELECT ad_column_id FROM ad_column WHERE ad_table_id = t.ad_table_id AND columnname = 'C_Invoice_ID'),
       'N','Created DESC','N','N','N','N','SBEINV', gen_random_uuid(), 'EI_InvoiceResponse.C_Invoice_ID=@C_Invoice_ID@'
FROM ad_table t
WHERE t.tablename = 'EI_InvoiceResponse'
  AND NOT EXISTS (SELECT 1 FROM ad_tab x WHERE x.ad_table_id = t.ad_table_id AND x.ad_window_id = 167);

-- 5.4 AD_Field
INSERT INTO ad_field (ad_field_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby,
  "name", description, help, iscentrallymaintained, ad_tab_id, ad_column_id, isdisplayed, displaylength,
  isreadonly, seqno, issameline, isheading, isfieldonly, isencrypted, entitytype, isallowcopy,
  seqnogrid, isdisplayedgrid, xposition, numlines, columnspan, isquickentry, isadvancedfield, isdefaultfocus, ad_field_uu)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Field')::integer, 'N'::varchar),
       0,0,'Y',now(),100,now(),100,
       c."name", c.description, c.help, 'Y', tab.ad_tab_id, c.ad_column_id, 'Y',
       CASE WHEN c.ad_reference_id = 34 THEN 60 ELSE LEAST(GREATEST(c.fieldlength,1),60) END,
       'Y', o.seq, 'N','N','N','N','SBEINV','N',
       CASE WHEN o.grid THEN o.seq ELSE 0 END,
       CASE WHEN o.grid THEN 'Y' ELSE 'N' END,
       1,
       CASE WHEN c.ad_reference_id = 34 THEN 3 ELSE 1 END,
       CASE WHEN c.ad_reference_id = 34 THEN 5 ELSE 2 END,
       'N','N','N', gen_random_uuid()
FROM (VALUES
  ('AD_Client_ID',10,false),('AD_Org_ID',20,false),('IsSuccess',30,true),('IrnStatus',40,true),('Irn',50,true),
  ('AckNo',60,true),('AckDt',70,true),('EwbNo',80,false),('EwbDt',90,false),('EwbValidTill',100,false),
  ('ErrorMsg',110,true),('SignedQRCode',120,false),('SignedInvoice',130,false),('RawResponse',140,false),('IsActive',150,false)
) AS o(columnname, seq, grid)
JOIN ad_table t ON t.tablename = 'EI_InvoiceResponse'
JOIN ad_column c ON c.ad_table_id = t.ad_table_id AND c.columnname = o.columnname
JOIN ad_tab tab ON tab.ad_table_id = t.ad_table_id AND tab.ad_window_id = 167
WHERE NOT EXISTS (SELECT 1 FROM ad_field f WHERE f.ad_tab_id = tab.ad_tab_id AND f.ad_column_id = c.ad_column_id);


-- =====================================================================
-- PART 6. CUSTOM COLUMNS ON CORE TABLES (AD_Column)
-- =====================================================================

INSERT INTO ad_column (ad_column_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby,
  "name", description, help, "version", entitytype, columnname, ad_table_id, ad_reference_id, ad_reference_value_id,
  fieldlength, defaultvalue, ismandatory, isupdateable, isidentifier, istranslated, isencrypted, valuemin, valuemax,
  isselectioncolumn, ad_element_id, ad_process_id, issyncdatabase, isalwaysupdateable, isallowlogging, isallowcopy, ad_column_uu)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Column')::integer, 'N'::varchar),
       0, 0, 'Y', now(), 100, now(), 100,
       e."name", e.description, e.help, 0, 'SBEINV', v.columnname, t.ad_table_id, v.ref_id, rv.ad_reference_id,
       v.len, v.defval, 'N', v.upd, 'N', 'N', 'N', v.vmin, v.vmax,
       'N', e.ad_element_id, p.ad_process_id, 'N', 'N', 'Y', v.cp, gen_random_uuid()
FROM (VALUES
  -- tablename, columnname, ref_id, refname, len, defval, vmin, vmax, upd, cp, process classname
  -- ===== C_Invoice: header =====
  ('C_Invoice','SupTyp',       17,'EI_SupTyp',   10,NULL,NULL,NULL,'Y','Y',NULL),
  ('C_Invoice','RegRev',       20,NULL,           1,'N', NULL,NULL,'Y','Y',NULL),
  ('C_Invoice','EcmGstin',     10,NULL,          15,NULL,NULL,NULL,'Y','Y',NULL),
  ('C_Invoice','IgstOnIntra',  20,NULL,           1,'N', NULL,NULL,'Y','Y',NULL),
  ('C_Invoice','PayInstr',     10,NULL,         100,NULL,NULL,NULL,'Y','Y',NULL),
  ('C_Invoice','CrTrn',        10,NULL,         100,NULL,NULL,NULL,'Y','Y',NULL),
  ('C_Invoice','DirDr',        10,NULL,         100,NULL,NULL,NULL,'Y','Y',NULL),
  ('C_Invoice','InvStDt',      15,NULL,           7,NULL,NULL,NULL,'Y','Y',NULL),
  ('C_Invoice','InvEndDt',     15,NULL,           7,NULL,NULL,NULL,'Y','Y',NULL),
  ('C_Invoice','InvNo',        10,NULL,          16,NULL,NULL,NULL,'Y','Y',NULL),
  ('C_Invoice','OthRefNo',     10,NULL,          20,NULL,NULL,NULL,'Y','Y',NULL),
  ('C_Invoice','TendRefr',     10,NULL,          20,NULL,NULL,NULL,'Y','Y',NULL),
  ('C_Invoice','ContrRefr',    10,NULL,          20,NULL,NULL,NULL,'Y','Y',NULL),
  ('C_Invoice','ExtRefr',      10,NULL,          20,NULL,NULL,NULL,'Y','Y',NULL),
  ('C_Invoice','RefClm',       20,NULL,           1,'N', NULL,NULL,'Y','Y',NULL),
  ('C_Invoice','ExpDuty',      12,NULL,          22,NULL,'0','999999999999.99','Y','Y',NULL),
  ('C_Invoice','ShipPort',     10,NULL,          10,NULL,NULL,NULL,'Y','Y',NULL),
  ('C_Invoice','ShipBNo',      10,NULL,          20,NULL,NULL,NULL,'Y','Y',NULL),
  ('C_Invoice','ShipBDt',      15,NULL,           7,NULL,NULL,NULL,'Y','Y',NULL),
  -- ===== C_Invoice: IRN summary (read only, not copied; full response is in EI_InvoiceResponse) =====
  ('C_Invoice','Irn',          10,NULL,          64,NULL,NULL,NULL,'N','N',NULL),
  ('C_Invoice','AckNo',        10,NULL,          20,NULL,NULL,NULL,'N','N',NULL),
  ('C_Invoice','AckDt',        10,NULL,          20,NULL,NULL,NULL,'N','N',NULL),
  ('C_Invoice','IrnStatus',    17,'EI_IrnStatus',10,NULL,NULL,NULL,'N','N',NULL),
  -- ===== Other tables =====
  ('C_InvoiceLine','FreeQty',  29,NULL,          22,NULL,'0','9999999999.999','Y','Y',NULL),
  ('M_Product','HsnCd',        10,NULL,           8,NULL,NULL,NULL,'Y','Y',NULL),
  ('C_Charge','HsnCd',         10,NULL,           8,NULL,NULL,NULL,'Y','Y',NULL),
  ('M_Product','C_Country_ID', 19,NULL,          10,NULL,NULL,NULL,'Y','Y',NULL),
  ('M_InOut','TransMode',      17,'EI_TransMode',10,NULL,NULL,NULL,'Y','Y',NULL),
  ('M_InOut','Distance',       11,NULL,          10,NULL,'0','4000','Y','Y',NULL),
  ('M_InOut','VehNo',          10,NULL,          20,NULL,NULL,NULL,'Y','Y',NULL),
  ('M_InOut','VehType',        17,'EI_VehType',  10,NULL,NULL,NULL,'Y','Y',NULL),
  ('AD_OrgInfo','TrdNm',       10,NULL,         100,NULL,NULL,NULL,'Y','Y',NULL),
  ('M_AttributeSetInstance','WrDt',15,NULL,       7,NULL,NULL,NULL,'Y','Y',NULL),
  ('C_Region','GstStateCode',  10,NULL,           2,NULL,NULL,NULL,'Y','Y',NULL)
) AS v(tablename, columnname, ref_id, refname, len, defval, vmin, vmax, upd, cp, proc)
JOIN ad_table t ON t.tablename = v.tablename
JOIN ad_element e ON e.columnname = v.columnname
LEFT JOIN ad_reference rv ON rv."name" = v.refname AND rv.entitytype = 'SBEINV'
LEFT JOIN ad_process p ON p.classname = v.proc
WHERE NOT EXISTS (SELECT 1 FROM ad_column c WHERE c.ad_table_id = t.ad_table_id AND c.columnname = v.columnname);


-- =====================================================================
-- PART 7. FIELDS FOR THOSE COLUMNS (appended to the existing tabs)
-- =====================================================================

INSERT INTO ad_field (ad_field_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby,
  "name", description, help, iscentrallymaintained, ad_tab_id, ad_column_id, isdisplayed, displaylength,
  isreadonly, seqno, issameline, isheading, isfieldonly, isencrypted, entitytype, isallowcopy,
  seqnogrid, isdisplayedgrid, xposition, numlines, columnspan, isquickentry, isadvancedfield, isdefaultfocus, ad_field_uu)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Field')::integer, 'N'::varchar),
       0, 0, 'Y', now(), 100, now(), 100,
       s."name", s.description, s.help, 'Y', s.ad_tab_id, s.ad_column_id, 'Y',
       CASE WHEN s.ad_reference_id = 34 THEN 60 ELSE LEAST(GREATEST(s.fieldlength, 1), 60) END,
       CASE WHEN s.isupdateable = 'N' THEN 'Y' ELSE 'N' END,
       s.seq, 'N', 'N', 'N', 'N', 'SBEINV', s.isallowcopy,
       0, 'N', 1,
       CASE WHEN s.ad_reference_id = 34 THEN 3 ELSE 1 END,
       CASE WHEN s.ad_reference_id = 34 THEN 5 ELSE 2 END,
       'N', 'N', 'N', gen_random_uuid()
FROM (
  SELECT c.ad_column_id, c."name", c.description, c.help, c.ad_reference_id, c.fieldlength, c.isupdateable, c.isallowcopy,
         m.ad_tab_id,
         (SELECT COALESCE(MAX(f.seqno), 0) FROM ad_field f WHERE f.ad_tab_id = m.ad_tab_id)
           + 10 * ROW_NUMBER() OVER (PARTITION BY m.ad_tab_id ORDER BY c.ad_column_id) AS seq
  FROM ad_column c
  JOIN ad_table t ON t.ad_table_id = c.ad_table_id
  JOIN (VALUES
    ('C_Invoice',              263),
    ('C_InvoiceLine',          270),
    ('M_Product',              180),
    ('C_Charge',               237),
    ('M_InOut',                257),
    ('AD_OrgInfo',             170),
    ('M_AttributeSetInstance', 758),
    ('C_Region',               136)
  ) AS m(tablename, ad_tab_id) ON m.tablename = t.tablename
  WHERE c.entitytype = 'SBEINV'
    AND NOT EXISTS (SELECT 1 FROM ad_field f WHERE f.ad_tab_id = m.ad_tab_id AND f.ad_column_id = c.ad_column_id)
) s;


-- =====================================================================
-- PART 8. GST STATE CODES (C_Region, India)
-- Inserts missing states, fixes the code on states that already exist.
-- Withdrawn codes 25 and 28 are left out on purpose; 97 is "Other Territory".
-- =====================================================================

WITH v(name, gststatecode) AS (
  VALUES
    ('Jammu and Kashmir',       '01'),
    ('Himachal Pradesh',        '02'),
    ('Punjab',                  '03'),
    ('Chandigarh',              '04'),
    ('Uttarakhand',             '05'),
    ('Haryana',                 '06'),
    ('Delhi',                   '07'),
    ('Rajasthan',               '08'),
    ('Uttar Pradesh',           '09'),
    ('Bihar',                   '10'),
    ('Sikkim',                  '11'),
    ('Arunachal Pradesh',       '12'),
    ('Nagaland',                '13'),
    ('Manipur',                 '14'),
    ('Mizoram',                 '15'),
    ('Tripura',                 '16'),
    ('Meghalaya',               '17'),
    ('Assam',                   '18'),
    ('West Bengal',             '19'),
    ('Jharkhand',               '20'),
    ('Odisha',                  '21'),
    ('Chhattisgarh',            '22'),
    ('Madhya Pradesh',          '23'),
    ('Gujarat',                 '24'),
    ('Dadra and Nagar Haveli and Daman and Diu', '26'),
    ('Maharashtra',             '27'),
    ('Karnataka',               '29'),
    ('Goa',                     '30'),
    ('Lakshadweep',             '31'),
    ('Kerala',                  '32'),
    ('Tamil Nadu',              '33'),
    ('Puducherry',              '34'),
    ('Andaman and Nicobar Islands', '35'),
    ('Telangana',               '36'),
    ('Andhra Pradesh',          '37'),
    ('Ladakh',                  '38'),
    ('Other Territory',         '97')
),
fixed AS (
  UPDATE c_region r SET gststatecode = v.gststatecode
  FROM v
  WHERE r.c_country_id = 208 AND r."name" = v.name AND r.gststatecode IS DISTINCT FROM v.gststatecode
  RETURNING r.c_region_id
)
INSERT INTO c_region
  (c_region_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby,
   "name", description, c_country_id, isdefault, c_region_uu, gststatecode)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'C_Region')::integer, 'N'::varchar),
       0, 0, 'Y', now(), 0, now(), 0,
       v.name, NULL, 208, 'N', gen_random_uuid(), v.gststatecode
FROM v
WHERE NOT EXISTS (SELECT 1 FROM c_region r WHERE r.c_country_id = 208 AND r."name" = v.name);


-- =====================================================================
-- PART 9. PROCESS "Generate E-Invoice (IRN)"
-- =====================================================================

-- 9.1 Validation rule for the invoice parameter
INSERT INTO ad_val_rule (ad_val_rule_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, "name", description, type, code, entitytype, ad_val_rule_uu)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Val_Rule')::integer, 'N'::varchar),
       0,0,'Y',now(),100,now(),100,
       'EI_InvoiceNotRegistered','Completed sales invoices without an IRN','S',
       'i.IsSOTrx=''Y'' AND i.DocStatus IN (''CO'',''CL'') AND NOT EXISTS (SELECT 1 FROM C_Invoice x WHERE x.C_Invoice_ID=i.C_Invoice_ID AND x.Irn IS NOT NULL)','SBEINV',gen_random_uuid()
WHERE NOT EXISTS (SELECT 1 FROM ad_val_rule WHERE "name" = 'EI_InvoiceNotRegistered');

-- 9.2 Process
INSERT INTO ad_process (ad_process_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby,
  value, "name", description, help, accesslevel, entitytype, isreport, isdirectprint, classname,
  isbetafunctionality, isserverprocess, showhelp, ad_process_uu)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Process')::integer, 'N'::varchar),
       0,0,'Y',now(),100,now(),100,
       'EI_GenerateIRN','Generate E-Invoice (IRN)',
       'Sends sales invoices to the IRP and logs the response',
       'Pick one invoice, or a date range to process all completed sales invoices in it that have no IRN yet. When started from the invoice window the open invoice is used and the parameters are ignored.',
       '3','SBEINV','N','N','com.santhoshbalaguru.einvoice.process.EInvoiceProcess','N','N','Y',gen_random_uuid()
WHERE NOT EXISTS (SELECT 1 FROM ad_process WHERE classname = 'com.santhoshbalaguru.einvoice.process.EInvoiceProcess');

-- 9.3 Parameters
INSERT INTO ad_process_para (ad_process_para_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby,
  "name", description, help, ad_process_id, seqno, ad_reference_id, ad_val_rule_id, columnname, iscentrallymaintained,
  fieldlength, ismandatory, isrange, ad_element_id, entitytype, ad_process_para_uu)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Process_Para')::integer, 'N'::varchar),
       0,0,'Y',now(),100,now(),100,
       e."name", e.description, e.help, p.ad_process_id, v.seq, v.ref_id, vr.ad_val_rule_id, v.columnname, 'Y',
       v.len, 'N', v.isrange, e.ad_element_id, 'SBEINV', gen_random_uuid()
FROM (VALUES
  ('C_Invoice_ID', 10, 30, 10, 'N', 'EI_InvoiceNotRegistered'),
  ('DateInvoiced', 20, 15,  7, 'Y', NULL)
) AS v(columnname, seq, ref_id, len, isrange, rulename)
JOIN ad_process p ON p.classname = 'com.santhoshbalaguru.einvoice.process.EInvoiceProcess'
JOIN ad_element e ON e.columnname = v.columnname
LEFT JOIN ad_val_rule vr ON vr."name" = v.rulename
WHERE NOT EXISTS (SELECT 1 FROM ad_process_para x WHERE x.ad_process_id = p.ad_process_id AND x.columnname = v.columnname);

-- 9.4 Process access: every role that has the customer invoice window
INSERT INTO ad_process_access (ad_process_id, ad_role_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, isreadwrite, ad_process_access_uu)
SELECT p.ad_process_id, a.ad_role_id, a.ad_client_id, 0, 'Y', now(), 100, now(), 100, 'Y', gen_random_uuid()
FROM ad_process p
JOIN (SELECT DISTINCT ad_role_id, ad_client_id FROM ad_window_access WHERE ad_window_id = 167 AND isactive = 'Y') a ON true
WHERE p.classname = 'com.santhoshbalaguru.einvoice.process.EInvoiceProcess'
  AND NOT EXISTS (SELECT 1 FROM ad_process_access x WHERE x.ad_process_id = p.ad_process_id AND x.ad_role_id = a.ad_role_id);

-- 9.5 Menu
INSERT INTO ad_menu (ad_menu_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby,
  "name", description, issummary, issotrx, isreadonly, action, ad_process_id, entitytype, ad_menu_uu)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Menu')::integer, 'N'::varchar),
       0,0,'Y',now(),100,now(),100,
       'Generate E-Invoice (IRN)','Send sales invoices to the IRP','N','Y','N','P', p.ad_process_id,'SBEINV',gen_random_uuid()
FROM ad_process p
WHERE p.classname = 'com.santhoshbalaguru.einvoice.process.EInvoiceProcess'
  AND NOT EXISTS (SELECT 1 FROM ad_menu m WHERE m.ad_process_id = p.ad_process_id);

-- 9.6 Menu tree (every client's tree)
INSERT INTO ad_treenodemm (ad_tree_id, node_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, parent_id, seqno, ad_treenodemm_uu)
SELECT t.ad_tree_id, m.ad_menu_id, t.ad_client_id, 0,'Y',now(),100,now(),100, 0, 999, gen_random_uuid()
FROM ad_menu m
JOIN ad_process p ON p.ad_process_id = m.ad_process_id
JOIN ad_tree t ON t.treetype = 'MM' AND t.isactive = 'Y' AND t.isallnodes = 'Y'
WHERE p.classname = 'com.santhoshbalaguru.einvoice.process.EInvoiceProcess'
  AND NOT EXISTS (SELECT 1 FROM ad_treenodemm n WHERE n.ad_tree_id = t.ad_tree_id AND n.node_id = m.ad_menu_id);


-- =====================================================================
-- PART 10. VERIFY (read only)
-- =====================================================================

-- 10.1 Elements. Expect up to 51 (fewer if core already has ErrorMsg or IsSuccess)
SELECT count(*) AS ei_elements FROM ad_element WHERE entitytype = 'SBEINV';

-- 10.2 Reference lists. Expect 14 rows
SELECT r."name" AS reference, l.value, l."name"
FROM ad_reference r JOIN ad_ref_list l ON l.ad_reference_id = r.ad_reference_id
WHERE r.entitytype = 'SBEINV' ORDER BY r."name", l.value;

-- 10.3 Columns per table. Expect EI_Config 16, EI_InvoiceResponse 22, and 34 across the eight core tables
SELECT t.tablename, count(*) AS columns
FROM ad_column c JOIN ad_table t ON t.ad_table_id = c.ad_table_id
WHERE c.entitytype = 'SBEINV' GROUP BY t.tablename ORDER BY t.tablename;

-- 10.4 Fields per table. Expect EI_Config 11, EI_InvoiceResponse 15, and 34 across the eight core tables
SELECT t.tablename, count(*) AS fields
FROM ad_field f
JOIN ad_tab tab ON tab.ad_tab_id = f.ad_tab_id
JOIN ad_table t ON t.ad_table_id = tab.ad_table_id
WHERE f.entitytype = 'SBEINV' GROUP BY t.tablename ORDER BY t.tablename;

-- 10.5 Process and parameters. Expect 1 process, 2 parameters
SELECT p.ad_process_id, p.value, pa.columnname, pa.seqno, pa.isrange
FROM ad_process p JOIN ad_process_para pa ON pa.ad_process_id = p.ad_process_id
WHERE p.classname = 'com.santhoshbalaguru.einvoice.process.EInvoiceProcess' ORDER BY pa.seqno;

-- 10.6 States. Expect 37 rows with a GST code
SELECT count(*) AS india_states_with_code FROM c_region WHERE c_country_id = 208 AND gststatecode IS NOT NULL;

-- 10.7 Physical columns exist. Expect 0 rows (every EI column has a database column)
SELECT t.tablename, c.columnname AS missing_in_database
FROM ad_column c
JOIN ad_table t ON t.ad_table_id = c.ad_table_id
WHERE c.entitytype = 'SBEINV'
  AND NOT EXISTS (SELECT 1 FROM information_schema.columns ic
                  WHERE ic.table_name = lower(t.tablename) AND ic.column_name = lower(c.columnname));



-- =====================================================================
-- PART 11. PROCESS "Cancel E-Invoice (IRN)"
-- =====================================================================

-- 11.1 Elements
INSERT INTO ad_element (ad_element_id,ad_client_id,ad_org_id,isactive,created,createdby,updated,updatedby,columnname,entitytype,"name",printname,description,help,po_name,po_printname,po_description,po_help,ad_element_uu,placeholder)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Element')::integer, 'N'::varchar),
       0,0,'Y',now(),100,now(),100,
       v.columnname,'SBEINV',v.name,v.printname,v.description,v.help,NULL,NULL,NULL,NULL,gen_random_uuid(),NULL
FROM (VALUES
  ('CnlRsn','Cancel Reason','Cancel Reason','Reason for cancelling the IRN','1 Duplicate, 2 Data entry mistake, 3 Order cancelled, 4 Others'),
  ('CnlRem','Cancel Remarks','Cancel Remarks','Remarks for cancelling the IRN','Length 1 to 100; no double quotes')
) AS v(columnname,name,printname,description,help)
WHERE NOT EXISTS (SELECT 1 FROM ad_element e WHERE e.columnname = v.columnname);

-- 11.2 Reason list
INSERT INTO ad_reference (ad_reference_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby,
  "name", description, help, validationtype, entitytype, isorderbyvalue, ad_reference_uu)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Reference')::integer, 'N'::varchar),
       0,0,'Y',now(),100,now(),100,
       'EI_CnlRsn','E-Invoice Cancel Reason','IRP CnlRsn, sent as is.','L','SBEINV','N',gen_random_uuid()
WHERE NOT EXISTS (SELECT 1 FROM ad_reference WHERE "name" = 'EI_CnlRsn');

INSERT INTO ad_ref_list (ad_ref_list_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby,
  value, "name", description, ad_reference_id, entitytype, ad_ref_list_uu)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Ref_List')::integer, 'N'::varchar),
       0,0,'Y',now(),100,now(),100,
       v.value, v."name", v."name", r.ad_reference_id, 'SBEINV', gen_random_uuid()
FROM (VALUES ('1','Duplicate'),('2','Data entry mistake'),('3','Order cancelled'),('4','Others')) AS v(value,"name")
JOIN ad_reference r ON r."name" = 'EI_CnlRsn' AND r.entitytype = 'SBEINV'
WHERE NOT EXISTS (SELECT 1 FROM ad_ref_list l WHERE l.ad_reference_id = r.ad_reference_id AND l.value = v.value);

-- 11.3 Validation rule: invoices that have an active IRN
INSERT INTO ad_val_rule (ad_val_rule_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, "name", description, type, code, entitytype, ad_val_rule_uu)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Val_Rule')::integer, 'N'::varchar),
       0,0,'Y',now(),100,now(),100,
       'EI_InvoiceWithActiveIrn','Reversed sales invoices whose IRN is not cancelled yet','S',
       'i.IsSOTrx=''Y'' AND EXISTS (SELECT 1 FROM C_Invoice x WHERE x.C_Invoice_ID=i.C_Invoice_ID AND x.DocStatus=''RE'' AND x.Irn IS NOT NULL AND COALESCE(x.IrnStatus,'''')<>''CNL'')','SBEINV',gen_random_uuid()
WHERE NOT EXISTS (SELECT 1 FROM ad_val_rule WHERE "name" = 'EI_InvoiceWithActiveIrn');

-- 11.4 Process
INSERT INTO ad_process (ad_process_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby,
  value, "name", description, help, accesslevel, entitytype, isreport, isdirectprint, classname,
  isbetafunctionality, isserverprocess, showhelp, ad_process_uu)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Process')::integer, 'N'::varchar),
       0,0,'Y',now(),100,now(),100,
       'EI_CancelIRN','Cancel E-Invoice (IRN)',
       'Cancels the IRN of a sales invoice at the IRP',
       'Reverse the invoice first, then run this within 24 hours of IRN generation. If an e-way bill was generated, cancel it first. A cancelled IRN cannot be regenerated for the same document number. When started from the invoice window the open invoice is used.',
       '3','SBEINV','N','N','com.santhoshbalaguru.einvoice.process.EInvoiceCancelProcess','N','N','Y',gen_random_uuid()
WHERE NOT EXISTS (SELECT 1 FROM ad_process WHERE classname = 'com.santhoshbalaguru.einvoice.process.EInvoiceCancelProcess');

-- 11.5 Parameters
INSERT INTO ad_process_para (ad_process_para_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby,
  "name", description, help, ad_process_id, seqno, ad_reference_id, ad_reference_value_id, ad_val_rule_id, columnname,
  iscentrallymaintained, fieldlength, ismandatory, isrange, ad_element_id, entitytype, ad_process_para_uu)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Process_Para')::integer, 'N'::varchar),
       0,0,'Y',now(),100,now(),100,
       e."name", e.description, e.help, p.ad_process_id, v.seq, v.ref_id, rv.ad_reference_id, vr.ad_val_rule_id, v.columnname,
       'Y', v.len, v.mand, 'N', e.ad_element_id, 'SBEINV', gen_random_uuid()
FROM (VALUES
  ('C_Invoice_ID', 10, 30, 10, 'N', 'EI_InvoiceWithActiveIrn', NULL),
  ('CnlRsn',       20, 17,  1, 'Y', NULL,                      'EI_CnlRsn'),
  ('CnlRem',       30, 10,100, 'N', NULL,                      NULL)
) AS v(columnname, seq, ref_id, len, mand, rulename, refname)
JOIN ad_process p ON p.classname = 'com.santhoshbalaguru.einvoice.process.EInvoiceCancelProcess'
JOIN ad_element e ON e.columnname = v.columnname
LEFT JOIN ad_val_rule vr ON vr."name" = v.rulename
LEFT JOIN ad_reference rv ON rv."name" = v.refname AND rv.entitytype = 'SBEINV'
WHERE NOT EXISTS (SELECT 1 FROM ad_process_para x WHERE x.ad_process_id = p.ad_process_id AND x.columnname = v.columnname);

-- 11.6 Process access. Cancelling is sensitive: EDIT the role list.
INSERT INTO ad_process_access (ad_process_id, ad_role_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, isreadwrite, ad_process_access_uu)
SELECT p.ad_process_id, r.ad_role_id, r.ad_client_id, 0, 'Y', now(), 100, now(), 100, 'Y', gen_random_uuid()
FROM ad_process p
JOIN ad_role r ON r."name" IN ('GardenWorld Admin')   -- <-- roles allowed to cancel IRNs
WHERE p.classname = 'com.santhoshbalaguru.einvoice.process.EInvoiceCancelProcess'
  AND NOT EXISTS (SELECT 1 FROM ad_process_access x WHERE x.ad_process_id = p.ad_process_id AND x.ad_role_id = r.ad_role_id);

-- 11.7 Menu and menu tree
INSERT INTO ad_menu (ad_menu_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby,
  "name", description, issummary, issotrx, isreadonly, action, ad_process_id, entitytype, ad_menu_uu)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Menu')::integer, 'N'::varchar),
       0,0,'Y',now(),100,now(),100,
       'Cancel E-Invoice (IRN)','Cancel the IRN of a sales invoice','N','Y','N','P', p.ad_process_id,'SBEINV',gen_random_uuid()
FROM ad_process p
WHERE p.classname = 'com.santhoshbalaguru.einvoice.process.EInvoiceCancelProcess'
  AND NOT EXISTS (SELECT 1 FROM ad_menu m WHERE m.ad_process_id = p.ad_process_id);

INSERT INTO ad_treenodemm (ad_tree_id, node_id, ad_client_id, ad_org_id, isactive, created, createdby, updated, updatedby, parent_id, seqno, ad_treenodemm_uu)
SELECT t.ad_tree_id, m.ad_menu_id, t.ad_client_id, 0,'Y',now(),100,now(),100, 0, 999, gen_random_uuid()
FROM ad_menu m
JOIN ad_process p ON p.ad_process_id = m.ad_process_id
JOIN ad_tree t ON t.treetype = 'MM' AND t.isactive = 'Y' AND t.isallnodes = 'Y'
WHERE p.classname = 'com.santhoshbalaguru.einvoice.process.EInvoiceCancelProcess'
  AND NOT EXISTS (SELECT 1 FROM ad_treenodemm n WHERE n.ad_tree_id = t.ad_tree_id AND n.node_id = m.ad_menu_id);


-- 14. Optional 30-day reporting pre-check (AATO 10 crore and above). Re-runnable.
-- EDIT: set the client id (11 = GardenWorld) and the value (0 = no check).
INSERT INTO ad_sysconfig (ad_sysconfig_id, ad_client_id, ad_org_id, created, updated, createdby, updatedby, isactive,
  "name", value, description, entitytype, configurationlevel, ad_sysconfig_uu)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_SysConfig')::integer, 'N'::varchar),
       11, 0, now(), now(), 100, 100, 'Y',
       'EI_MAX_INVOICE_AGE_DAYS', '30',
       'E-Invoice: days after the invoice date up to which the IRP accepts reporting (30 for AATO 10 crore and above). 0 = no check.',
       'SBEINV', 'C', gen_random_uuid()
WHERE NOT EXISTS (SELECT 1 FROM ad_sysconfig
                  WHERE "name" = 'EI_MAX_INVOICE_AGE_DAYS' AND ad_client_id = 11 AND ad_org_id = 0);