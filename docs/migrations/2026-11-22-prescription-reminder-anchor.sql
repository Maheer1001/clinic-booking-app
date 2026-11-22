-- Dose (treatment) reminders keep a fixed rhythm across logins: doses are due at
-- schedule_anchor + k * interval_between_doses. Null for rows saved before this column existed;
-- the app fills it in the first time such a reminder is restored.
-- Additive and idempotent; also run at start-up by app.DB.initTables.
ALTER TABLE "PrescriptionReminder" ADD COLUMN IF NOT EXISTS schedule_anchor TIMESTAMP;
