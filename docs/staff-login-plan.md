# Clinician / staff login: plan

Status: planned, not started. Build after the UI, bug-fix and UX work on `fix/reminders-email-qa`.

## Decisions (owner, 2026-11-22)
- **Staff sign-up needs a clinic code.** Staff register themselves but must enter their clinic's unique staff code to prove they work there; for this demo the codes are simply `clinic1`, `clinic2`, ... The code decides which clinic the account is tied to. Store codes hashed in a `"ClinicStaffCode"` table (or column on `"Clinic"`) so they can be rotated later.
- **Draft then release** for test results, with a confirm step before releasing a positive.
- **Roles: clinician + clinic admin** in the first version. Clinic admin can manage the clinic's staff (approve/remove, rotate the staff code) and timeslots/capacity.
- **Staff and patients are separate accounts; staff see only their own clinic(s)** - patients with an appointment there, and only records from those clinics.
- Data model is the demo/portfolio one (direct DB from the client); the limitation is documented.

## What the code already tells us
- **No server.** The JavaFX client talks to Neon directly over JDBC with the `.env` credentials (`app/DB.java`). Clinic scoping in DAOs controls what the app *shows*; anyone with `.env` can read every table. Fine for a demo/portfolio app; real patient data needs a backend API first.
- **Groundwork exists:** `Prescription.prescribing_clinician_id`, `Test.clinic_id`, `Test.clinician_notes` / `treatment_instructions`, `Medicine`, `Timeslot.capacity`, `TestResultEmail`. Nothing ever sets an appointment to `COMPLETED`.
- **New-result alert** counts tests in memory (`Data.lastSeenTestCount`), so results added while logged out, or PENDING -> POSITIVE changes, never alert.
- `SessionDAO` exists but is unused. `Navigator` hard-codes the patient tabs.

## 1. Roles and auth
- `"User".role` = `PATIENT` (default) | `CLINICIAN` | `CLINIC_ADMIN`. Same email, password and `PasswordHasher` as patients.
- `"StaffClinic"(user_id, clinic_id)` so one staff member can work at several clinics.
- Update 2026-11-23 (docs/migrations/2026-11-23-per-clinic-roles.sql): the role is per clinic, on `"StaffClinic".role` (`CLINICIAN` | `CLINIC_ADMIN`). `"User".role` now only separates patients from staff for login routing; every permission check uses the role at the clinic being acted on.
- Staff sign up from the normal sign-up screen via "I'm clinic staff", entering their clinic's staff code (`clinic1`, `clinic2`, ... in the demo). A valid code creates a CLINICIAN tied to that clinic; a wrong code is rejected with an attempt limit. The first admin per clinic is seeded; admins can promote/remove staff and rotate the code.
- Login routing: `loadLogin` also returns role + clinic ids; staff get `StaffNavigator`, patients `MainView`. Reminder restore and patient alerts are skipped for staff.
- Authorisation: a single `service/StaffService` entry point. Every call takes a `clinicId`, re-checks `StaffClinic` membership and role from the DB, and every `StaffDAO` query filters by clinic in SQL. A patient is visible only if they have an appointment at that clinic. Writes re-check the record's clinic.

## 2. Features
**MVP**
- Clinic day list (date picker, appointment cards: time, service, patient, status; booking details on the detail page).
- Appointment detail: mark attended (`COMPLETED`) / `NO_SHOW`, staff notes (separate from the patient's own notes).
- Patient view: that patient's appointments, tests and prescriptions at this clinic.
- Create/edit a Test with results (condition + POSITIVE / NEGATIVE / PENDING), clinician notes, treatment instructions.
- Draft then "Release to patient" (`released_at`); patients only see released tests. Confirm dialog before releasing a positive.
- Prescribe from `Medicine` (dose, doses per day, days between doses, start/end, instructions); "Stop early".
- Patient notified on release: discreet email ("a new result is available", never the result) + in-app alert based on `released_at > "User".last_results_seen_at` (stored in DB, replaces the in-memory count).

**Later:** timeslot/capacity management, staff admin screen, medicine catalogue editor, audit log viewer.

## 3. Screens (420px)
`Navigator` refactored to take a tab config; staff tabs **Today / Patients / Settings**, clinic switcher if more than one clinic. `StaffDayView`, `StaffAppointmentView`, `StaffPatientView`, `TestEditorView`, `PrescriptionEditorView`, all from the shared `UI` helpers.

## 4. Schema (additive, idempotent)
```sql
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS role VARCHAR(20) DEFAULT 'PATIENT';
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS must_reset_password BOOLEAN DEFAULT FALSE;
ALTER TABLE "User" ADD COLUMN IF NOT EXISTS last_results_seen_at TIMESTAMP;
CREATE TABLE IF NOT EXISTS "StaffClinic"(user_id INT REFERENCES "User", clinic_id INT, PRIMARY KEY(user_id, clinic_id));
ALTER TABLE "Appointment" ADD COLUMN IF NOT EXISTS staff_notes TEXT;
ALTER TABLE "Appointment" ADD COLUMN IF NOT EXISTS attended_at TIMESTAMP;
ALTER TABLE "Test" ADD COLUMN IF NOT EXISTS app_id INT;
ALTER TABLE "Test" ADD COLUMN IF NOT EXISTS clinician_id INT;
ALTER TABLE "Test" ADD COLUMN IF NOT EXISTS released_at TIMESTAMP;
ALTER TABLE "TestResult" ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;
CREATE TABLE IF NOT EXISTS "AuditLog"(audit_id SERIAL PRIMARY KEY, staff_user_id INT, clinic_id INT, action VARCHAR(40),
  entity VARCHAR(30), entity_id INT, patient_user_id INT, at TIMESTAMP DEFAULT now());
```
Backfill existing tests as released, once, so current patients keep seeing their results.

## 5. Privacy and security
- Shared DB credential is the hard limit (document in `docs/ARCHITECTURE.md`).
- Sexual-health results, sexuality and gender are special-category data: detail screens only, never in list cards or logs.
- Append-only `AuditLog` for every staff read of a patient record and every write.
- Positive results: release step + confirmation; notifications never state the result.
- Account deletion must handle `StaffClinic` and the new columns.
- Staff idle timeout via `SessionDAO`; staff can't book as patients.
- When `SessionDAO` is wired up, a password or email change (`AccountSecurityService`) must revoke the user's other sessions (e.g. `SessionDAO.deleteAllForUser(userId, exceptSessionId)` or a per-user session version checked on restore). Today nothing persists or restores sessions, so there is nothing to revoke yet.

## 6. Work packages
| WP | What | Needs |
|---|---|---|
| WP0 | Schema + model (`DB.java`, `User`, `StaffRole`, `AppointmentStatus`, seed) | – |
| WP1 | Auth + routing (`UserDAO`, `Data`, `LoginController`, logout) | WP0 |
| WP2 | `StaffDAO` + `StaffService` (clinic guard, audit, notify) | WP0 |
| WP3 | Patient side of release (`TestDAO` released only, timestamp alert) | WP0 |
| WP4 | `Navigator` tab-config refactor | – |
| WP5 | Staff screens + controllers | WP2, WP4 |
| WP6 | Release notification | WP2, WP3 |

WP1, WP2, WP3, WP4 can run in parallel once WP0 lands.

**Tests:** `StaffService` against a fake repository (wrong clinic rejected, non-staff rejected, unrelated patient hidden, every action audited); two-clinic DAO tests proving no leakage; migrations run twice safely; draft hidden / release alerts once / PENDING -> POSITIVE alerts again; login routing; 420px screenshots.
