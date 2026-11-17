# Codebase structure and data flow


---

## 1. Project layout on disk

- `pom.xml` – Maven build configuration and dependencies. Declares `app.Main` as the JavaFX entry point and pulls in JavaFX, MaterialFX, PostgreSQL, Thymeleaf, JavaMail, etc.
- `src/main/java/` – All Java source code, organised into the packages described below.
- `src/main/resources/` – FXML files (`/fxml`), CSS (`/css/app.css`), and email templates (`/email-templates`) loaded via the classpath.
- `docs/` – Project documentation:
  - `SETUP.md` – how a fresh user clones, configures `.env`, runs the app, and what to do when things go wrong (DB, TLS/email, build issues).
  - `ARCHITECTURE.md` – this file: how packages, classes, and flows connect.
- `.env.example` – template for the `.env` configuration file used on each machine (database URL + credentials, SMTP host/credentials). Developers copy this to `.env` and fill in real values; `.env` is ignored by Git.

Key resource folders:

- `src/main/resources/fxml/` – `login.fxml`, `signup.fxml`, `home.fxml`, each bound to a controller in `controller.*`.
- `src/main/resources/email-templates/` – `layout.html` (wrapper) and one content template per email type (`booking-confirmation`, `appointment-reminder`, etc.).
- `src/main/resources/css/` – `app.css`, which is applied both to FXML scenes and Java-built views.

---

## 2. Entry point and startup

```
app.Main (main class in pom.xml)
  └── start(Stage)
        ├── app.DB.connect() + DB.initTables(c)   // ensure DB tables exist
        └── load /fxml/login.fxml → LoginController
```

- **app.Main** is the JavaFX Application entry point. It ensures the database has tables, then shows the login screen (FXML).
- **app.DB** is the single place that provides database connections. All DAOs use `app.DB.connect()`.
- **SceneManager** holds the Stage; login and signup controllers use it when switching scenes.

---

## 3. Package layout (what lives where)

| Package | Role | Key types |
|--------|------|-----------|
| **app** | Entry point, DB access, global state | `Main`, `DB`, `Data`, `SceneManager` |
| **controller** | FXML controllers and MVC controllers | `LoginController`, `SignUpController`, `MainController`, `BookingController`, `ViewAppointmentController`, `TrackingController`, `ReminderController` |
| **dao** | Data access (one class per table/area) | `*DAO`, `*Repository` (interfaces), `SessionDAO`, `TimeslotDAO` |
| **database** | DB integration tests only (tagged `db`) | — |
| **model** | Domain data (records/POJOs) | `User`, `Session`, `Book`, `Clinic`, `AppointmentDetails`, `Test`, etc. |
| **service** | Business logic, auth, email, scheduling | `AuthService`, `BookingService`, `TrackingService`, `NotificationScheduler`, `service.email.*` |
| **view** | All in-app UI (no FXML) | `Navigator`, `MainView`, `Home`, booking views, `view.reminders.*`, `view.notification.*`, `view.ui.UI` |

- **FXML** (login, signup, home) live under `src/main/resources/fxml/` and are loaded by controllers.
- **In-app screens** (Booking, Tracking, Reminders) are built in Java via `view.*` and `view.ui.UI`, not FXML.

---

## 4. Login and transition to main app

```
LoginController (FXML)
  ├── AuthService.signIn(email, password)     // validates credentials
  ├── UserDAO.getUserIdByEmail(email), getDisplayNameByUserId(id)
  ├── Data.setCurrentUser(userId, email, name)
  └── createNavigator(stage, userId)
        ├── new MainView()
        ├── new Navigator(mainView)           // Navigator holds mainView.getMainScene() as “home”
        ├── new MainController(nav, mainView, userId)
        └── Scene(nav.getRoot(), …) + app.css
```

- **AuthService** uses **UserDAO** (and optionally **UserRepository**) for password check and user lookup.
- **Data** holds the current user (id, email, name) and the current **Book** (booking form state). Set once at login; used everywhere that needs “current user” or “current booking”.
- **MainController** is created once after login. It wires the three dashboard buttons to:
  - **Booking** → `nav.goToPage(Home.getPage(nav), "Booking", false)`
  - **Tracking** → `new TrackingController(nav, new TrackingService(userID))`
  - **Reminders** → `new RemindersView(nav, userID).show()`
