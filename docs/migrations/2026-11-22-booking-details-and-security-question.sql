-- 2026-11-22: booking personal details + account security question.
-- Additive and idempotent: safe to run more than once, nothing is dropped or renamed.
-- The same statements run from app.DB.initTables, so a fresh database gets them too.

-- One row per appointment with the personal details entered on the booking "Your details" step.
-- Kept out of "Appointment" so the sensitive fields live in one table that a future staff/clinician
-- role can be granted (or denied) on its own, and so the many Appointment queries never touch them.
-- A booking of several services stores the same details once per appointment.
CREATE TABLE IF NOT EXISTS "BookingDetails" (
    app_id INTEGER PRIMARY KEY REFERENCES "Appointment"(app_id),
    first_name VARCHAR(50),
    last_name VARCHAR(50),
    dob DATE,
    phone_number VARCHAR(20),
    contact_email VARCHAR(255),
    gender VARCHAR(100),
    sexuality VARCHAR(100),
    accessibility_needs VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Security question chosen at sign-up. The answer is normalised then hashed with the password hasher.
-- Failed answers are counted in the database so a lockout survives restarting the app or logging in again.
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS security_question VARCHAR(50);
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS security_answer_hash VARCHAR(255);
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS security_failed_attempts INTEGER;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS security_locked_until TIMESTAMP;
