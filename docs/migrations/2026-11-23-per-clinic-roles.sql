-- 2026-11-23: staff roles are per clinic.
-- Additive and idempotent: safe to run more than once. The same statements run from
-- app.DB.applyPerClinicRolesMigration (called by app.DB.initTables), so a fresh database gets them too.
-- Needs 2026-11-22-staff-login.sql and 2026-11-22-staff-approval.sql first.
--
-- Before: "User".role (CLINICIAN / CLINIC_ADMIN) applied to every clinic the person belonged to.
-- After:  each "StaffClinic" membership has its own role, so someone can be CLINIC_ADMIN at one
--         clinic and CLINICIAN at another; every permission check uses the role at the clinic being
--         acted on. "User".role stays and from now on only says PATIENT or staff (login routing).
--
-- Existing memberships (ACTIVE and PENDING) copy the user's current account-wide role, so nobody
-- gains or loses anything by running this. A membership whose user has no staff role gets CLINICIAN
-- (the lesser role). Review afterwards: anyone who was CLINIC_ADMIN is now admin at each of their
-- clinics, exactly as before; demote them where that is not wanted.

BEGIN;

ALTER TABLE "StaffClinic" ADD COLUMN IF NOT EXISTS role VARCHAR(20);

-- Only rows not migrated yet (role IS NULL), so a re-run never overwrites a later promote/demote.
UPDATE "StaffClinic" SET role = COALESCE(
    (SELECT u.role FROM "User" u
     WHERE u.user_id = "StaffClinic".user_id AND u.role IN ('CLINICIAN', 'CLINIC_ADMIN')),
    'CLINICIAN')
WHERE role IS NULL;

-- Anything else that is not a staff role grants nothing extra: least privilege.
UPDATE "StaffClinic" SET role = 'CLINICIAN' WHERE role NOT IN ('CLINICIAN', 'CLINIC_ADMIN');

ALTER TABLE "StaffClinic" ALTER COLUMN role SET DEFAULT 'CLINICIAN';
ALTER TABLE "StaffClinic" ALTER COLUMN role SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'staff_clinic_role_check') THEN
        ALTER TABLE "StaffClinic" ADD CONSTRAINT staff_clinic_role_check
            CHECK (role IN ('CLINICIAN', 'CLINIC_ADMIN'));
    END IF;
END $$;

COMMIT;

-- Check (expect no rows):
--   SELECT user_id, clinic_id, role FROM "StaffClinic" WHERE role IS NULL OR role NOT IN ('CLINICIAN', 'CLINIC_ADMIN');
