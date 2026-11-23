package dev;

import app.DB;
import app.Data;
import dao.PrescriptionDAO;
import dao.ReminderDAO;
import dao.UserDAO;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.stage.Stage;
import model.TestResultStatus;
import service.PasswordHasher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Developer tool: clicks through the whole staff side against the database in .env, then checks the
 * patient side of what staff did (released result alert, "Missed" visit, stopped prescription's
 * reminders). Writes PNGs and a log to target/staff-shots. Not part of {@code mvn test}.
 *
 * <p>Needs a clinic admin made with {@code app.StaffSeed admin} and two passwords in the environment:
 * {@code STAFF_ADMIN_EMAIL}, {@code STAFF_ADMIN_PASSWORD} (the seeded one) and
 * {@code STAFF_ADMIN_NEW_PASSWORD} (chosen at the forced reset). {@code STAFF_CLINIC} is the admin's
 * clinic. The admin is put back to the seeded password with must_reset_password at the start, so the
 * script can be run again. It creates a clinician, a patient, two timeslots and their records with
 * example.com emails, and deletes them again at the end (audit log rows stay: append-only).
 * The clinic's staff code is rotated; the demo code is put back at the end when DEMO_STAFF_CODES is on.
 * Steps depend on each other, so run them all (no {@code -Dshots.only}).
 *
 * <p>Run: {@code mvn -o -Pstaff-screenshots test-compile exec:exec}.
 */
public class StaffHarness extends ScreenshotHarness {

    private final int clinic = Integer.parseInt(env("STAFF_CLINIC", "5"));
    private final String adminEmail = env("STAFF_ADMIN_EMAIL", "clinic.admin.demo@example.com");
    private final String adminPw = env("STAFF_ADMIN_PASSWORD", null);
    private final String adminNewPw = env("STAFF_ADMIN_NEW_PASSWORD", null);
    private final String run = String.valueOf(System.currentTimeMillis() % 1_000_000);
    private final String clinicianEmail = "clinician.qa." + run + "@example.com";
    private final String clinicianPw = "ClinicianQa2026" + run;
    private final String patientEmail = "patient.qa." + run + "@example.com";
    private final String patientLast = "Tester" + run;

    private int adminId;
    private int patientId;
    private final List<Integer> slotIds = new ArrayList<>();
    private int todayAppId;
    private int pastAppId;
    private String newCode;
    private String currentAdminPw;

    public static void main(String[] args) {
        out = Path.of(args.length > 0 ? args[0] : "target/staff-shots");
        only = args.length > 1 ? args[1] : "";
        System.setProperty("email.disabled", "true");
        launch(StaffHarness.class, args);
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v;
    }

