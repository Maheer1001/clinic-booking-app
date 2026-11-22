-- 2026-11-22: clinic admin approves new staff (docs/staff-login-plan.md, owner decision).
-- Additive and idempotent: safe to run more than once. The same statements run from
-- app.DB.applyStaffLoginMigration, so a fresh database gets them too.
--
-- A valid clinic code at sign-up now creates a PENDING membership; the clinic admin approves it
-- (ACTIVE) or rejects it (the row is deleted). Only ACTIVE memberships give access to a clinic.
-- Memberships from before this change (seeded admins, existing staff) become ACTIVE.

ALTER TABLE "StaffClinic" ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'ACTIVE';
UPDATE "StaffClinic" SET status = 'ACTIVE' WHERE status IS NULL;
-- New rows that do not say otherwise wait for approval.
ALTER TABLE "StaffClinic" ALTER COLUMN status SET DEFAULT 'PENDING';
ALTER TABLE "StaffClinic" ADD COLUMN IF NOT EXISTS approved_by INTEGER;
ALTER TABLE "StaffClinic" ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;
