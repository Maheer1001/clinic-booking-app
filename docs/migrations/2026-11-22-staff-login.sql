-- 2026-11-22: clinician / clinic admin login (docs/staff-login-plan.md, WP0).
-- Additive and idempotent: safe to run more than once, nothing is dropped or renamed.
-- The same statements run from app.DB.initTables (see DB.applyStaffLoginMigration), so a fresh
-- database gets them too. The append-only trigger at the end is PostgreSQL only.
--
-- Staff codes are NOT in this file: they are hashed with service.PasswordHasher, so they are
-- seeded from Java. Only when DEMO_STAFF_CODES=true (demo only), app.StaffSeed.seedMissingStaffCodes
-- runs from DB.initTables and gives every clinic WITHOUT a code the demo code clinic<clinic_id>
-- (clinic1, clinic2, ...), marked code_is_demo. Existing or rotated codes are never overwritten.
-- The first CLINIC_ADMIN of a clinic is created with the dev main app.StaffSeed as a NEW account
-- (must_reset_password; an existing email is refused, never promoted). The password comes from an
-- environment variable, never SQL or the command line.

-- Roles: PATIENT (default) | CLINICIAN | CLINIC_ADMIN. Same email/password login as patients.
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS role VARCHAR(20) DEFAULT 'PATIENT';
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS must_reset_password BOOLEAN DEFAULT FALSE;
-- Watermark for the "new results" alert: results released (or PENDING -> final) after this are new.
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS last_results_seen_at TIMESTAMP;
UPDATE "User" SET role = 'PATIENT' WHERE role IS NULL;

-- Which clinic(s) a staff member works at. One staff member can work at several clinics.
CREATE TABLE IF NOT EXISTS "StaffClinic" (
    user_id INTEGER NOT NULL REFERENCES "User"(user_id),
    clinic_id INTEGER NOT NULL,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, clinic_id)
);

-- The code staff enter at sign-up to prove they work at a clinic. Stored hashed (PasswordHasher),
-- one per clinic; a clinic admin can rotate it.
CREATE TABLE IF NOT EXISTS "ClinicStaffCode" (
    clinic_id INTEGER PRIMARY KEY,
    code_hash VARCHAR(255) NOT NULL,
    rotated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    rotated_by INTEGER
);
-- TRUE for a seeded demo code (clinic<id>): the admin screen asks for it to be rotated.
ALTER TABLE "ClinicStaffCode" ADD COLUMN IF NOT EXISTS code_is_demo BOOLEAN DEFAULT FALSE;
-- Codes seeded before the column existed: only an admin-set code has rotated_by.
UPDATE "ClinicStaffCode" SET code_is_demo = TRUE WHERE rotated_by IS NULL AND code_is_demo = FALSE;

-- Wrong staff codes at sign-up, keyed by a SHA-256 of the normalised email (no plaintext email).
-- Counted in the database so the attempt limit survives restarting the app.
CREATE TABLE IF NOT EXISTS "StaffCodeAttempt" (
    attempt_id SERIAL PRIMARY KEY,
    email_hash VARCHAR(64) NOT NULL,
    attempted_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS staff_code_attempt_email_idx ON "StaffCodeAttempt" (email_hash, attempted_at);

-- Appointment outcome recorded by staff. Status may now also be COMPLETED or NO_SHOW.
ALTER TABLE "Appointment" ADD COLUMN IF NOT EXISTS staff_notes TEXT;
ALTER TABLE "Appointment" ADD COLUMN IF NOT EXISTS attended_at TIMESTAMP;

-- Tests are drafts until released; patients only see released tests.
ALTER TABLE "Test" ADD COLUMN IF NOT EXISTS app_id INTEGER;
ALTER TABLE "Test" ADD COLUMN IF NOT EXISTS clinician_id INTEGER;
ALTER TABLE "Test" ADD COLUMN IF NOT EXISTS released_at TIMESTAMP;
-- Set when a result changes (e.g. PENDING -> POSITIVE) so the patient is alerted again.
ALTER TABLE "TestResult" ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

-- Every staff read of a patient record and every staff write. Ids only, never record contents.
CREATE TABLE IF NOT EXISTS "AuditLog" (
    audit_id SERIAL PRIMARY KEY,
    staff_user_id INTEGER,
    clinic_id INTEGER,
    action VARCHAR(40) NOT NULL,
    entity VARCHAR(30),
    entity_id INTEGER,
    patient_user_id INTEGER,
    at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS audit_log_clinic_idx ON "AuditLog" (clinic_id, at);

-- One-off backfills are recorded here so they never run twice.
CREATE TABLE IF NOT EXISTS "SchemaMigration" (
    name VARCHAR(100) PRIMARY KEY,
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Once: tests that existed before draft/release are treated as released, so patients keep seeing
-- them, and existing users have already "seen" them, so nobody gets a flood of old alerts.
UPDATE "Test" SET released_at = COALESCE(created_at, CURRENT_TIMESTAMP)
WHERE released_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM "SchemaMigration" WHERE name = '2026-11-22-release-existing-tests');
UPDATE "User" SET last_results_seen_at = CURRENT_TIMESTAMP
WHERE last_results_seen_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM "SchemaMigration" WHERE name = '2026-11-22-release-existing-tests');
INSERT INTO "SchemaMigration" (name)
SELECT '2026-11-22-release-existing-tests'
WHERE NOT EXISTS (SELECT 1 FROM "SchemaMigration" WHERE name = '2026-11-22-release-existing-tests');

-- PostgreSQL only: the audit log cannot be changed or deleted from the app.
CREATE OR REPLACE FUNCTION audit_log_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'AuditLog is append-only';
END;
$$ LANGUAGE plpgsql;
CREATE OR REPLACE TRIGGER audit_log_append_only
    BEFORE UPDATE OR DELETE ON "AuditLog"
    FOR EACH ROW EXECUTE FUNCTION audit_log_append_only();
