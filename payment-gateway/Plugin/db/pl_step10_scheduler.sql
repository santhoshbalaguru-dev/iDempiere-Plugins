-- Step 10: scheduler for the Sync Payment Links process, every N minutes.
-- Run after pl_step9_sync_process.sql. Safe to re-run; changing the minutes and re-running
-- points the scheduler at the new schedule.
-- The scheduler runs as the Supervisor user, with that user's first active role, in the client and
-- organization below. Change the three values in the first statement if yours differ.
-- If a step fails partway, run ROLLBACK; before trying again.

BEGIN;

DROP TABLE IF EXISTS pg_temp.pl_sched_cfg;
CREATE TEMP TABLE pl_sched_cfg AS
SELECT 11            AS client_id,        -- client whose links are synced
       11            AS org_id,           -- organization of the scheduler record
       'GardenAdmin' AS supervisor_name,  -- user the process runs as (also notified when a run fails)
       5             AS minutes;          -- how often

DO $$
DECLARE
    c      RECORD;
    v_user INTEGER;
BEGIN
    SELECT * INTO c FROM pg_temp.pl_sched_cfg;

    IF NOT EXISTS (SELECT 1 FROM AD_Process WHERE Value = 'PL_SyncPaymentLinks') THEN
        RAISE EXCEPTION 'Run pl_step9_sync_process.sql first';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM ad_sequence WHERE name IN ('AD_Schedule'))
       OR NOT EXISTS (SELECT 1 FROM ad_sequence WHERE name IN ('AD_Scheduler')) THEN
        RAISE EXCEPTION 'ID sequences for AD_Schedule / AD_Scheduler not found';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_name = 'ad_scheduler' AND column_name = 'ad_schedule_id')
       OR NOT EXISTS (SELECT 1 FROM information_schema.columns
                       WHERE table_name = 'ad_schedule' AND column_name = 'scheduletype') THEN
        RAISE EXCEPTION 'This iDempiere version has a different scheduler layout; create the Scheduler record in the UI instead';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM AD_Client WHERE AD_Client_ID = c.client_id) THEN
        RAISE EXCEPTION 'Client % not found', c.client_id;
    END IF;

    SELECT AD_User_ID INTO v_user FROM AD_User
     WHERE AD_Client_ID = c.client_id AND Name = c.supervisor_name AND IsActive = 'Y';
    IF v_user IS NULL THEN
        RAISE EXCEPTION 'User "%" not found in client %; change supervisor_name at the top of this script',
                        c.supervisor_name, c.client_id;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM AD_User_Roles ur JOIN AD_Role r ON r.AD_Role_ID = ur.AD_Role_ID
                    WHERE ur.AD_User_ID = v_user AND ur.IsActive = 'Y' AND r.IsActive = 'Y') THEN
        RAISE EXCEPTION 'User "%" has no active role; the scheduler needs one', c.supervisor_name;
    END IF;
END $$;

-- 10.1 schedule: every N minutes
INSERT INTO AD_Schedule
    (AD_Schedule_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     Name, Description, ScheduleType, FrequencyType, Frequency, IsIgnoreProcessingTime, IsSystemSchedule,
     AD_Schedule_UU)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Schedule')::integer, 'N'::varchar),
       c.client_id, 0, 'Y', NOW(), 100, NOW(), 100,
       'Payment Links: every ' || c.minutes || ' minutes',
       'Runs every ' || c.minutes || ' minutes',
       'F', 'M', c.minutes, 'N', 'N', gen_random_uuid()
  FROM pg_temp.pl_sched_cfg c
 WHERE NOT EXISTS (SELECT 1 FROM AD_Schedule s
                    WHERE s.AD_Client_ID = c.client_id
                      AND s.Name = 'Payment Links: every ' || c.minutes || ' minutes');

-- 10.2 scheduler record for the process
INSERT INTO AD_Scheduler
    (AD_Scheduler_ID, AD_Client_ID, AD_Org_ID, IsActive, Created, CreatedBy, Updated, UpdatedBy,
     Name, Description, AD_Process_ID, AD_Schedule_ID, Supervisor_ID, KeepLogDays, Processing,
     AD_Scheduler_UU)
SELECT nextidfunc((SELECT ad_sequence_id FROM ad_sequence WHERE name = 'AD_Scheduler')::integer, 'N'::varchar),
       c.client_id, c.org_id, 'Y', NOW(), 100, NOW(), 100,
       'Sync Payment Links', 'Updates payment links from the payment service and posts received payments',
       p.AD_Process_ID,
       (SELECT s.AD_Schedule_ID FROM AD_Schedule s
         WHERE s.AD_Client_ID = c.client_id AND s.Name = 'Payment Links: every ' || c.minutes || ' minutes'),
       (SELECT u.AD_User_ID FROM AD_User u
         WHERE u.AD_Client_ID = c.client_id AND u.Name = c.supervisor_name AND u.IsActive = 'Y'),
       7, 'N', gen_random_uuid()
  FROM pg_temp.pl_sched_cfg c
  JOIN AD_Process p ON p.Value = 'PL_SyncPaymentLinks'
 WHERE NOT EXISTS (SELECT 1 FROM AD_Scheduler x
                    WHERE x.AD_Client_ID = c.client_id AND x.AD_Process_ID = p.AD_Process_ID);

-- repair / re-run with other settings: point the existing scheduler at the current schedule and user
UPDATE AD_Scheduler x
   SET AD_Schedule_ID = (SELECT s.AD_Schedule_ID FROM AD_Schedule s
                          WHERE s.AD_Client_ID = c.client_id
                            AND s.Name = 'Payment Links: every ' || c.minutes || ' minutes'),
       Supervisor_ID  = (SELECT u.AD_User_ID FROM AD_User u
                          WHERE u.AD_Client_ID = c.client_id AND u.Name = c.supervisor_name AND u.IsActive = 'Y'),
       AD_Org_ID      = c.org_id,
       IsActive       = 'Y'
  FROM pg_temp.pl_sched_cfg c, AD_Process p
 WHERE p.Value = 'PL_SyncPaymentLinks'
   AND x.AD_Client_ID = c.client_id AND x.AD_Process_ID = p.AD_Process_ID;

COMMIT;

-- checks (read only)
-- expect 1 row: the scheduler, its schedule, supervisor and process
SELECT sc.Name AS scheduler, sc.IsActive, sh.Name AS schedule, sh.Frequency, sh.FrequencyType,
       u.Name AS supervisor, p.Value AS process
  FROM AD_Scheduler sc
  JOIN AD_Process p   ON p.AD_Process_ID = sc.AD_Process_ID
  JOIN AD_Schedule sh ON sh.AD_Schedule_ID = sc.AD_Schedule_ID
  LEFT JOIN AD_User u ON u.AD_User_ID = sc.Supervisor_ID
 WHERE p.Value = 'PL_SyncPaymentLinks';

-- after the server has been restarted: the latest runs and their results
SELECT pi.Created, pi.IsProcessing, pi.Result, pi.ErrorMsg
  FROM AD_PInstance pi JOIN AD_Process p ON p.AD_Process_ID = pi.AD_Process_ID
 WHERE p.Value = 'PL_SyncPaymentLinks'
 ORDER BY pi.Created DESC LIMIT 10;

-- NEXT: restart iDempiere (the server reads the schedulers at startup; changes made with SQL are not noticed while it runs).