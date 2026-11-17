# Sexual Health Clinic App

A JavaFX desktop app for booking and managing sexual health clinic appointments. Users can sign up, find clinics, book, cancel or reschedule appointments, track tests and prescriptions, and get email reminders.

> **Group project.** Built by a team for the University of Southampton *Software Design and Development Project* (COMP2300, 2025–26). This repo is a snapshot of the code, which I continued to develop after the module ended.

## Features

- **Accounts:** sign up, log in, password reset, session handling, plus a terms and privacy flow.
- **Clinic search:** filter clinics and see them on a static map (Google Maps Static API). Users can set a default clinic.
- **Booking:** choose a service and timeslot, then cancel or reschedule. Confirmation emails are sent with Thymeleaf HTML templates.
- **Tracking:** appointment, test and prescription history.
- **Reminders:** configurable defaults, delivered by email.
- **Accessibility:** adjustable accessibility settings.

## Stack

Java 21, JavaFX (FXML, MaterialFX, ControlsFX), PostgreSQL, a DAO/repository data layer, JavaMail with Thymeleaf templates, and `dotenv-java` for configuration. Tests use JUnit 5 with H2. The build uses Maven.

## Run it

1. Copy `.env.example` to `.env` and fill in your Postgres URL and credentials, SMTP settings and a Google Maps key.
2. Build and run:

```bash
mvn clean javafx:run
mvn test
```

Detailed setup and troubleshooting: [`docs/SETUP.md`](docs/SETUP.md).

## Docs

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md): packages, data flow and key classes
- [`docs/SETUP.md`](docs/SETUP.md): fresh install and common errors
- [`docs/BASICS.md`](docs/BASICS.md): the team's Git workflow
- `src/*.puml`: class diagram, ERD and sequence diagrams (auth, booking, cancel/reschedule, reminders, tracking)
