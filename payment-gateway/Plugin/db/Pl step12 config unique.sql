-- Step 12: at most one ACTIVE Payment Gateway Configuration per client and organization.
-- PaymentGatewayConfig.load takes the first active row (organization first, then client-wide), so two
-- active rows for the same organization would make the choice arbitrary. Safe to re-run.
-- After this, saving a second active configuration for the same organization is refused by the database;
-- deactivate the old one first.

BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM PL_Config
                WHERE IsActive = 'Y'
                GROUP BY AD_Client_ID, AD_Org_ID
               HAVING COUNT(*) > 1) THEN
        RAISE EXCEPTION 'Duplicate active PL_Config rows exist; deactivate the extra ones, then run this script again';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS PL_Config_ClientOrg
    ON PL_Config (AD_Client_ID, AD_Org_ID)
 WHERE IsActive = 'Y';

COMMIT;

-- checks (read only)
-- duplicates: expect 0 rows
SELECT AD_Client_ID, AD_Org_ID, COUNT(*) AS active_rows
  FROM PL_Config WHERE IsActive = 'Y'
 GROUP BY AD_Client_ID, AD_Org_ID HAVING COUNT(*) > 1;

-- index: expect 1 row
SELECT indexname FROM pg_indexes WHERE tablename = 'pl_config' AND indexname = 'pl_config_clientorg';
