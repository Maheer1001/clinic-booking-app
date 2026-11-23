package dev;

import app.DB;
import app.Data;
import dao.UserDAO;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import model.Book;
import model.Item;
import model.TestResultStatus;
import service.BookingService;
import service.PasswordHasher;
import service.ReminderAlert;
import service.ResultReleaseNotifier;
import service.StaffService;
import service.email.Email;
import service.email.EmailSender;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Developer tool: checks the popup queue, the usual-clinic question, a partly booked multi-service
 * booking, per-clinic staff roles and the result email's sender name in the real app against the
 * database in .env. PNGs and a log go to target/verify-shots. Not part of {@code mvn test}.
 *
 * <p>Needs what {@link StaffHarness} needs (STAFF_ADMIN_PASSWORD, STAFF_ADMIN_NEW_PASSWORD; the admin
 * is reset at the start and put back to the seeded password with must_reset_password at the end).
 * Creates two example.com patients, their tests and bookings, and a second (CLINICIAN) membership for
 * the admin at {@code STAFF_OTHER_CLINIC} (default 1); all are deleted at the end (audit rows stay).
 * Steps depend on each other, so run them all.
 *
 * <p>Run: {@code mvn -o -Pverify-screenshots test-compile exec:exec}.
 */
public class VerifyHarness extends StaffHarness {

    private final int clinic = Integer.parseInt(env("STAFF_CLINIC", "5"));
    private final int otherClinic = Integer.parseInt(env("STAFF_OTHER_CLINIC", "1"));
    private final String adminEmail = env("STAFF_ADMIN_EMAIL", "clinic.admin.demo@example.com");
    private final String adminPw = env("STAFF_ADMIN_PASSWORD", null);
    private final String adminNewPw = env("STAFF_ADMIN_NEW_PASSWORD", null);
    private final String run = String.valueOf(System.currentTimeMillis() % 1_000_000);
    private final String patientEmail = "verify.qa." + run + "@example.com";
    private final String patientLast = "Verify" + run;
    private final String otherEmail = "verify.other." + run + "@example.com";
    static final String LONG_TEST = "Extended sexual health screen incl. hepatitis B/C"; // 50 characters
    static final String SENDER = "Riverside Wellbeing Team";

    private int adminId;
    private int patientId;
    private int otherId;
    private boolean membershipAdded;
    private String currentAdminPw;

    public static void main(String[] args) {
        out = Path.of(args.length > 0 ? args[0] : "target/verify-shots");
        only = args.length > 1 ? args[1] : "";
        System.setProperty("email.disabled", "true");
        launch(VerifyHarness.class, args);
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v;
    }

    @Override
    List<Step> steps() {
        List<Step> list = new ArrayList<>();
        list.add(new Step("setup", this::setup));
        list.add(new Step("popups", this::popups));
        list.add(new Step("usual", this::usualClinic));
        list.add(new Step("partial", this::partialBooking));
        list.add(new Step("both", this::fullTwoServiceBooking));
        list.add(new Step("sender", this::senderName));
        list.add(new Step("roles", this::roles));
        list.add(new Step("release", this::release));
        list.add(new Step("cleanup", this::cleanup));
        return list;
    }

    // ---- Setup ---------------------------------------------------------------------------------