    @Override
    List<Step> steps() {
        List<Step> list = new ArrayList<>();
        list.add(new Step("setup", this::setup));
        list.add(new Step("reset", this::adminReset));
        list.add(new Step("code", this::rotateCode));
        list.add(new Step("signup", this::clinicianSignUp));
        list.add(new Step("approve", this::approve));
        list.add(new Step("clinician", this::clinicianView));
        list.add(new Step("remove", this::removeClinician));
        list.add(new Step("today", this::today));
        list.add(new Step("patients", this::patients));
        list.add(new Step("test", this::testEditor));
        list.add(new Step("rx", this::prescription));
        list.add(new Step("patient", this::patientSide));
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
                    "UPDATE \"User\" SET password_hash = ?, must_reset_password = TRUE WHERE email = ? AND role = 'CLINIC_ADMIN' RETURNING user_id")) {
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
            patientId = intQuery(c, "SELECT user_id FROM \"User\" WHERE email = '" + patientEmail + "'");
            exec(c, "UPDATE \"User\" SET first_name = 'Pat', last_name = '" + patientLast + "' WHERE user_id = " + patientId);
            LocalDate today = LocalDate.now();
            int todaySlot = slot(c, today, "16:00", "16:30");
            int pastSlot = slot(c, today.minusDays(3), "10:00", "10:30");
            todayAppId = appointment(c, todaySlot, "Todays visit (QA)");
            pastAppId = appointment(c, pastSlot, "Earlier visit (QA)");
            exec(c, "INSERT INTO \"BookingDetails\" (app_id, first_name, last_name, dob, phone_number, contact_email, gender, sexuality, accessibility_needs) "
                    + "VALUES (" + todayAppId + ", 'Pat', '" + patientLast + "', DATE '1995-04-02', '07700 900123', '" + patientEmail
                    + "', 'Prefer not to say', 'Prefer not to say', 'Step-free access')");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        log("setup: admin " + adminId + ", patient " + patientId + " (" + patientEmail + "), appointments today "
                + todayAppId + " past " + pastAppId + ", slots " + slotIds);
    }

    private int slot(Connection c, LocalDate day, String start, String end) throws SQLException {
        int id = intQuery(c, "INSERT INTO \"Timeslot\" (date, start_time, end_time, service_id, clinic_id, capacity, booked_count) "
                + "VALUES (DATE '" + day + "', TIME '" + start + "', TIME '" + end + "', 3, " + clinic + ", 1, 1) RETURNING timeslot_id");
        slotIds.add(id);
        return id;
    }

    private int appointment(Connection c, int slotId, String notes) throws SQLException {
        return intQuery(c, "INSERT INTO \"Appointment\" (user_id, timeslot_id, notes, status) VALUES ("
                + patientId + ", " + slotId + ", '" + notes + "', 'CONFIRMED') RETURNING app_id");
    }

    private static int intQuery(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            if (!rs.next()) throw new IllegalStateException("No row: " + sql);
            return rs.getInt(1);
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

    private static void exec(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    // ---- Staff steps ---------------------------------------------------------------------------

    private void adminReset() {
        staffLogin(adminEmail, adminPw);
        shot("staff-01-forced-reset");
        dump("staff-01-forced-reset");
        PasswordField pw = Fx.require(stage, PasswordField.class, f -> "New password".equals(f.getPromptText()), "new pw");
        PasswordField pw2 = Fx.require(stage, PasswordField.class, f -> "Confirm new password".equals(f.getPromptText()), "confirm pw");
        // mismatch first
        Fx.type(pw, adminNewPw);
        Fx.type(pw2, adminNewPw + "x");
        click("Save new password");
        shot("staff-02-reset-mismatch");
        Fx.type(pw2, adminNewPw);
        Scene before = Fx.call(stage::getScene);
        click("Save new password");
        if (!Fx.waitFor(() -> stage.getScene() != before, 20000)) throw new IllegalStateException("reset did not finish");
        currentAdminPw = adminNewPw;
        Fx.sleep(1500);
        shot("staff-03-today-after-reset");
        dump("staff-03-today-after-reset");
        log("after reset: " + query("SELECT must_reset_password FROM \"User\" WHERE user_id = " + adminId));
    }

    private void rotateCode() {
        ensureAdmin();
        tab("Clinic");
        Fx.sleep(1500);
        shot("staff-04-clinic");
        dump("staff-04-clinic");
        Stage d = clickForDialog(b -> "Rotate staff code".equals(b.getText()) || "Create staff code".equals(b.getText()), "rotate");
        shotDialog("staff-05-rotate-confirm", d);
        dialogButton(d, "Rotate code");
        TextField code = Fx.require(stage, TextField.class, f -> "New staff code".equals(f.getAccessibleText()), "new code");
        newCode = Fx.call(code::getText);
        Fx.sleep(500);
        scrollToNode(code);
        shot("staff-06-new-code");
        dump("staff-06-new-code");
        log("rotated code length " + newCode.length() + "; demo flag: "
                + query("SELECT code_is_demo, rotated_by FROM \"ClinicStaffCode\" WHERE clinic_id = " + clinic));
    }

    private void clinicianSignUp() {
        logOut();
        // wrong code first
        signUpStaff(clinicianEmail, "not-the-code");
        Fx.waitFor(() -> Fx.find(stage, Label.class, l -> l.getStyleClass().contains("error") && l.getText() != null
                && !l.getText().isBlank()).isPresent(), 20000);
        shot("staff-07-signup-wrong-code");
        dump("staff-07-signup-wrong-code");
        Fx.type(Fx.require(stage, TextField.class, f -> "Clinic staff code".equals(f.getPromptText()), "code"), newCode);
        Scene before = Fx.call(stage::getScene);
        Fx.run(() -> Fx.labeled(stage, Button.class, "Create account").fire());
        if (!Fx.waitFor(() -> stage.getScene() != before, 20000)) {
            dump("staff-08-signup-stuck");
            throw new IllegalStateException("staff sign-up did not finish");
        }
        Fx.settle();
        shot("staff-08-signup-done");
        dump("staff-08-signup-done");
        staffLogin(clinicianEmail, clinicianPw);
        shot("staff-09-clinician-pending");
        dump("staff-09-clinician-pending");
        click("Log out");
    }

    private void signUpStaff(String email, String code) {
        var draft = Data.getSignUpDraft();
        draft.termsReadToEnd = true;
        draft.privacyReadToEnd = true;
        draft.termsAccepted = true;
        draft.privacyAccepted = true;
        showLoginNow();
        click("Create an account");
        Fx.type(Fx.require(stage, TextField.class, f -> "Email".equals(f.getPromptText()), "email"), email);
        Fx.type(Fx.require(stage, PasswordField.class, f -> "Password".equals(f.getPromptText()), "pw"), clinicianPw);
        Fx.type(Fx.require(stage, PasswordField.class, f -> "Confirm password".equals(f.getPromptText()), "pw2"), clinicianPw);
        ComboBox<?> q = Fx.require(stage, ComboBox.class, c -> true, "question");
        Fx.run(() -> q.getSelectionModel().select(0));
        Fx.type(Fx.require(stage, TextField.class, f -> "Your answer".equals(f.getPromptText()), "answer"), "Southampton");
        Fx.run(() -> Fx.require(stage, javafx.scene.control.CheckBox.class, c -> "I'm clinic staff".equals(c.getText()), "staff").setSelected(true));
        Fx.settle();
        Fx.type(Fx.require(stage, TextField.class, f -> "Clinic staff code".equals(f.getPromptText()), "code"), code);
        Fx.run(() -> {
            for (var cb : Fx.all(stage.getScene().getRoot())) {
                if (cb instanceof javafx.scene.control.CheckBox c && "I agree to the".equals(c.getText())) {
                    c.setDisable(false);
                    c.setSelected(true);
                }
            }
        });
        shot("staff-07a-signup-filled");
        Fx.run(() -> Fx.labeled(stage, Button.class, "Create account").fire());
    }

    private void approve() {
        ensureAdmin();
        tab("Clinic");
        Fx.sleep(1500);
        shot("staff-10-pending-list");
        dump("staff-10-pending-list");
        Button approve = Fx.require(stage, Button.class, b -> "Approve".equals(b.getText()) && rowMentions(b, clinicianEmail), "approve");
        Fx.run(approve::fire);
        Fx.waitFor(() -> Fx.find(stage, Label.class, l -> l.getText() != null && l.getText().contains(clinicianEmail)
                && !rowHasButton(l, "Approve")).isPresent(), 15000);
        Fx.sleep(1000);
        shot("staff-11-approved");
        dump("staff-11-approved");
        log("after approve: " + query("SELECT sc.status, u.role FROM \"StaffClinic\" sc JOIN \"User\" u USING (user_id) WHERE u.email = '" + clinicianEmail + "'"));
    }

    private void clinicianView() {
        logOut();
        staffLogin(clinicianEmail, clinicianPw);
        Fx.sleep(1500);
        shot("staff-12-clinician-today");
        dump("staff-12-clinician-today");
        boolean clinicTab = Fx.find(stage, ToggleButton.class, t -> "Clinic".equals(t.getText())).isPresent();
        log("clinician has Clinic tab: " + clinicTab);
        logOut();
    }

    private void removeClinician() {
        ensureAdmin();
        tab("Clinic");
        Fx.sleep(1500);
        Button remove = Fx.require(stage, Button.class, b -> "Remove".equals(b.getText()) && rowMentions(b, clinicianEmail), "remove");
        scrollToNode(remove);
        Stage d = clickForDialog(b -> b == remove, "remove");
        shotDialog("staff-13-remove-confirm", d);
        dumpWindow("staff-13-remove-confirm", d);
        dialogButton(d, "Remove");
        Fx.sleep(2000);
        shot("staff-14-removed");
        dump("staff-14-removed");
        log("after remove: " + query("SELECT sc.status FROM \"StaffClinic\" sc JOIN \"User\" u USING (user_id) WHERE u.email = '" + clinicianEmail + "'"));
        // the removed clinician can't get in any more
        logOut();
        staffLogin(clinicianEmail, clinicianPw);
        shot("staff-15-removed-clinician-login");
        dump("staff-15-removed-clinician-login");
        Fx.find(stage, Button.class, b -> "Log out".equals(b.getText())).ifPresent(b -> Fx.run(b::fire));
        Fx.settle();
    }

    private void today() {
        ensureAdmin();
        tab("Today");
        Fx.sleep(1500);
        shot("staff-16-today");
        dump("staff-16-today");
        clickRow("Pat " + patientLast);
        Fx.sleep(1500);
        shot("staff-17-appointment");
        dump("staff-17-appointment");
        scrollTo(0.5);
        shot("staff-18-appointment-mid");
        click("Mark attended");
        Fx.sleep(1500);
        TextArea notes = Fx.require(stage, TextArea.class, t -> "Staff notes".equals(t.getAccessibleText()), "notes");
        Fx.type(notes, "Came in on time. QA note.");
        click("Save notes");
        Fx.sleep(1500);
        scrollTo(1);
        shot("staff-19-appointment-attended");
        dump("staff-19-appointment-attended");
        log("today appt: " + query("SELECT status, attended_at IS NOT NULL AS attended, staff_notes FROM \"Appointment\" WHERE app_id = " + todayAppId));
        clickHeader("Back");
        Fx.sleep(1000);
        shot("staff-20-today-after");
    }

    private void patients() {
        ensureAdmin();
        tab("Patients");
        shot("staff-21-patients");
        TextField search = Fx.require(stage, TextField.class, f -> "Name or email".equals(f.getPromptText()), "search");
        Fx.type(search, "P");
        click("Search");
        Fx.sleep(1500);
        shot("staff-22-search-too-short");
        dump("staff-22-search-too-short");
        Fx.type(search, patientLast);
        click("Search");
        Fx.sleep(2000);
        shot("staff-23-search-results");
        clickRow("Pat " + patientLast);
        Fx.sleep(2000);
        shot("staff-24-record");
        dump("staff-24-record");
        // past visit: mark no-show
        Button past = Fx.require(stage, Button.class, b -> showing(b) && b.getAccessibleText() != null
                && b.getAccessibleText().startsWith(view.ui.Dates.dateTime(LocalDate.now().minusDays(3),
                java.time.LocalTime.of(10, 0), java.time.LocalTime.of(10, 30))), "past visit");
        Fx.run(past::fire);
        Fx.sleep(1500);
        shot("staff-25-past-appointment");
        click("No-show");
        Fx.sleep(1500);
        shot("staff-26-no-show");
        dump("staff-26-no-show");
        log("past appt: " + query("SELECT status FROM \"Appointment\" WHERE app_id = " + pastAppId));
        clickHeader("Back");
        Fx.sleep(1500);
        shot("staff-27-record-after-no-show");
    }

    private void testEditor() {
        ensureRecord();
        click("Add test result");
        Fx.sleep(800);
        shot("staff-28-test-new");
        dump("staff-28-test-new");
        Fx.type(Fx.require(stage, TextField.class, f -> "Test type".equals(f.getAccessibleText()), "type"), "STI screen (QA)");
        Fx.type(Fx.require(stage, TextField.class, f -> "Condition".equals(f.getAccessibleText()), "cond"), "Chlamydia");
        click("+ Add result");
        List<TextField> conds = Fx.call(() -> Fx.all(stage.getScene().getRoot()).stream()
                .filter(n -> n instanceof TextField t && "Condition".equals(t.getAccessibleText())).map(n -> (TextField) n).toList());
        Fx.type(conds.get(1), "Gonorrhoea");
        @SuppressWarnings("unchecked")
        List<ComboBox<TestResultStatus>> results = Fx.call(() -> Fx.all(stage.getScene().getRoot()).stream()
                .filter(n -> n instanceof ComboBox<?> c && "Result".equals(c.getAccessibleText()))
                .map(n -> (ComboBox<TestResultStatus>) n).toList());
        Fx.run(() -> results.get(0).setValue(TestResultStatus.POSITIVE));
        Fx.run(() -> results.get(1).setValue(TestResultStatus.NEGATIVE));
        Fx.type(Fx.require(stage, TextArea.class, t -> "Clinician notes".equals(t.getAccessibleText()), "notes"), "Treatable with a short course of antibiotics.");
        Fx.type(Fx.require(stage, TextArea.class, t -> "Treatment instructions".equals(t.getAccessibleText()), "tx"), "Take doxycycline as prescribed. Avoid sex until finished.");
        scrollTo(0.5);
        shot("staff-29-test-filled");
        // a draft is saved without asking (the patient sees nothing yet)
        click("Save draft");
        Fx.sleep(2000);
        scrollTo(1);
        shot("staff-30-draft-saved");
        dump("staff-30-draft-saved");
        log("draft: " + query("SELECT t.released_at IS NOT NULL AS released FROM \"Test\" t WHERE t.user_id = " + patientId));
        // release, but cancel at the positive-result confirmation
        Stage d = clickForDialog(b -> "Release to patient".equals(b.getText()), "release");
        shotDialog("staff-31-release-confirm", d);
        dumpWindow("staff-31-release-confirm", d);
        dialogButton(d, null);
        Fx.sleep(1500);
        shot("staff-32-release-cancelled");
        dump("staff-32-release-cancelled");
        log("after cancel: " + query("SELECT t.released_at IS NOT NULL AS released FROM \"Test\" t WHERE t.user_id = " + patientId));
        d = clickForDialog(b -> "Release to patient".equals(b.getText()), "release");
        dialogButton(d, "Release to patient");
        Fx.sleep(2500);
        scrollTo(0);
        shot("staff-33-released");
        scrollTo(1);
        shot("staff-34-released-bottom");
        dump("staff-34-released-bottom");
        log("tests: " + query("SELECT t.test_id, t.released_at IS NOT NULL AS released, r.condition, r.result FROM \"Test\" t JOIN \"TestResult\" r USING (test_id) WHERE t.user_id = " + patientId));
        clickHeader("Back");
        Fx.sleep(1500);
        shot("staff-35-record-with-test");
    }

    private void prescription() {
        ensureRecord();
        click("Prescribe");
        Fx.sleep(1500);
        shot("staff-36-rx-new");
        dump("staff-36-rx-new");
        click("Prescribe");  // no medicine chosen
        Fx.sleep(300);
        scrollTo(1);
        shot("staff-37-rx-no-medicine");
        @SuppressWarnings("unchecked")
        ComboBox<model.Medicine> med = Fx.require(stage, ComboBox.class, c -> "Medicine".equals(c.getAccessibleText()), "medicine");
        Fx.run(() -> med.getSelectionModel().select(0));
        Fx.type(Fx.require(stage, TextField.class, f -> "Dose".equals(f.getAccessibleText()), "dose"), "100mg");
        @SuppressWarnings("unchecked")
        javafx.scene.control.Spinner<Integer> perDay = Fx.require(stage, javafx.scene.control.Spinner.class,
                s -> "Doses per day".equals(s.getAccessibleText()), "per day");
        Fx.run(() -> perDay.getValueFactory().setValue(2));
        javafx.scene.control.DatePicker end = Fx.require(stage, javafx.scene.control.DatePicker.class,
                p -> "End date".equals(p.getAccessibleText()), "end");
        Fx.run(() -> end.setValue(LocalDate.now().plusDays(7)));
        Fx.type(Fx.require(stage, TextArea.class, t -> "Instructions".equals(t.getAccessibleText()), "instr"), "Take with food.");
        scrollTo(0.6);
        shot("staff-38-rx-filled");
        click("Prescribe");
        Fx.sleep(2000);
        shot("staff-39-rx-saved");
        dump("staff-39-rx-saved");
        int rxId;
        try (Connection c = DB.connect()) {
            rxId = intQuery(c, "SELECT prescription_id FROM \"Prescription\" WHERE user_id = " + patientId + " ORDER BY prescription_id DESC LIMIT 1");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        // what the patient's Reminders page saves when they turn dose reminders on
        new ReminderDAO().savePrescriptionReminder(patientId, rxId, true, 12, null, LocalDateTime.now());
        log("rx before stop: " + query("SELECT actively_taking, end_date FROM \"Prescription\" WHERE prescription_id = " + rxId)
                + " reminders: " + query("SELECT notify, interval_between_doses FROM \"PrescriptionReminder\" WHERE prescription_id = " + rxId)
                + " scheduler list has it: " + remindersInclude(rxId));
        // shorten: a day in the past is refused, then three days from now
        javafx.scene.control.DatePicker lastDay = Fx.require(stage, javafx.scene.control.DatePicker.class,
                p -> "New last day".equals(p.getAccessibleText()), "last day");
        Fx.run(() -> lastDay.setValue(LocalDate.now().minusDays(1)));
        click("Shorten course");
        Fx.sleep(1500);
        scrollTo(1);
        shot("staff-39a-rx-shorten-past");
        dump("staff-39a-rx-shorten-past");
        Fx.run(() -> lastDay.setValue(LocalDate.now().plusDays(3)));
        click("Shorten course");
        Fx.sleep(2000);
        shot("staff-39b-rx-shortened");
        dump("staff-39b-rx-shortened");
        String shortState;
        try {
            shortState = String.valueOf(new PrescriptionDAO().findReminderState(rxId)
                    .map(s -> s.wantsRemindersOn(LocalDate.now().plusDays(3)) + "/" + s.wantsRemindersOn(LocalDate.now().plusDays(4))));
        } catch (SQLException e) {
            shortState = "failed";
        }
        log("rx after shorten: " + query("SELECT actively_taking, end_date FROM \"Prescription\" WHERE prescription_id = " + rxId)
                + " wantsReminders(+3/+4 days): " + shortState);
        Stage d = clickForDialog(b -> "Stop early".equals(b.getText()), "stop");
        shotDialog("staff-40-stop-confirm", d);
        dumpWindow("staff-40-stop-confirm", d);
        dialogButton(d, "Stop early");
        Fx.sleep(2000);
        shot("staff-41-rx-stopped");
        dump("staff-41-rx-stopped");
        String state;
        try {
            state = String.valueOf(new PrescriptionDAO().findReminderState(rxId).map(s -> s.wantsRemindersOn(LocalDate.now())));
        } catch (SQLException e) {
            state = "failed";
        }
        log("rx after stop: " + query("SELECT actively_taking, end_date FROM \"Prescription\" WHERE prescription_id = " + rxId)
                + " reminders: " + query("SELECT notify, interval_between_doses FROM \"PrescriptionReminder\" WHERE prescription_id = " + rxId)
                + " scheduler list has it: " + remindersInclude(rxId) + " wantsRemindersOn(today): " + state);
        clickHeader("Back");
        Fx.sleep(1500);
        scrollTo(1);
        shot("staff-42-record-final");
    }

    private boolean remindersInclude(int rxId) {
        return new PrescriptionDAO().getPrescriptionsForRemindersByUserId(patientId).stream()
                .anyMatch(p -> p.getPrescriptionId() == rxId);
    }

    // ---- Patient side --------------------------------------------------------------------------

    private void patientSide() {
        logOut();
        login(patientEmail, DEMO_PASSWORD);
        Fx.sleep(3000);
        shot("patient-01-home");
        alertShots("patient-02-alert-login");
        tab("Tracking");
        Fx.sleep(3000);
        alertShots("patient-03-alert-tracking");
        closeDialogs();
        shot("patient-04-tracking-appointments");
        dump("patient-04-tracking-appointments");
        hubTab("Test Results");
        Fx.sleep(1000);
        shot("patient-05-tests");
        dump("patient-05-tests");
        hubTab("Prescriptions");
        Fx.sleep(1000);
        shot("patient-06-prescriptions");
        dump("patient-06-prescriptions");
        tab("Reminders");
        Fx.sleep(1500);
        shot("patient-07-reminders");
        dump("patient-07-reminders");
        log("patient last_results_seen_at: " + query("SELECT last_results_seen_at FROM \"User\" WHERE user_id = " + patientId));
    }

    private void alertShots(String name) {
        for (int i = 0; i < 4; i++) {
            Stage d = waitOther(2500);
            if (d == null) return;
            shotDialog(name + "-" + i, d);
            dumpWindow(name + "-" + i, d);
            Fx.run(d::close);
            Fx.sleep(300);
        }
    }

    // ---- Cleanup -------------------------------------------------------------------------------

    private void cleanup() {
        logOut();
        try {
            UserDAO users = new UserDAO();
            users.deleteUserById(patientId);
            try (Connection c = DB.connect()) {
                try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(
                        "SELECT user_id FROM \"User\" WHERE email = '" + clinicianEmail + "'")) {
                    if (rs.next()) users.deleteUserById(rs.getInt(1));
                }
                for (int id : slotIds) exec(c, "DELETE FROM \"Timeslot\" WHERE timeslot_id = " + id
                        + " AND NOT EXISTS (SELECT 1 FROM \"Appointment\" a WHERE a.timeslot_id = " + id + ")");
                // put the demo code (clinic<id>) back for demo databases, as the app would seed it
                if (DB.demoStaffCodesEnabled() && newCode != null) {
                    exec(c, "DELETE FROM \"ClinicStaffCode\" WHERE clinic_id = " + clinic);
                    app.StaffSeed.seedMissingStaffCodes(c);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        log("cleanup: deleted patient " + patientId + ", clinician " + clinicianEmail + ", slots " + slotIds);
    }

    // ---- Helpers -------------------------------------------------------------------------------

    void staffLogin(String email, String password) {
        showLoginNow();
        Scene loginScene = Fx.call(stage::getScene);
        Fx.type(Fx.require(stage, TextField.class, f -> "Email".equals(f.getPromptText()), "email"), email);
        Fx.type(Fx.require(stage, PasswordField.class, f -> true, "password"), password);
        Fx.run(() -> Fx.labeled(stage, Button.class, "Log in").fire());
        if (!Fx.waitFor(() -> stage.getScene() != loginScene, 20000)) {
            dump("login-stuck-" + email);
            throw new IllegalStateException("Login did not complete for " + email);
        }
        Fx.sleep(1200);
        Fx.settle();
    }

    void ensureAdmin() {
        if (Data.getCurrentUserId() == adminId && stage.getScene() == Data.getScene()) return;
        logOut();
        staffLogin(adminEmail, currentAdminPw);
    }

    void ensureRecord() {
        if (Fx.find(stage, Label.class, l -> showing(l) && ("Pat " + patientLast).equals(l.getText())
                && l.getStyleClass().contains("detail-title")).isPresent()) return;
        patients();
    }

    /** The real Log Out button on the Settings page when logged in, otherwise straight to login. */
    void logOut() {
        closeDialogs();
        if (Data.getCurrentUserId() > 0 && stage.getScene() == Data.getScene()) {
            clickHeader("Settings");
            Button b = Fx.require(stage, Button.class, x -> "Log Out".equals(x.getText()) && showing(x), "log out");
            Fx.later(b::fire);
            Stage d = waitOther(1500);
            if (d != null) dialogButton(d, "Log Out");
            Fx.waitFor(() -> Data.getCurrentUserId() <= 0, 10000);
            Fx.settle();
        }
        Data.clearCurrentUser();
        showLoginNow();
    }

    boolean rowMentions(Button b, String text) {
        return Fx.call(() -> {
            for (javafx.scene.Parent p = b.getParent(); p != null; p = p.getParent()) {
                if (p.getStyleClass().contains(view.ui.UI.CARD_INNER)) {
                    return Fx.all(p).stream().anyMatch(n -> n instanceof Label l && l.getText() != null && l.getText().contains(text));
                }
            }
            return false;
        });
    }

    boolean rowHasButton(Label l, String text) {
        for (javafx.scene.Parent p = l.getParent(); p != null; p = p.getParent()) {
            if (p.getStyleClass().contains(view.ui.UI.CARD_INNER)) {
                return Fx.all(p).stream().anyMatch(n -> n instanceof Button b && text.equals(b.getText()));
            }
        }
        return false;
    }

    void scrollToNode(javafx.scene.Node node) {
        Fx.run(() -> {
            for (javafx.scene.Parent p = node.getParent(); p != null; p = p.getParent()) {
                if (p instanceof javafx.scene.control.ScrollPane sp && sp.getContent() != null) {
                    double contentH = sp.getContent().getLayoutBounds().getHeight();
                    double viewH = sp.getViewportBounds().getHeight();
                    if (contentH <= viewH) return;
                    double y = sp.getContent().sceneToLocal(node.localToScene(0, 0)).getY();
                    sp.setVvalue(Math.max(0, Math.min(1, (y - 80) / (contentH - viewH))));
                    return;
                }
            }
        });
        Fx.settle();
    }

    void log(String line) {
        System.out.println("[staff] " + line);
        try {
            Files.createDirectories(out);
            Files.writeString(out.resolve("log.txt"), line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