- **Navigator** is the single place that swaps the centre content and title; all in-app navigation goes through `nav.goToPage(...)` or `nav.goBack()` / `nav.goToHome()`.

---

## 5. Navigator and main scene

```
Navigator
  ├── root: BorderPane
  │     ├── top: header (Back, title Label, Home)
  │     └── center: bound to currentPage (Node)
  ├── homePage = MainView.getMainScene()   // Booking | Tracking | Reminders
  └── goToPage(node, title, isHome) / goBack() / goToHome()
```

- Every in-app screen is a **Node** (usually a VBox built with `view.ui.UI.page(...)` etc.). Controllers/views call `nav.goToPage(node, title, false)` to show it; Back and Home are handled by the Navigator header.
- **MainView** only provides the three buttons; it does not know about Home, Tracking, or Reminders content. Those are attached by **MainController** and the various view classes.

---

## 6. Booking flow (data and navigation)

Booking uses **Data.getBooking()** (a single `Book` instance) to accumulate clinic, service, timeslot, and personal info. No FXML; all screens are built in `view.*` and use **Navigator**.

| Screen | Who builds it | Reads | Writes | Next |
|--------|----------------|-------|--------|------|
| **Home** | `view.Home.getPage(nav)` | — | — | ViewBookings, ClinicSearch, Reschedule, Cancel |
| **View Bookings** | `view.ViewBookings.getPage(nav)` | `Data.getCurrentUserId()`, **AppointmentDAO** | — | List or details → Reschedule / Cancel |
| **Clinic Search** | `view.ClinicSearch.getPage(nav)` | **ClinicDAO**, **ServiceDAO** | `Data.getBooking().setClinic(c)` | ServiceSelection |
| **Service Selection** | `view.ServiceSelection.getPage(nav)` | `Data.getBooking().getClinic()` | `Data.getBooking().setService(s)` | PersonalInfo |
| **Personal Info** | `view.PersonalInfo.getPage(nav)` | — | `Data.getBooking()` (name, DOB, etc.) | DateTime |
| **Date/Time** | `view.DateTime.getPage(nav)` | `Data.getBooking()`, **AppointmentDAO** (slots) | `Data.getBooking().setTimeslotId/Date/Time` | Review |
| **Review** | `view.Review.getPage(nav)` | `Data.getBooking()`, `Data.getCurrentUserId()` | **AppointmentDAO.save()**, `Data.reset()` | goToHome |
| **Reschedule** | `view.Reschedule.getPage(nav)` / `getPageWithAppId(nav, id)` | `Data.getCurrentUserId()`, **AppointmentDAO** | **AppointmentDAO.rescheduleAppointment()** | — |
| **Cancel** | `view.Cancel.getPage(nav)` | `Data.getCurrentUserId()`, **AppointmentDAO** | **AppointmentDAO.cancelAppointment()** | — |

- **AppointmentDAO** is used for: upcoming/past lists, available timeslots, save booking, reschedule, cancel, and “is this appointment owned by this user?” (`isUpcomingAppointmentForUser`).
- **ClinicDAO** and **ServiceDAO** are used in ClinicSearch and ServiceSelection. **Data.getBooking()** is the only place booking form state lives until it’s saved in Review.

---

## 7. Tracking flow

```
MainController (Tracking button)
  └── new TrackingController(nav, new TrackingService(userID))
        ├── TrackingService(userId) uses TestDAO, PrescriptionDAO, AppointmentDAO (app.DB)
        ├── view = new TrackingView()   // menu: Appointments, Test Results, Prescriptions
        ├── nav.goToPage(view.getTrackingMainScene(), "Tracking", false)
        └── button actions → nav.goToPage(getTestResultsPage() | getPrescriptionsPage() | getAppointmentsPage(), …)
```