    private void setup() {
        if (adminPw == null || adminNewPw == null) {
            throw new IllegalStateException("Set STAFF_ADMIN_PASSWORD and STAFF_ADMIN_NEW_PASSWORD");
        }
        try (Connection c = DB.connect()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE \"User\" SET password_hash = ?, must_reset_password = TRUE WHERE email = ? RETURNING user_id")) {
                ps.setString(1, PasswordHasher.hash(adminPw));
                ps.setString(2, adminEmail);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalStateException("No clinic admin " + adminEmail);
                    adminId = rs.getInt(1);
                }
            }
            currentAdminPw = adminPw;
            UserDAO users = new UserDAO();
            users.insertUser(patientEmail, PasswordHasher.hash(DEMO_PASSWORD));
            users.insertUser(otherEmail, PasswordHasher.hash(DEMO_PASSWORD));
            patientId = intQuery(c, "SELECT user_id FROM \"User\" WHERE email = '" + patientEmail + "'");
            otherId = intQuery(c, "SELECT user_id FROM \"User\" WHERE email = '" + otherEmail + "'");
            // no security question (made like an account from before they existed), usual clinic set
            exec(c, "UPDATE \"User\" SET first_name = 'Val', last_name = '" + patientLast + "', phone_number = '07700 900456', "
                    + "security_question = NULL, security_answer_hash = NULL, default_clinic_id = " + clinic
                    + " WHERE user_id = " + patientId);
            exec(c, "UPDATE \"User\" SET first_name = 'Other', last_name = 'Patient' WHERE user_id = " + otherId);
            String[] types = {"Full STI screen", LONG_TEST, "HIV test"};
            for (int i = 0; i < types.length; i++) {
                int t = intQuery(c, "INSERT INTO \"Test\" (user_id, clinic_id, test_type, test_date, released_at) VALUES ("
                        + patientId + ", " + clinic + ", '" + types[i] + "', CURRENT_DATE - " + (i + 2)
                        + ", (NOW() AT TIME ZONE 'UTC') - INTERVAL '" + (i + 1) + " minutes') RETURNING test_id");
                exec(c, "INSERT INTO \"TestResult\" (test_id, condition, result) VALUES (" + t + ", 'Chlamydia', 'NEGATIVE')");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        log("setup: admin " + adminId + ", patient " + patientId + ", other patient " + otherId);
        log("new results waiting: " + query("SELECT count(*) FROM \"Test\" WHERE user_id = " + patientId));
    }

    // ---- 1. Popup queue ------------------------------------------------------------------------

    private void popups() {
        login(patientEmail, DEMO_PASSWORD);
        Stage prompt = waitOther(8000);
        if (prompt == null) throw new IllegalStateException("no security-question prompt");
        shotDialog("v1-01-security-prompt", prompt);
        dumpWindow("v1-01-security-prompt", prompt);
        log("prompt title: " + Fx.call(prompt::getTitle) + ", open popups: " + openTitles());

        // the Tracking tab checks for new results; its popup must wait for the prompt
        ToggleButton tracking = Fx.require(stage, ToggleButton.class, t -> "Tracking".equals(t.getText())
                && t.getStyleClass().contains(view.ui.UI.NAV_BTN), "tracking tab");
        Fx.run(tracking::fire);
        Fx.sleep(5000);
        log("while prompt open, after Tracking: open popups " + openTitles() + ", waiting in queue "
                + Fx.call(() -> view.notification.PopupQueue.shared().waitingCount()));
        shotDialog("v1-02-results-wait-behind-prompt", prompt);

        closeWithX(prompt);
        Stage results = waitOther(5000);
        if (results == null) throw new IllegalStateException("no results popup after the prompt closed");
        log("after closing the prompt with X: open popups " + openTitles());
        shotDialog("v1-03-results-combined", results);
        dumpWindow("v1-03-results-combined", results);
        Fx.save(Fx.snapshot(results.getScene()), out.resolve("v1-03b-results-popup-alone.png"));
        log("results popup size " + Fx.call(() -> results.getWidth() + "x" + results.getHeight()));

        // a reminder firing now waits its turn
        fireBookingReminder();
        Fx.sleep(2500);
        log("reminder fired while results open: open popups " + openTitles() + ", waiting "
                + Fx.call(() -> view.notification.PopupQueue.shared().waitingCount()));
        shotDialog("v1-04-reminder-waits", results);

        closeWithX(results);
        Fx.sleep(1500);
        Stage reminder = waitOther(5000);
        if (reminder == null) throw new IllegalStateException("reminder did not open after the results popup closed");
        log("after closing results with X: open popups " + openTitles());
        shotDialog("v1-05-reminder-after-results", reminder);
        closeWithX(reminder);
        Fx.sleep(800);
        log("after closing reminder: open popups " + openTitles() + ", queue showing "
                + Fx.call(() -> view.notification.PopupQueue.shared().isShowing()));
        log("results marked seen: " + query("SELECT last_results_seen_at IS NOT NULL AS seen FROM \"User\" WHERE user_id = " + patientId));
    }

    private void fireBookingReminder() {
        ReminderAlert alert = new ReminderAlert(ReminderAlert.Kind.BOOKING, patientEmail, "Val " + patientLast, null,
                null, null, null, "It has been at least 180 days since your last visit");
        try {
            var m = view.notification.ReminderPopups.class.getDeclaredMethod("showIfSignedIn", ReminderAlert.class,
                    java.util.function.Supplier.class, java.util.function.Consumer.class);
            m.setAccessible(true);
            java.util.function.Supplier<String> who = Data::getCurrentUserEmail;
            java.util.function.Consumer<Runnable> fx = Platform::runLater;
            m.invoke(null, alert, who, fx);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // ---- 2. Usual clinic -----------------------------------------------------------------------

    private void usualClinic() {
        closeDialogs();
        ensurePatient();
        tab("Booking");
        Heartbeat hb = Heartbeat.start();
        Button book = Fx.require(stage, Button.class, x -> x.getText() != null && x.getText().contains("Book") && showing(x), "book");
        Fx.later(book::fire);
        Stage d = waitOther(10000);
        long stall = hb.stop();
        if (d == null) throw new IllegalStateException("usual clinic question did not appear");
        String header = Fx.call(d::getTitle);
        boolean searchShowing = Fx.find(stage, Label.class, l -> showing(l) && "Find a clinic".equals(l.getText())).isPresent();
        log("usual clinic: dialog '" + header + "' appeared with the clinic search page behind: " + searchShowing
                + "; longest FX-thread stall from click to question: " + stall + " ms");
        shotDialog("v2-01-usual-clinic-question", d);
        dumpWindow("v2-01-usual-clinic-question", d);
        dialogButton(d, "Yes, continue");
        Fx.waitFor(() -> Fx.find(stage, Label.class, l -> showing(l) && "Choose a service".equals(l.getText())).isPresent(), 15000);
        Fx.sleep(800);
        shot("v2-02-services-at-usual-clinic");
    }

    // ---- 3. Partial multi-service booking ------------------------------------------------------

    private void partialBooking() {
        Fx.run(() -> {
            for (String name : List.of("STI Test", "Pregnancy Testing and Options Counselling")) {
                TitledPane pane = Fx.require(stage, TitledPane.class, t -> t.getGraphic() != null
                        && Fx.all((Parent) t.getGraphic()).stream().anyMatch(n -> n instanceof CheckBox cb && name.equals(cb.getText())
                        || n instanceof Label l && name.equals(l.getText())), "service " + name);
                for (Node n : Fx.all((Parent) pane.getGraphic())) {
                    if (n instanceof CheckBox cb) cb.setSelected(true);
                }
            }
        });
        Fx.settle();
        shot("v3-01-two-services");
        clickPrimary();
        Fx.sleep(1500);
        List<ComboBox<?>> combos = timeCombos();
        if (combos.size() != 2) throw new IllegalStateException("expected 2 time pickers, got " + combos.size());
        Fx.run(() -> {
            combos.get(0).getSelectionModel().select(0);
            combos.get(1).getSelectionModel().select(2);
        });
        Fx.settle();
        shot("v3-02-times-chosen");
        dump("v3-02-times-chosen");
        clickPrimary(); // Continue to details
        Fx.sleep(800);
        clickPrimary(); // details -> review
        Fx.sleep(800);
        shot("v3-03-review");

        // someone else takes the second service's time
        Book mine = Data.getBooking();
        Item taken = mine.getItems().get(1);
        LocalDate date = taken.getDate();
        Book theirs = new Book();
        theirs.setClinic(mine.getClinic());
        Item copy = new Item(taken.getService());
        copy.setTimeslotId(taken.getTimeslotId());
        copy.setDate(taken.getDate());
        copy.setTime(taken.getTime());
        theirs.setItems(new ArrayList<>(List.of(copy)));
        var r = new BookingService().placeBooking(theirs, otherId);
        log("other patient took " + taken.getService().getName() + " at " + taken.getDate() + " " + taken.getTime()
                + ": booked " + r.booked().size() + ", unavailable " + r.unavailable());

        Button confirm = Fx.require(stage, Button.class, b -> "Confirm booking".equals(b.getText()) && showing(b), "confirm");
        Fx.run(confirm::fire);
        if (!Fx.waitFor(() -> Fx.find(stage, Button.class, b -> showing(b) && "Choose a different time".equals(b.getText())
                && Fx.find(stage, Label.class, l -> showing(l) && "You're booked in".equals(l.getText())).isPresent()).isPresent(), 20000)) {
            dump("v3-04-stuck");
            throw new IllegalStateException("partial result page did not appear");
        }
        Fx.sleep(1500);
        log("popups after confirm: " + openTitles());
        closeDialogs();
        shot("v3-04-partly-booked");
        dump("v3-04-partly-booked");
        scrollTo(1);
        shot("v3-05-partly-booked-bottom");
        log("appointments after first confirm: " + appointments());

        click("Choose a different time");
        Fx.sleep(1500);
        shot("v3-06-time-picker-again");
        dump("v3-06-time-picker-again");
        DatePicker dp = Fx.require(stage, DatePicker.class, x -> showing(x), "date");
        log("time picker reopened on " + Fx.call(dp::getValue) + " (was " + date + "), pickers: " + timeCombos().size());
        List<ComboBox<?>> again = timeCombos();
        Fx.run(() -> again.get(0).getSelectionModel().select(3));
        Fx.settle();
        shot("v3-07-new-time");
        clickPrimary();
        Fx.sleep(800);
        shot("v3-08-details");
        clickPrimary();
        Fx.sleep(800);
        shot("v3-09-review-remaining");
        dump("v3-09-review-remaining");
        Button confirm2 = Fx.require(stage, Button.class, b -> "Confirm booking".equals(b.getText()) && showing(b), "confirm");
        Fx.run(confirm2::fire);
        Fx.waitFor(() -> Fx.find(stage, Label.class, l -> showing(l) && "You're booked in".equals(l.getText())).isPresent(), 20000);
        Fx.sleep(1500);
        closeDialogs();
        shot("v3-10-remaining-booked");
        dump("v3-10-remaining-booked");
        log("appointments after second confirm: " + appointments());
        clickAny("Done");
    }

    /** Both services booked: their confirmation popups take turns too. */
    private void fullTwoServiceBooking() {
        closeDialogs();
        ensurePatient();
        tab("Booking");
        Button book = Fx.require(stage, Button.class, x -> x.getText() != null && x.getText().contains("Book") && showing(x), "book");
        Heartbeat hb = Heartbeat.start();
        Fx.later(book::fire);
        Stage d = waitOther(10000);
        log("second Book: longest FX-thread stall from click to question: " + hb.stop() + " ms");
        if (d == null) throw new IllegalStateException("usual clinic question did not appear");
        dialogButton(d, "Yes, continue");
        Fx.waitFor(() -> Fx.find(stage, Label.class, l -> showing(l) && "Choose a service".equals(l.getText())).isPresent(), 15000);
        Fx.sleep(800);
        Fx.run(() -> {
            for (String name : List.of("STI Test", "PrEP Consultation")) {
                TitledPane pane = Fx.require(stage, TitledPane.class, t -> t.getGraphic() != null
                        && Fx.all((Parent) t.getGraphic()).stream().anyMatch(n -> n instanceof CheckBox cb && name.equals(cb.getText())
                        || n instanceof Label l && name.equals(l.getText())), "service " + name);
                for (Node n : Fx.all((Parent) pane.getGraphic())) {
                    if (n instanceof CheckBox cb) cb.setSelected(true);
                }
            }
        });
        clickPrimary();
        Fx.sleep(1500);
        List<ComboBox<?>> combos = timeCombos();
        Fx.run(() -> {
            combos.get(0).getSelectionModel().select(6);
            combos.get(1).getSelectionModel().select(9);
        });
        Fx.settle();
        clickPrimary();
        Fx.sleep(800);
        clickPrimary();
        Fx.sleep(800);
        Button confirm = Fx.require(stage, Button.class, b -> "Confirm booking".equals(b.getText()) && showing(b), "confirm");
        Fx.run(confirm::fire);
        Fx.waitFor(() -> Fx.find(stage, Label.class, l -> showing(l) && "You're booked in".equals(l.getText())).isPresent(), 20000);
        Fx.sleep(2000);
        log("two services booked: open popups " + openTitles() + ", waiting "
                + Fx.call(() -> view.notification.PopupQueue.shared().waitingCount()));
        Stage first = waitOther(3000);
        if (first != null) {
            shotDialog("v1-06-two-confirmations-first", first);
            closeWithX(first);
            Fx.sleep(800);
            log("after closing the first confirmation: open popups " + openTitles());
            Stage second = waitOther(3000);
            if (second != null) {
                shotDialog("v1-07-two-confirmations-second", second);
                closeWithX(second);
            }
        }
        closeDialogs();
        log("appointments after full booking: " + appointments());
        clickAny("Done");
    }

    private String appointments() {
        return query("SELECT a.app_id, s.name, t.date, t.start_time, a.status FROM \"Appointment\" a JOIN \"Timeslot\" t USING (timeslot_id) "
                + "JOIN \"Service\" s ON s.service_id = t.service_id WHERE a.user_id = " + patientId + " ORDER BY a.app_id");
    }

    List<ComboBox<?>> timeCombos() {
        return Fx.call(() -> Fx.all(stage.getScene().getRoot()).stream()
                .filter(n -> n instanceof ComboBox<?> c && showing(c) && "Choose a time".equals(c.getPromptText()))
                .<ComboBox<?>>map(n -> (ComboBox<?>) n).toList());
    }

    // ---- 5a. Patient sets a sender name ---------------------------------------------------------

    private void senderName() {
        ensurePatient();
        tab("Reminders");
        hubTab("Booking");
        Fx.sleep(1000);
        Parent card = Fx.call(() -> cardWith("Booking confirmations"));
        Fx.run(() -> {
            for (Node n : Fx.all(card)) {
                if (n instanceof TitledPane t && "Email".equals(t.getText())) t.setExpanded(true);
            }
        });
        Fx.settle();
        TextField from = Fx.call(() -> (TextField) Fx.all(card).stream().filter(n -> n instanceof TextField t
                && "Email sender name (optional)".equals(t.getPromptText())).findFirst().orElseThrow());
        Fx.type(from, SENDER);
        scrollToNode(from);
        Button save = Fx.call(() -> (Button) Fx.all(card).stream().filter(n -> n instanceof Button b && "Save".equals(b.getText()))
                .findFirst().orElseThrow());
        Fx.run(save::fire);
        Fx.sleep(1500);
        scrollToNode(from);
        shot("v5-01-sender-name-saved");
        log("saved prefs: " + query("SELECT booking_comm_prefs FROM \"User\" WHERE user_id = " + patientId));
    }

    Parent cardWith(String title) {
        for (Node n : Fx.all(stage.getScene().getRoot())) {
            if (n instanceof Label l && title.equals(l.getText()) && showing(l)) {
                for (Parent p = l.getParent(); p != null; p = p.getParent()) {
                    if (p.getStyleClass().contains(view.ui.UI.CARD)) return p;
                }
                return l.getParent();
            }
        }
        throw new IllegalStateException("no card " + title);
    }

    // ---- 4. Per-clinic roles --------------------------------------------------------------------

    private void roles() {
        logOut();
        // through the app first: a second staff sign-up with the same email and the other clinic's code
        var draft = Data.getSignUpDraft();
        draft.termsReadToEnd = true;
        draft.privacyReadToEnd = true;
        draft.termsAccepted = true;
        draft.privacyAccepted = true;
        showLoginNow();
        click("Create an account");
        Fx.type(Fx.require(stage, TextField.class, f -> "Email".equals(f.getPromptText()), "email"), adminEmail);
        Fx.type(Fx.require(stage, PasswordField.class, f -> "Password".equals(f.getPromptText()), "pw"), adminNewPw);
        Fx.type(Fx.require(stage, PasswordField.class, f -> "Confirm password".equals(f.getPromptText()), "pw2"), adminNewPw);
        ComboBox<?> q = Fx.require(stage, ComboBox.class, c -> true, "question");
        Fx.run(() -> q.getSelectionModel().select(0));
        Fx.type(Fx.require(stage, TextField.class, f -> "Your answer".equals(f.getPromptText()), "answer"), "Southampton");
        Fx.run(() -> Fx.require(stage, CheckBox.class, c -> "I'm clinic staff".equals(c.getText()), "staff").setSelected(true));
        Fx.settle();
        Fx.type(Fx.require(stage, TextField.class, f -> "Clinic staff code".equals(f.getPromptText()), "code"), "clinic" + otherClinic);
        Fx.run(() -> {
            for (Node n : Fx.all(stage.getScene().getRoot())) {
                if (n instanceof CheckBox c && "I agree to the".equals(c.getText())) {
                    c.setDisable(false);
                    c.setSelected(true);
                }
            }
        });
        Fx.run(() -> Fx.labeled(stage, Button.class, "Create account").fire());
        Fx.waitFor(() -> Fx.find(stage, Label.class, l -> l.getStyleClass().contains("error") && l.getText() != null
                && !l.getText().isBlank()).isPresent(), 20000);
        Fx.sleep(500);
        Fx.find(stage, Label.class, l -> l.getStyleClass().contains("error") && l.getText() != null
                && !l.getText().isBlank()).ifPresent(this::scrollToNode);
        shot("v4-01-second-signup-refused");
        dump("v4-01-second-signup-refused");
        log("memberships after refused sign-up: " + memberships());

        try (Connection c = DB.connect()) {
            exec(c, "INSERT INTO \"StaffClinic\" (user_id, clinic_id, status, role) VALUES (" + adminId + ", " + otherClinic
                    + ", 'ACTIVE', 'CLINICIAN')");
            membershipAdded = true;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        log("inserted membership directly: " + memberships());

        staffLogin(adminEmail, adminPw);
        PasswordField pw = Fx.require(stage, PasswordField.class, f -> "New password".equals(f.getPromptText()), "new pw");
        PasswordField pw2 = Fx.require(stage, PasswordField.class, f -> "Confirm new password".equals(f.getPromptText()), "confirm pw");
        Fx.type(pw, adminNewPw);
        Fx.type(pw2, adminNewPw);
        Scene before = Fx.call(stage::getScene);
        click("Save new password");
        if (!Fx.waitFor(() -> stage.getScene() != before, 20000)) throw new IllegalStateException("reset did not finish");
        currentAdminPw = adminNewPw;
        Fx.sleep(2000);
        Stage sq = waitOther(2500);
        if (sq != null) {
            shotDialog("v4-02-admin-security-prompt", sq);
            closeWithX(sq);
        }
        shot("v4-02-today");
        tab("Clinic");
        Fx.sleep(2500);
        log("after login the staff side opens at clinic " + Data.getCurrentClinicId());
        shot("v4-03a-clinic-tab-at-login");
        @SuppressWarnings("unchecked")
        ComboBox<Integer> box = Fx.require(stage, ComboBox.class, c -> "Clinic".equals(c.getAccessibleText()) && showing(c), "switcher");
        Fx.run(() -> box.setValue(clinic));
        Fx.sleep(2500);
        shot("v4-03-clinic-admin-tools");
        dump("v4-03-clinic-admin-tools");
        Fx.run(box::show);
        Fx.sleep(500);
        shot("v4-04-switcher-open");
        Fx.run(() -> { box.hide(); box.setValue(otherClinic); });
        Fx.sleep(2000);
        boolean note = Fx.find(stage, Label.class, l -> showing(l) && "For clinic admins".equals(l.getText())).isPresent();
        boolean tools = Fx.find(stage, Label.class, l -> showing(l) && "Waiting for approval".equals(l.getText())).isPresent();
        log("at clinic " + otherClinic + ": note shown " + note + ", admin tools shown " + tools
                + ", current clinic " + Data.getCurrentClinicId());
        shot("v4-05-clinician-clinic-note");
        dump("v4-05-clinician-clinic-note");
        tab("Today");
        Fx.sleep(1500);
        shot("v4-06-today-at-clinician-clinic");

        StaffService svc = new StaffService();
        log("admin-only at clinic " + otherClinic + ": pendingStaff " + svc.pendingStaff(adminId, otherClinic).status()
                + ", listStaff " + svc.listStaff(adminId, otherClinic).status()
                + ", staffCodeStatus " + svc.staffCodeStatus(adminId, otherClinic).status()
                + ", timeslots " + svc.timeslots(adminId, otherClinic, LocalDate.now()).status()
                + ", approveStaff " + svc.approveStaff(adminId, otherClinic, otherId).status());
        log("admin-only at clinic " + clinic + ": pendingStaff " + svc.pendingStaff(adminId, clinic).status()
                + ", listStaff " + svc.listStaff(adminId, clinic).status());
        log("clinician work at clinic " + otherClinic + ": dayList " + svc.dayList(adminId, otherClinic, LocalDate.now()).status());

        tab("Clinic");
        Fx.sleep(800);
        @SuppressWarnings("unchecked")
        ComboBox<Integer> box2 = Fx.require(stage, ComboBox.class, c -> "Clinic".equals(c.getAccessibleText()) && showing(c), "switcher");
        Fx.run(() -> box2.setValue(clinic));
        Fx.sleep(2500);
        boolean toolsBack = Fx.find(stage, Label.class, l -> showing(l) && "Waiting for approval".equals(l.getText())).isPresent();
        log("back at clinic " + clinic + ": admin tools shown " + toolsBack);
        shot("v4-07-back-to-admin-clinic");
    }

    private String memberships() {
        return query("SELECT clinic_id, status, role FROM \"StaffClinic\" WHERE user_id = " + adminId + " ORDER BY clinic_id");
    }

    // ---- 5b. Release with the sender name ------------------------------------------------------

    private void release() {
        ensureAdmin2();
        tab("Patients");
        TextField search = Fx.require(stage, TextField.class, f -> "Name or email".equals(f.getPromptText()), "search");
        Fx.type(search, patientLast);
        click("Search");
        Fx.sleep(2000);
        clickRow("Val " + patientLast);
        Fx.sleep(2000);
        click("Add test result");
        Fx.sleep(800);
        Fx.type(Fx.require(stage, TextField.class, f -> "Test type".equals(f.getAccessibleText()), "type"), "Syphilis test (verify)");
        Fx.type(Fx.require(stage, TextField.class, f -> "Condition".equals(f.getAccessibleText()), "cond"), "Syphilis");
        @SuppressWarnings("unchecked")
        ComboBox<TestResultStatus> res = Fx.require(stage, ComboBox.class, c -> "Result".equals(c.getAccessibleText()), "result");
        Fx.run(() -> res.setValue(TestResultStatus.NEGATIVE));
        click("Save draft");
        Fx.sleep(2000);
        Button rel = Fx.require(stage, Button.class, b -> "Release to patient".equals(b.getText()) && showing(b), "release");
        Fx.later(rel::fire);
        Stage d = waitOther(3000);
        if (d != null) dialogButton(d, "Release to patient");
        Fx.sleep(3000);
        scrollTo(1);
        shot("v5-02-released");
        dump("v5-02-released");
        int testId;
        LocalDate testDate;
        try (Connection c = DB.connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(
                "SELECT test_id, test_date, released_at FROM \"Test\" WHERE user_id = " + patientId + " ORDER BY test_id DESC LIMIT 1")) {
            rs.next();
            testId = rs.getInt(1);
            testDate = rs.getDate(2).toLocalDate();
            log("released test " + testId + " at " + rs.getString(3));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        // what the production notifier hands to the mail sender for this patient
        List<Email> sent = new ArrayList<>();
        EmailSender capture = new EmailSender() {
            @Override
            public boolean send(Email email) {
                sent.add(email);
                return true;
            }
        };
        boolean ok = ResultReleaseNotifier.email(new UserDAO(), capture).resultAvailable(patientId, testDate);
        for (Email e : sent) {
            String resolved;
            try {
                var m = EmailSender.class.getDeclaredMethod("resolveFromName", Email.class);
                m.setAccessible(true);
                resolved = (String) m.invoke(null, e);
            } catch (ReflectiveOperationException ex) {
                resolved = "?";
            }
            log("result email: ok=" + ok + ", to " + e.getRecipientEmail() + ", subject '" + e.getSubject()
                    + "', getFromName '" + e.getFromName() + "', From display name used by EmailSender '" + resolved
                    + "', discreet " + e.isDiscreet() + ", template " + e.getTemplateName());
        }
        if (sent.isEmpty()) log("result email: nothing sent (ok=" + ok + ")");

        // the patient sees it
        logOut();
        login(patientEmail, DEMO_PASSWORD);
        Stage p = waitOther(6000);
        if (p != null) closeWithX(p);
        tab("Tracking");
        Stage alert = waitOther(6000);
        if (alert != null) {
            shotDialog("v5-03-patient-result-popup", alert);
            dumpWindow("v5-03-patient-result-popup", alert);
            closeWithX(alert);
        }
    }

    // ---- Cleanup -------------------------------------------------------------------------------

    private void cleanup() {
        closeDialogs();
        logOut();
        try (Connection c = DB.connect()) {
            List<Integer> slots = new ArrayList<>();
            try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(
                    "SELECT DISTINCT timeslot_id FROM \"Appointment\" WHERE user_id IN (" + patientId + ", " + otherId + ")")) {
                while (rs.next()) slots.add(rs.getInt(1));
            }
            UserDAO users = new UserDAO();
            if (patientId > 0) users.deleteUserById(patientId);
            if (otherId > 0) users.deleteUserById(otherId);
            for (int id : slots) exec(c, "DELETE FROM \"Timeslot\" WHERE timeslot_id = " + id
                    + " AND NOT EXISTS (SELECT 1 FROM \"Appointment\" a WHERE a.timeslot_id = " + id + ")");
            if (membershipAdded) exec(c, "DELETE FROM \"StaffClinic\" WHERE user_id = " + adminId + " AND clinic_id = " + otherClinic);
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE \"User\" SET password_hash = ?, must_reset_password = TRUE, security_question = NULL, security_answer_hash = NULL WHERE user_id = ?")) {
                ps.setString(1, PasswordHasher.hash(adminPw));
                ps.setInt(2, adminId);
                ps.executeUpdate();
            }
            log("cleanup: slots " + slots + " (unused ones deleted)");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        log("left: users " + query("SELECT count(*) FROM \"User\" WHERE email IN ('" + patientEmail + "', '" + otherEmail + "')")
                + " memberships " + memberships());
    }

    // ---- Helpers -------------------------------------------------------------------------------

    void ensurePatient() {
        if (Data.getCurrentUserId() == patientId && stage.getScene() == Data.getScene()) return;
        logOut();
        login(patientEmail, DEMO_PASSWORD);
        Stage p = waitOther(5000);
        if (p != null) closeWithX(p);
    }

    void ensureAdmin2() {
        if (Data.getCurrentUserId() == adminId && stage.getScene() == Data.getScene()) return;
        logOut();
        staffLogin(adminEmail, currentAdminPw);
        Stage p = waitOther(2500);
        if (p != null) closeWithX(p);
    }

    /** The window's X: a close request, which (unless consumed) hides the window, as the OS button does. */
    void closeWithX(Stage w) {
        Fx.run(() -> w.fireEvent(new WindowEvent(w, WindowEvent.WINDOW_CLOSE_REQUEST)));
        Fx.sleep(400);
        Fx.settle();
    }

    String openTitles() {
        return Fx.call(() -> {
            List<String> t = new ArrayList<>();
            for (Window w : Window.getWindows()) {
                if (w instanceof Stage s && s != stage && s.isShowing()) t.add("'" + s.getTitle() + "'");
            }
            return t.toString();
        });
    }

    private static int intQuery(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            if (!rs.next()) throw new IllegalStateException("No row: " + sql);
            return rs.getInt(1);
        }
    }

    private static void exec(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private static String query(String sql) {
        try (Connection c = DB.connect(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            StringBuilder sb = new StringBuilder();
            int n = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                for (int i = 1; i <= n; i++) sb.append(rs.getMetaData().getColumnName(i)).append('=').append(rs.getString(i)).append(' ');
                sb.append("; ");
            }
            return sb.toString();
        } catch (SQLException e) {
            return "query failed: " + e.getSQLState();
        }
    }

    /** Measures the longest time the JavaFX thread took to run a posted task. */
    static final class Heartbeat {
        private volatile boolean running = true;
        private volatile long max;
        private Thread thread;

        static Heartbeat start() {
            Heartbeat h = new Heartbeat();
            h.thread = new Thread(() -> {
                while (h.running) {
                    long t0 = System.nanoTime();
                    CountDownLatch l = new CountDownLatch(1);
                    Platform.runLater(l::countDown);
                    try {
                        l.await(30, TimeUnit.SECONDS);
                        Thread.sleep(15);
                    } catch (InterruptedException e) {
                        return;
                    }
                    long ms = (System.nanoTime() - t0) / 1_000_000 - 15;
                    if (ms > h.max) h.max = ms;
                }
            }, "fx-heartbeat");
            h.thread.setDaemon(true);
            h.thread.start();
            return h;
        }

        long stop() {
            running = false;
            return max;
        }
    }
}
