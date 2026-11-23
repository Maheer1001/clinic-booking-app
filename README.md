# Sexual Health Clinic App

Book a sexual-health clinic appointment in under a minute: a JavaFX desktop app to find a clinic by postcode, book a slot, and keep track of test results, prescriptions and reminders.

![Demo: sign in, book at your usual clinic or the nearest one on the map, choose a service and time, confirm, reminders, results and prescriptions, then the staff side: the day list, patient records, releasing a result, shortening a course and approving staff](media/demo.gif)

![Screens: upcoming appointments, clinic search by postcode, times generated from opening hours, test results](media/screens.png)

> **Bring your own database.** The app needs a PostgreSQL database that you provide; no shared or hosted database is included. Point `DB_URL`, `DB_USERNAME` and `DB_PASSWORD` in `.env` at any PostgreSQL 13+ (a local install, Docker, or a free hosted one such as Neon or Supabase). The tables are created automatically on first run, and `docs/sample-data.sql` adds demo clinics. See [Getting API keys / credentials](#getting-api-keys--credentials).

> **Group project.** Built by a team for the University of Southampton *Software Design and Development Project* (COMP2300, 2025–26). This repo is a snapshot of the code, which I continued to develop after the module ended.

## Features

The demo above follows a patient through the app:

1. **Sign in.** Accounts with sign-up checks, PBKDF2-hashed passwords, password reset by an emailed 6-digit code, and a terms and privacy flow.
2. **Everything at a glance.** The home screen greets you, counts down to your next appointment and lists everything you have booked.
3. **Find the nearest clinic.** Search by postcode (free [postcodes.io](https://postcodes.io) API). Clinics are sorted by distance and pinned on an interactive OpenStreetMap map (Leaflet, no API key). Click a pin for the address and choose the clinic straight from the popup. You can save a default clinic, which is offered first next time.
4. **Choose a service.** STI test, HIV test, contraception and so on; tick one or more and expand each to see what it involves.
5. **Pick a free time.** Slots are generated from each clinic's opening hours and the service's length. Only booked slots are stored, and a slot drops out once its bookings reach capacity (reserved with a single conditional update, so it can't be overbooked).
6. **Check and confirm.** A five-step progress bar runs through the booking; the review page shows the clinic, time and your details before you confirm.
7. **Confirmation email.** Booking, reschedule and cancellation emails are HTML (Thymeleaf templates) and are sent in the background, alongside an in-app notification with a booking reference.
8. **Reminders your way.** Set how far ahead you are reminded, repeat reminders, quiet hours, discreet wording, and saved defaults for new bookings; check-up reminders if it has been a while since your last visit. Dose reminders for a prescription keep a fixed rhythm and stop when staff stop or shorten the course. While the app is open, reminders and new-result alerts also show as in-app popups, one at a time (several new results come as one popup).
9. **Manage bookings.** Reschedule to another free slot (the old one is released) or cancel; cancelling always asks first and defaults to keeping the appointment.
10. **Track tests and prescriptions.** Test results with a clear status, treatment and clinician notes (and a shortcut to book a retest after a positive result); current and past prescriptions with dose, frequency and course dates; past appointments. Search, sort and filter each list by date.
11. **Accessible.** Keyboard navigation with visible focus rings, screen-reader labels, and per-user high contrast, bold text and text size settings that apply instantly.

![More screens: booking confirmed, reschedule, prescriptions, high contrast mode](media/screens-more.png)

## Staff side

Clinic staff use the same app and the same login screen, with their own tabs:

- **Today**: the clinic's appointments for a day. Open one to see the booking details, mark it attended or a no-show, and keep staff-only notes.
- **Patients**: search patients who have an appointment at the clinic and open their record (appointments, tests, prescriptions). Add a test result: it is saved as a draft and the patient sees nothing until you **Release to patient** (a positive result asks you to confirm first). The patient then gets a discreet email that never says the result, and an in-app alert. Prescribe from the medicine list, then shorten the course or stop it early; dose reminders end with it.
- **Clinic** (clinic admins only): approve or reject new staff, make staff admins or clinicians, remove them, rotate the staff code, and add or edit timeslots.

Roles are per clinic: someone can be a clinic admin at one clinic and a clinician at another. Staff who belong to more than one clinic switch between them with the clinic picker on the staff pages, and the Clinic tab shows admin tools only at clinics where they are an admin.

Staff and patient accounts are separate: an email that already has an account can't become staff. The app talks to the database directly, so clinic scoping controls what the app shows, not who can read the database (see [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)).

### Staff codes

Each clinic has a staff code. Someone who signs up with **I'm clinic staff** and a clinic's code becomes a clinician at that clinic, waiting for approval. Only a hash of the code is stored. A clinic admin can rotate it on the **Clinic** tab: the new code is shown once, the old one stops working at once, and staff who already have access keep it. Wrong codes are rate-limited (5 tries per email and 50 overall per 15 minutes).

`DEMO_STAFF_CODES=true` in `.env` is for demo databases: on startup every clinic with no code gets `clinic<id>` (`clinic1`, `clinic2`, ...). It never overwrites a code, and the Clinic tab asks the admin to rotate a demo code. Leave it `false` for anything real; staff sign-up then fails until an admin creates a code (**Clinic > Create staff code**).

### First clinic admin

There is no sign-up for admins. Create a clinic's first admin with `app.StaffSeed`, against the database in `.env`. The email must not have an account yet, the password comes from an environment variable (never the command line), and the admin must choose a new password at first login.

```powershell
$env:STAFF_SEED_PASSWORD = "<temporary password, 8+ characters with a letter and a number>"
mvn -q compile org.codehaus.mojo:exec-maven-plugin:3.1.0:java "-Dexec.mainClass=app.StaffSeed" "-Dexec.args=admin <clinicId> <email>"
Remove-Item Env:STAFF_SEED_PASSWORD
```

`<clinicId>` is a `clinic_id` from the `"Clinic"` table (the sample data has 1 to 3). Further admins are promoted from the Clinic tab.

### Clinician sign-up and approval

1. The admin gives the clinic's staff code to the new clinician.
2. The clinician clicks **Create an account**, ticks **I'm clinic staff** and enters the code.
3. Until approved, logging in shows **Waiting for approval**.
4. The admin opens **Clinic > Waiting for approval** and clicks **Approve** (or **Reject**). At their next login the clinician gets the staff tabs.

**Remove** on the Clinic tab takes someone's access away straight away; their account stays but can't use the staff area. They can't sign up again with the same email, so use a different email if they come back.

## Stack

Java 21, JavaFX (FXML, MaterialFX, ControlsFX, WebView + Leaflet), PostgreSQL, a DAO/repository data layer, JavaMail with Thymeleaf templates, and `dotenv-java` for configuration. Tests use JUnit 5 with H2. The build uses Maven.

## Prerequisites

- **JDK 21** or newer (`java -version`). Install with `winget install --id EclipseAdoptium.Temurin.21.JDK -e`.
- **Maven 3.9+** (`mvn -v`). Install with `winget install --id Apache.Maven -e`, or use the one bundled with IntelliJ.
- **PostgreSQL** 13 or newer, local or hosted, that you set up yourself (only needed to run the app and the integration tests, not for `mvn test`).
- Optional: an SMTP account for sending emails.

## Setup

```powershell
git clone https://github.com/Maheer1001/clinic-booking-app.git
cd clinic-booking-app
mvn -DskipTests package      # downloads dependencies and compiles
```

Then create a database for the app (next section) and put its details in `.env`. Nothing is shared: every clone uses its own PostgreSQL database.

## Configure

```powershell
Copy-Item .env.example .env
notepad .env
```

`.env` is git-ignored. Keys the app reads (environment variables with the same names also work):

| Key | Required | Used for |
| --- | --- | --- |
| `DB_URL` | yes | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/clinic_booking` |
| `DB_USERNAME` | yes | Postgres user |
| `DB_PASSWORD` | yes | Postgres password |
| `SMTP_HOST` | for email | e.g. `smtp.gmail.com` |
| `SMTP_PORT` | for email | `587` (STARTTLS); Gmail falls back to `465` automatically |
| `SMTP_USERNAME` | for email | SMTP login |
| `SMTP_PASSWORD` | for email | SMTP password / app password |
| `MAIL_FROM` | for email | Sender address |
| `MAIL_FROM_NAME` | no | Sender display name |
| `DEMO_STAFF_CODES` | no | `true` gives clinics without a staff code the demo code `clinic<id>` (demo databases only, see [Staff side](#staff-side)) |

Without the SMTP keys the app still runs; it logs `[Email] WARNING` and skips sending.

## Getting API keys / credentials

### PostgreSQL database

1. Install PostgreSQL, either:
   - **winget:** `winget install --id PostgreSQL.PostgreSQL.17 -e` (the installer asks you to set a password for the `postgres` superuser; `psql` is in `C:\Program Files\PostgreSQL\17\bin`), or
   - **Docker:** `docker run -d --name clinic-db -p 5432:5432 -e POSTGRES_PASSWORD=postgres postgres:17`
2. Create a database and user (enter the `postgres` password when prompted):

   ```powershell
   psql -h localhost -U postgres -c "CREATE USER clinic_app WITH PASSWORD 'change-me';"
   psql -h localhost -U postgres -c "CREATE DATABASE clinic_booking OWNER clinic_app;"
   ```

   With Docker, prefix each command with `docker exec -it clinic-db` (e.g. `docker exec -it clinic-db psql -U postgres -c "..."`).
3. Put `DB_URL=jdbc:postgresql://localhost:5432/clinic_booking`, `DB_USERNAME=clinic_app` and `DB_PASSWORD=change-me` in `.env`.
4. Schema: nothing to run. The app creates every table and column itself on startup (`CREATE TABLE IF NOT EXISTS` / `ADD COLUMN IF NOT EXISTS` in `app.DB.initTables`). The SQL files in `docs/migrations/` are only for upgrading a database made by an older version of the app by hand; starting the new app does the same. It does not add any clinics or services, so load the sample data to get something to book:

   ```powershell
   psql -h localhost -U clinic_app -d clinic_booking -f docs/sample-data.sql
   ```

   With Docker: `Get-Content docs/sample-data.sql | docker exec -i clinic-db psql -U clinic_app -d clinic_booking`.
5. Optional demo patient: after the app has been started once (so all tables exist), load `docs/demo-data.sql` for a fictional patient with upcoming and past appointments, test results and prescriptions (dates are relative to today). Sign in as `alex.morgan@example.com` / `DemoPass2026!`.

   ```powershell
   psql -h localhost -U clinic_app -d clinic_booking -f docs/demo-data.sql
   ```

A hosted Postgres (Neon, Supabase, Render, etc.) also works: copy its JDBC URL into `DB_URL` and add `?sslmode=require`.

### SMTP (email confirmations and reminders)

Gmail with an app password:

1. Turn on 2-Step Verification for your Google account (https://myaccount.google.com/security).
2. Open https://myaccount.google.com/apppasswords, enter a name (e.g. "Clinic app") and click **Create**.
3. Copy the 16-character password into `SMTP_PASSWORD` (no spaces). Set `SMTP_HOST=smtp.gmail.com`, `SMTP_PORT=587`, and your Gmail address as `SMTP_USERNAME` and `MAIL_FROM`.

Alternatives:

- **Mailtrap** (test inbox, nothing is really delivered): sign up at https://mailtrap.io, open **Email Testing > Inboxes > SMTP Settings**, and copy the host (`sandbox.smtp.mailtrap.io`), port `587`, username and password.
- **Brevo** (free real sending): sign up at https://www.brevo.com, go to **SMTP & API > SMTP**, generate an SMTP key, and use host `smtp-relay.brevo.com`, port `587`, your Brevo login as the username and the SMTP key as the password. `MAIL_FROM` must be a sender verified in Brevo.

### Map and postcode lookup

No keys needed. The clinic map uses Leaflet with public OpenStreetMap tiles, and postcode search uses the free https://api.postcodes.io API. Both need internet access; without it the map shows a "Map unavailable" message and the clinic list still works.

## Run

```powershell
mvn clean javafx:run
```

Or in IntelliJ, run `app.Main`. If the database is unreachable the window still opens and shows a "Can't connect to the database" message.

## Test

```powershell
mvn test                 # unit tests: 598 tests on in-memory H2, no database or .env needed
mvn test -Psetup-check   # "check my setup": is the app hooked up to the database and SMTP in your .env?
```

Plain `mvn test` never touches your database: it refuses to connect to anything except in-memory H2 (`-Ddb.inMemoryOnly=true`) and email sending is disabled (`-Demail.disabled=true`). Tests tagged `db` are left out.

### Check my setup

After filling in `.env`, `mvn test -Psetup-check` runs `setup.SetupCheckTest` and then the other `db` tests (`UserDAOAccountTest` and the account tests in `AccountServiceTest`, which each get their own temporary database too) against the server in `.env`. The check reads your database and runs `DB.initTables` on it (as every app start does), but everything it writes goes into a temporary database `setup_check_<time>_<random>` on the same server that it creates, fills and drops again; it never deletes rows from your tables or touches the `AuditLog` guard. Temporary databases over an hour old (from an interrupted run) are dropped at the start. It is safe on a fresh, empty database and on one already in use. Each step prints `[setup-check] OK`, `NOTE` or `SKIP`; when a step fails, the steps that depend on it are skipped, so read the first failure.

| Step | What it proves | If it fails |
|---|---|---|
| 1. `.env` | `.env` is found in the project root (or the variables are set in the environment) and has `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`; `DB_URL` starts with `jdbc:postgresql://`. Optional keys you have not set are listed as a NOTE. | Copy `.env.example` to `.env` and fill it in (see [Configure](#configure)). Run Maven from the project root. |
| 2. Database reachable | The app can log in to the database in `DB_URL` and it is PostgreSQL. | The message says which: wrong login (`DB_USERNAME`/`DB_PASSWORD`), unknown database name, or the server cannot be reached (host, port, `?sslmode=require` for hosted databases, network). |
| 3a. Schema on an empty database | Creates the temporary database and checks that `DB.initTables` builds all 20 tables, the per-clinic `"StaffClinic".role` column and the unique email index from nothing. | Skipped (with a NOTE) if your login may not create databases (`CREATEDB`); 4b-4e are then skipped too rather than writing to your tables, and 3b still runs. A failure here is a bug in `DB.initTables`. |
| 3b. Schema on your database | `DB.initTables` (run twice, as on every app start) creates or upgrades your schema: all tables, the role column with its CHECK constraint, the `user_email_lower_key` unique index and the append-only `AuditLog` trigger. | The login needs rights to create and alter tables. A missing unique email index on a database that already has users means duplicates must be checked first: apply `docs/migrations/2026-11-23-unique-email.sql`. |
| 4a. Sample data | Clinics and their services can be read from your database (read only). | Skipped when there are no clinics yet (the booking screens would be empty): load `docs/sample-data.sql`. |
| 4b. Patient sign-up | In the temporary database (as are 4c-4e): Sign-up with a security question, sign-in, wrong password refused, and a second account with the same email in a different case refused by the database index itself. | See the assertion message; usually the schema (step 3). |
| 4c. Booking | On a temporary clinic open every day: the clinic and service are listed, free slots are generated, booking saves the `Timeslot`, `Appointment` and `BookingDetails` rows, the booking shows as upcoming and the full slot is no longer offered. | See the assertion message. |
| 4d. Reminders and cancel | Appointment and booking reminder rows are saved (and replaced, not duplicated); cancelling frees the slot. | See the assertion message. |
| 4e. Staff | A clinic admin is seeded, a clinician signs up with the clinic's staff code, waits as PENDING with no access, is approved by the admin and gets the CLINICIAN role at that clinic; `AuditLog` refuses deletes. | See the assertion message. |
| 5a. SMTP login | Connects to `SMTP_HOST:SMTP_PORT` and logs in with `SMTP_USERNAME`/`SMTP_PASSWORD`. **No email is sent.** Skipped when `SMTP_HOST` is empty (the app then runs but sends no email). | Rejected login: for Gmail use an App Password. Cannot connect: check host and port (587 STARTTLS, 465 SSL) and that your network allows outgoing SMTP. |
| 5b. Send a test email | Only when `SETUP_CHECK_EMAIL_TO` is set (environment or `.env`): sends one test booking confirmation there through the app's `EmailSender`. Otherwise skipped. | The `[Email]` lines above the failure say why. |
| 6. Cleanup | The temporary database is dropped with everything the round trips created, and your own `"User"` and `"AuditLog"` row counts are the same as after step 3b. | A changed count is only a NOTE: the app may be running against the same database. If the drop fails, the next run drops it once it is an hour old. |

To send yourself the test email as well (PowerShell): `$env:SETUP_CHECK_EMAIL_TO="you@example.com"; mvn test -Psetup-check`.

`mvn test -Pdb-tests` runs everything: the unit tests, the setup check and the other `db` tests, plus `SchemaIntrospectTest`, which only prints your live schema (`mvn test -Pdb-tests -Dtest=SchemaIntrospectTest`).

### Screenshots

The images above come from a screenshot harness (`src/test/java/dev`) that drives the real app through every screen. With the demo data loaded and a desktop session:

```powershell
mvn -Pscreenshots test-compile exec:exec                      # PNGs in target/screenshots
mvn -Pscreenshots test-compile exec:exec -Dshots.only=book    # just the booking flow
```

It books and moves appointments, so reload `docs/demo-data.sql` before running it again.

`mvn -Pstaff-screenshots test-compile exec:exec` does the same for the staff side (PNGs and a log in `target/staff-shots`). It needs a clinic admin made with `StaffSeed` and the environment variables listed in `dev.StaffHarness`; it creates its own test patient and clinician with `example.com` emails and deletes them at the end. `mvn -Pverify-screenshots test-compile exec:exec` (`dev.VerifyHarness`, same environment variables) checks the popup queue, a partly-failed multi-service booking, switching clinics with per-clinic roles and the result-release email, with PNGs in `target/verify-shots`.

`media/demo.gif` is built from the patient and staff screenshots with `python media/make_demo_gif.py` (needs Pillow); the steps are at the top of that script.

Both staff harnesses use the app normally, so they add rows to `AuditLog`, which is append-only by design.

### Known limits

- Removed or rejected staff can't sign up again with the same email.
- New user ids are the highest id plus one, so a deleted account's id can be reused.
- A few database calls (selecting a clinic, confirming a booking, loading account details, saving booking-communication settings) still run on the UI thread and can pause the window briefly on a slow connection.
- Staff land on their lowest-numbered clinic after login, even if they are an admin somewhere else.

More setup notes and troubleshooting: [`docs/SETUP.md`](docs/SETUP.md).

## Docs

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md): packages, data flow and key classes
- [`docs/SETUP.md`](docs/SETUP.md): fresh install and common errors
- `src/*.puml`: class diagram, ERD and sequence diagrams (auth, booking, cancel/reschedule, reminders, tracking)
