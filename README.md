# Sexual Health Clinic App

A JavaFX desktop app for booking and managing sexual health clinic appointments. Users can sign up, find clinics, book, cancel or reschedule appointments, track tests and prescriptions, and get email reminders.

> **Group project.** Built by a team for the University of Southampton *Software Design and Development Project* (COMP2300, 2025–26). This repo is a snapshot of the code, which I continued to develop after the module ended.

## Features

- **Accounts:** sign up, log in, password reset, session handling, plus a terms and privacy flow.
- **Clinic search:** search by postcode to sort clinics by distance and see them on an interactive OpenStreetMap map (Leaflet, no API key needed), zoomed to you and your nearest clinics. Users can set a default clinic.
- **Booking:** choose a service and timeslot, then cancel or reschedule. Confirmation emails are sent with Thymeleaf HTML templates.
- **Timeslots from opening hours:** available slots are generated from each clinic's opening hours and the service's length. Only booked slots are stored, and a slot drops out once its bookings reach capacity (reserved with a single conditional update, so it can't be overbooked).
- **Tracking:** appointment, test and prescription history.
- **Reminders:** configurable defaults, delivered by email.
- **Accessibility:** adjustable accessibility settings.

## Stack

Java 21, JavaFX (FXML, MaterialFX, ControlsFX, WebView + Leaflet), PostgreSQL, a DAO/repository data layer, JavaMail with Thymeleaf templates, and `dotenv-java` for configuration. Tests use JUnit 5 with H2. The build uses Maven.

## Prerequisites

- **JDK 21** or newer (`java -version`). Install with `winget install --id EclipseAdoptium.Temurin.21.JDK -e`.
- **Maven 3.9+** (`mvn -v`). Install with `winget install --id Apache.Maven -e`, or use the one bundled with IntelliJ.
- **PostgreSQL** 14 or newer, local or hosted (only needed to run the app and the integration tests, not for `mvn test`).
- Optional: an SMTP account for sending emails.

## Setup

```powershell
git clone https://github.com/Maheer1001/clinic-booking-app.git
cd clinic-booking-app
mvn -DskipTests package      # downloads dependencies and compiles
```

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
4. Schema: there is no separate migration file. The app creates every table itself on startup (`CREATE TABLE IF NOT EXISTS` in `app.DB.initTables`). It does not add any clinics or services, so load the sample data to get something to book:

   ```powershell
   psql -h localhost -U clinic_app -d clinic_booking -f docs/sample-data.sql
   ```

   With Docker: `Get-Content docs/sample-data.sql | docker exec -i clinic-db psql -U clinic_app -d clinic_booking`.

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

No keys needed. The clinic map uses Leaflet with public OpenStreetMap tiles, and postcode search uses the free https://api.postcodes.io API. Both need internet access.

## Run

```powershell
mvn clean javafx:run
```

Or in IntelliJ, run `app.Main`. If the database is unreachable the window still opens and shows a "Database unavailable" warning.

## Test

```powershell
mvn test                 # 289 unit tests, no database needed (tests tagged "db" are excluded)
mvn test -Pdb-tests      # also runs the 9 integration tests against the Postgres in .env
```

Email sending is disabled during tests (`-Demail.disabled=true` is set by the build).

More setup notes and troubleshooting: [`docs/SETUP.md`](docs/SETUP.md).

## Docs

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md): packages, data flow and key classes
- [`docs/SETUP.md`](docs/SETUP.md): fresh install and common errors
- `src/*.puml`: class diagram, ERD and sequence diagrams (auth, booking, cancel/reschedule, reminders, tracking)