- **TrackingController** builds list and detail pages (using **TrackingView** for layout and **TrackingService** for data). It does not use **Data** for tracking; the `userId` is passed in when the controller is created.
- **TrackingService** delegates to **TestDAO**, **PrescriptionDAO**, and **AppointmentDAO** (past appointments only). All DAOs use **app.DB**.

---

## 8. Reminders flow

```
MainController (Reminders button)
  └── new RemindersView(nav, userID).show()
        ├── AppointmentDAO.getUpcomingAppointmentsForUser(userId)
        ├── PrescriptionDAO.getPrescriptionsForRemindersByUserId(userId)
        └── Buttons → AppointmentRemindersView | TreatmentRemindersView | BookingRemindersView
```

Each sub-view:

- **AppointmentRemindersView**: **AppointmentDAO** (upcoming), **ReminderDAO** (save reminder), **NotificationScheduler** (schedule email), **Data** (user email/name).
- **TreatmentRemindersView**: **PrescriptionDAO**, **ReminderDAO**, **NotificationScheduler**, **Data**.
- **BookingRemindersView**: **ReminderDAO**, **AppointmentDAO.getLastAppointmentDateByUserId** (for “days since last visit”), **NotificationScheduler**, **Data**.

Reminder settings are stored via **ReminderDAO**; emails are sent later by **service.NotificationScheduler** (and **service.email.***).

---

## 9. Database and DAOs

- **app.DB** is the only connection source used by the application code. It provides `DB.connect()` and `DB.initTables(Connection)`.
- Every DAO uses **app.DB.connect()** inside try-with-resources. There are no connection holders in controllers or views.

| DAO | Used by | Purpose |
|-----|--------|---------|
| **UserDAO** | AuthService, LoginController, SessionDAO | Login, signup, user id/name by email, findById |
| **SessionDAO** | (optional) | Save/load sessions (Session model) |
| **TimeslotDAO** | AppointmentDAO, BookingService | Available slots, increment/decrement booked_count, update date |
| **AppointmentDAO** | TrackingService, ViewAppointmentController, ReminderController, BookingService | Bookings, reschedule, cancel, upcoming/past, last appointment date |
| **ClinicDAO** | BookingService | List clinics |
| **ServiceDAO** | BookingService | Services by clinic |
| **TestDAO** | TrackingService | Test results for user |
| **PrescriptionDAO** | TrackingService, ReminderController | Prescriptions for user / for reminders |
| **ReminderDAO** | ReminderController | Save/load appointment, prescription, booking reminders |

---

## 10. Shared UI and styling

- **view.ui.UI** provides style-class constants and helpers (`UI.page()`, `UI.title()`, `UI.primaryButton()`, etc.) so all in-app screens use the same look. Styles are defined in **app.css** (`.app-*`).
- **view.notification.*** are popup notifications (e.g. “Reminder set”); they do not drive navigation. Email sending is done in **service.NotificationScheduler** and **service.email.***.

---

## 11. Summary diagram (text)

```
Main
  → app.DB (init tables)
  → login.fxml → LoginController
        → AuthService + UserDAO → Data.setCurrentUser
        → createNavigator → MainView, Navigator, MainController

MainController
  → Booking:  nav.goToPage(Home.getPage(nav))
  → Tracking: new TrackingController(nav, new TrackingService(userId))
  → Reminders: new RemindersView(nav, userId).show()

Booking (Home → ClinicSearch → ServiceSelection → PersonalInfo → DateTime → Review)
  → BookingController → BookingService → ClinicDAO, ServiceDAO, TimeslotDAO, AppointmentDAO
  → Data.getBooking() (read/write)

View Bookings / Reschedule / Cancel
  → ViewAppointmentController → AppointmentDAO

Tracking
  → TrackingController + TrackingView
  → TrackingService → TestDAO, PrescriptionDAO, AppointmentDAO (app.DB)

Reminders
  → RemindersView → ReminderController → AppointmentRemindersView | TreatmentRemindersView | BookingRemindersView
  → ReminderController uses ReminderDAO, AppointmentDAO, PrescriptionDAO, NotificationScheduler, Data (email/name)

All DAOs → app.DB.connect()
```

