package dev;

import app.Data;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import view.ui.UI;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Developer tool: drives the real app through every screen with the demo data loaded and saves a PNG of
 * each one (used for UI review and the README media). Not part of the app or of {@code mvn test}.
 *
 * <p>Needs the database in .env with docs/sample-data.sql and docs/demo-data.sql loaded, a desktop
 * session, and internet access for the map tiles. Run it with
 * {@code mvn -Pscreenshots test-compile exec:exec} (PNGs go to target/screenshots; add
 * {@code -Dshots.only=book} to run one part). Reload docs/demo-data.sql first: the script books and moves appointments.
 */
public class ScreenshotHarness extends Application {

    static final double WIDTH = 420;
    static final double HEIGHT = 720;

    static final String DEMO_EMAIL = "alex.morgan@example.com";
    static final String DEMO_PASSWORD = "DemoPass2026!";
    static final String DEMO_POSTCODE = "SO17 1BJ";

    static Path out = Path.of("target", "screenshots");
    static String only = "";
    Stage stage;

    public static void main(String[] args) {
        if (args.length > 0) out = Path.of(args[0]);
        if (args.length > 1) only = args[1];
        System.setProperty("email.disabled", "true");
        launch(ScreenshotHarness.class, args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        for (String f : new String[]{"Light", "Regular", "Medium", "SemiBold", "Bold", "ExtraBold"}) {
            try (var in = getClass().getResourceAsStream("/fonts/Nunito-" + f + ".ttf")) {
                Font.loadFont(in, 14);
            }
        }
        this.stage = stage;
        stage.setTitle("Health Tracking and Appointments");
        stage.getIcons().add(new Image("/icons/safe.png"));
        stage.setResizable(false);
        showLogin();
        stage.show();
        Thread script = new Thread(this::script, "screenshot-script");
        script.setDaemon(true);
        script.start();
    }

    void showLogin() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/login.fxml"));
        Scene scene = new Scene(loader.load(), WIDTH, HEIGHT);
        scene.getStylesheets().add(UI.STYLE);
        stage.setScene(scene);
    }

    // ---- Script --------------------------------------------------------------------------------

    private final List<String> failures = new ArrayList<>();

    private void script() {
        try {
            Fx.sleep(800);
            Fx.settle();
            for (Step s : steps()) {
                if (!only.isEmpty() && !s.name.matches(only)) continue;
                try {
                    s.body.run();
                } catch (Throwable t) {
                    failures.add(s.name + ": " + t);
                    t.printStackTrace();
                    closeDialogs();
                }
            }
        } finally {
            System.out.println("[harness] done, failures: " + failures.size());
            failures.forEach(f -> System.out.println("  " + f));
            Platform.runLater(() -> System.exit(failures.isEmpty() ? 0 : 1));
        }
    }

    record Step(String name, Runnable body) {}

    List<Step> steps() {
        List<Step> list = new ArrayList<>();
        list.add(new Step("auth", this::authScreens));
        list.add(new Step("dbwarning", this::dbWarning));
        list.add(new Step("home", this::home));
        list.add(new Step("book", this::booking));
        list.add(new Step("tracking", this::tracking));
        list.add(new Step("reminders", this::reminders));
        list.add(new Step("settings", this::settings));
        list.add(new Step("a11y", this::accessibility));
        list.add(new Step("empty", this::emptyAccount));
        return list;
    }

    // ---- Scenes --------------------------------------------------------------------------------

    private void authScreens() {
        showLoginNow();
        shot("auth-01-login");
        Fx.type(Fx.require(stage, TextField.class, f -> "Email".equals(f.getPromptText()), "email"), DEMO_EMAIL);
        Fx.type(Fx.require(stage, PasswordField.class, f -> true, "password"), DEMO_PASSWORD);
        shot("auth-00-login-filled");
        Fx.type(Fx.require(stage, PasswordField.class, f -> true, "password"), "wrong-password1");
        Fx.run(() -> Fx.labeled(stage, Button.class, "Log in").fire());
        Fx.waitFor(() -> Fx.find(stage, Label.class, l -> l.getStyleClass().contains("error") && !l.getText().isEmpty()).isPresent(), 15000);
        shot("auth-02-login-error");
        dump("auth-02-login-error");

        click("Create an account");
        shot("auth-03-signup");
        dump("auth-03-signup");
        Fx.type(Fx.require(stage, TextField.class, f -> "Email".equals(f.getPromptText()), "email"), "sam@example");
        Fx.type(Fx.require(stage, PasswordField.class, f -> "Password".equals(f.getPromptText()), "pw"), "short");
        Fx.type(Fx.require(stage, PasswordField.class, f -> "Confirm password".equals(f.getPromptText()), "pw2"), "shorter");
        Fx.run(() -> Fx.require(stage, PasswordField.class, f -> "Password".equals(f.getPromptText()), "pw").requestFocus());
        shot("auth-04-signup-errors");
        dump("auth-04-signup-errors");

        Fx.run(() -> Fx.labeled(stage, Hyperlink.class, "Terms of Service").fire());
        Fx.settle();
        shot("auth-05-terms");
        dump("auth-05-terms");

        showLoginNow();
        click("Forgot your password?");
        shot("auth-06-password-reset");
        dump("auth-06-password-reset");
    }

    private void dbWarning() {
        showLoginNow();
        Fx.later(() -> {
            try {
                var m = app.Main.class.getDeclaredMethod("showDatabaseWarning", String.class);
                m.setAccessible(true);
                m.invoke(null, "Could not reach the database server. Check DB_URL in .env, your network connection, "
                        + "and that the server accepts connections on its port. (Connection to localhost:5432 refused.)");
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
        Stage d = Fx.awaitOtherStage(stage);
        shotDialog("auth-07-db-warning", d);
        closeDialogs();
    }

    private void home() {
        login(DEMO_EMAIL, DEMO_PASSWORD);
        shot("home-01-upcoming");
        Button card = Fx.require(stage, Button.class, b -> b.getStyleClass().contains("appointment-card"), "appointment card");
        Fx.run(card::fire);
        Fx.settle();
        shot("home-02-details");
        dump("home-02-details");

        Stage d = clickForDialog(b -> b.getText() != null && b.getText().startsWith("Cancel"), "cancel");
        shotDialog("home-03-cancel-confirm", d);
        dialogButton(d, "Keep it");

        click("Reschedule");
        shot("home-04-reschedule");
        dump("home-04-reschedule");
        ComboBox<?> times = timeCombo();
        Fx.run(times::show);
        Fx.settle();
        shot("home-05-reschedule-times");
        Fx.run(() -> { times.getSelectionModel().select(4); times.hide(); });
        Fx.settle();
        shot("home-06-reschedule-chosen");
        Button go = Fx.require(stage, Button.class, b -> b.getStyleClass().contains(UI.BTN) && b.getText() != null
                && (b.getText().startsWith("Move") || b.getText().equals("Reschedule")) && showing(b), "reschedule button");
        Fx.run(go::fire);
        Fx.settle();
        Stage note = waitOther(3000);
        if (note != null) {
            shotDialog("home-07-reschedule-notification", note);
            closeDialogs();
        }
        shot("home-08-after-reschedule");
    }

    private void booking() {
        ensureLoggedIn(DEMO_EMAIL);
        tab("Booking");
        clickBook();
        waitForMap();
        shot("book-01-clinic-search");
        dump("book-01-clinic-search");

        TextField postcode = Fx.require(stage, TextField.class, f -> f.getPromptText() != null
                && f.getPromptText().toLowerCase().contains("postcode"), "postcode");
        Fx.type(postcode, "ZZ9 9ZZ");
        click("Search");
        Fx.sleep(2000);
        shot("book-02-invalid-postcode");

        Fx.type(postcode, DEMO_POSTCODE);
        click("Search");
        Fx.sleep(5000);
        shot("book-03-search-results");
        dump("book-03-search-results");

        openFirstMapPopup();
        Fx.sleep(1500);
        shot("book-04-map-popup");
        Fx.run(() -> mapScript("map.closePopup();"));

        Fx.run(() -> {
            ListView<?> lv = Fx.require(stage, ListView.class, l -> showing(l), "clinic list");
            lv.getSelectionModel().select(0);
            for (Node n : Fx.all(lv)) {
                if (n instanceof TitledPane t) {
                    t.setExpanded(true);
                    break;
                }
            }
        });
        Fx.settle();
        shot("book-05-clinic-selected");

        Stage d = clickForDialog(b -> b.getStyleClass().contains(UI.BTN) && isContinueToServices(b), "select clinic");
        shotDialog("book-06-save-default", d);
        dumpWindow("book-06-save-default", d);
        dialogButton(d, "Save");
        Fx.settle();
        shot("book-07-services");
        dump("book-07-services");

        Fx.run(() -> {
            TitledPane pane = Fx.require(stage, TitledPane.class, t -> t.getGraphic() instanceof Parent g
                    && Fx.all(g).stream().anyMatch(n -> n instanceof Label l && "STI test".equals(l.getText())),
                    "sti pane");
            pane.setExpanded(true);
            Fx.all((Parent) pane.getGraphic()).stream().filter(n -> n instanceof CheckBox).map(n -> (CheckBox) n)
                    .findFirst().orElseThrow(() -> new IllegalStateException("sti checkbox")).setSelected(true);
        });
        Fx.settle();
        shot("book-08-service-chosen");

        Button confirmServices = Fx.require(stage, Button.class, b -> b.getStyleClass().contains(UI.BTN)
                && !b.isDisabled() && showing(b), "confirm services");
        Fx.run(confirmServices::fire);
        Fx.settle();
        shot("book-09-timeslots");
        dump("book-09-timeslots");

        DatePicker dp = Fx.require(stage, DatePicker.class, x -> showing(x), "date picker");
        Fx.run(dp::show);
        Fx.settle();
        shot("book-10-date-picker");
        Fx.run(dp::hide);

        ComboBox<?> times = timeCombo();
        Fx.run(times::show);
        Fx.settle();
        shot("book-11-time-list");
        Fx.run(() -> { times.getSelectionModel().select(3); times.hide(); });
        Fx.settle();
        shot("book-12-time-chosen");

        clickPrimary();
        shot("book-13-details");
        dump("book-13-details");
        Fx.run(() -> Fx.require(stage, TextArea.class, t -> true, "notes").setText("First visit, a little nervous."));
        clickPrimary();
        shot("book-14-review");
        dump("book-14-review");
        scrollTo(1);
        shot("book-15-review-bottom");

        Button confirm = Fx.require(stage, Button.class, b -> b.getText() != null
                && b.getText().toLowerCase().startsWith("confirm booking"), "confirm");
        Fx.run(confirm::fire);
        Fx.settle();
        Fx.sleep(800);
        shot("book-16-confirmed");
        dump("book-16-confirmed");
        Stage popup = waitOther(3000);
        if (popup != null) {
            shotDialog("book-17-confirmation-notification", popup);
            dumpWindow("book-17-confirmation-notification", popup);
            closeDialogs();
        }

        clickAny("Done", "Skip for now");
        shot("book-18-home-after-booking");

        // Next time, the saved default clinic is offered first.
        Stage again = clickForDialog(b -> b.getText() != null && b.getText().contains("Book") && showing(b), "book again");
        shotDialog("book-19-default-clinic-offer", again);
        dialogButton(again, null);
        Fx.settle();
        // Map unavailable (no internet) state.
        setMapAvailable(false);
        shot("book-20-map-offline");
        setMapAvailable(true);
        tab("Booking");
        tab("Booking");
    }

    private void tracking() {
        ensureLoggedIn(DEMO_EMAIL);
        tab("Tracking");
        shot("tracking-01-appointments");
        hubTab("Test Results");
        shot("tracking-02-tests");
        dump("tracking-02-tests");
        clickRow("Full STI screen");
        shot("tracking-03-test-detail");
        dump("tracking-03-test-detail");
        clickHeader("Back");
        clickRow("Retest (chlamydia)");
        shot("tracking-04-test-pending");
        clickHeader("Back");
        hubTab("Prescriptions");
        shot("tracking-05-prescriptions");
        dump("tracking-05-prescriptions");
        clickRow("Desogestrel");
        shot("tracking-06-prescription-detail");
        dump("tracking-06-prescription-detail");
        clickHeader("Back");
        hubTab("Appointments");
        clickRow("Contraception");
        shot("tracking-07-appointment-detail");
        dump("tracking-07-appointment-detail");
        clickHeader("Back");
        hubTab("Test Results");
        TextField search = Fx.require(stage, TextField.class, f -> f.getPromptText() != null
                && f.getPromptText().startsWith("Search") && showing(f), "search");
        Fx.type(search, "zzz");
        shot("tracking-08-no-matches");
        Fx.type(search, "");
    }

    private void reminders() {
        ensureLoggedIn(DEMO_EMAIL);
        tab("Reminders");
        shot("reminders-01-appointments");
        Fx.run(() -> Fx.require(stage, TitledPane.class, t -> "Time".equals(t.getText()) && showing(t), "time pane").setExpanded(true));
        shot("reminders-02-time-expanded");
        hubTab("Prescriptions");
        shot("reminders-03-prescriptions");
        dump("reminders-03-prescriptions");
        hubTab("Booking");
        shot("reminders-04-booking");
        dump("reminders-04-booking");
        hubTab("Appointments");
        Button preview = Fx.require(stage, Button.class, b -> "Preview notification".equals(b.getText()) && showing(b), "preview");
        Fx.later(preview::fire);
        Stage popup = waitOther(4000);
        if (popup != null) {
            shotDialog("reminders-05-notification-preview", popup);
            closeDialogs();
        }
    }

    private void settings() {
        ensureLoggedIn(DEMO_EMAIL);
        tab("Booking");
        clickHeader("Settings");
        shot("settings-01-account");
        scrollTo(0.45);
        shot("settings-02-accessibility");
        scrollTo(1);
        shot("settings-03-reminder-defaults");
        scrollTo(0);
        Stage d = clickForDialog(b -> b.getStyleClass().contains(UI.BTN_DANGER) && b.getText() != null
                && b.getText().startsWith("Delete"), "delete");
        shotDialog("settings-04-delete-confirm", d);
        dumpWindow("settings-04-delete-confirm", d);
        dialogButton(d, "Cancel");
        clickHeader("Back");
    }

    private void accessibility() {
        ensureLoggedIn(DEMO_EMAIL);
        tab("Booking");
        tab("Booking");
        // Keyboard focus ring: Tab from the top of the page.
        Fx.run(() -> stage.getScene().getRoot().requestFocus());
        for (int i = 0; i < 4; i++) pressTab();
        shot("a11y-01-keyboard-focus");
        var s = Data.getSettings();
        Fx.run(() -> s.setHighContrast(true));
        shot("a11y-02-high-contrast");
        tab("Tracking");
        shot("a11y-03-high-contrast-tracking");
        Fx.run(() -> { s.setHighContrast(false); s.setTextSize(model.AccessibilitySettings.TextSize.LARGE); });
        tab("Booking");
        shot("a11y-04-large-text");
        Fx.run(() -> s.setTextSize(model.AccessibilitySettings.TextSize.MEDIUM));
        Fx.settle();
    }

    private void emptyAccount() {
        String email = "sam.taylor@example.com";
        try {
            dao.UserDAO users = new dao.UserDAO();
            if (!users.emailExists(email)) {
                users.insertUser(email, service.PasswordHasher.hash(DEMO_PASSWORD));
            }
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
        Data.clearCurrentUser();
        login(email, DEMO_PASSWORD);
        shot("empty-01-home");
        tab("Tracking");
        shot("empty-02-tracking");
        hubTab("Test Results");
        shot("empty-03-tests");
        tab("Reminders");
        shot("empty-04-reminders");
        tab("Booking");
    }

    // ---- Steps ---------------------------------------------------------------------------------

    void login(String email, String password) {
        showLoginNow();
        Fx.type(Fx.require(stage, TextField.class, f -> "Email".equals(f.getPromptText()), "email"), email);
        Fx.type(Fx.require(stage, PasswordField.class, f -> true, "password"), password);
        Button login = Fx.labeled(stage, Button.class, "Log in");
        Fx.run(login::fire);
        if (!Fx.waitFor(() -> Data.getCurrentUserId() > 0 && stage.getScene() == Data.getScene(), 20000)) {
            throw new IllegalStateException("Login did not complete");
        }
        Fx.settle();
    }

    void showLoginNow() {
        Fx.run(() -> { try { showLogin(); } catch (IOException e) { throw new RuntimeException(e); } });
        Fx.settle();
    }

    void ensureLoggedIn(String email) {
        if (Data.getCurrentUserId() > 0 && email.equals(Data.getCurrentUserEmail()) && stage.getScene() == Data.getScene()) return;
        login(email, DEMO_PASSWORD);
    }

    void tab(String name) {
        ToggleButton b = Fx.require(stage, ToggleButton.class, t -> name.equals(t.getText())
                && t.getStyleClass().contains(UI.NAV_BTN), "tab " + name);
        Fx.run(b::fire);
        Fx.settle();
    }

    void hubTab(String text) {
        ToggleButton b = Fx.require(stage, ToggleButton.class, t -> text.equals(t.getText())
                && !t.getStyleClass().contains(UI.NAV_BTN) && showing(t), "hub tab " + text);
        Fx.run(b::fire);
        Fx.settle();
    }

    void clickHeader(String accessibleText) {
        Button b = Fx.require(stage, Button.class, t -> accessibleText.equals(t.getAccessibleText()), "header " + accessibleText);
        Fx.run(b::fire);
        Fx.settle();
    }

    void click(String text) {
        Button b = Fx.require(stage, Button.class, x -> text.equals(x.getText()) && showing(x), "button " + text);
        Fx.run(b::fire);
        Fx.settle();
    }

    void clickBook() {
        Button b = Fx.require(stage, Button.class, x -> x.getText() != null && x.getText().contains("Book") && showing(x), "book button");
        Fx.run(b::fire);
        Fx.settle();
    }

    void clickRow(String text) {
        Button b = Fx.require(stage, Button.class, x -> showing(x) && !x.getStyleClass().contains(UI.NAV_BTN)
                && Fx.all(x).stream().anyMatch(n -> n instanceof Label l && text.equals(l.getText())), "row " + text);
        Fx.run(b::fire);
        Fx.settle();
    }

    /** The enabled full-width primary button at the bottom of a booking step. */
    void clickPrimary() {
        Button b = Fx.require(stage, Button.class, x -> x.getStyleClass().contains(UI.BTN) && showing(x) && !x.isDisabled()
                && x.getText() != null && !x.getText().equals("Search"), "primary button");
        Fx.run(b::fire);
        Fx.settle();
    }

    void clickAny(String... texts) {
        Button b = Fx.require(stage, Button.class, x -> showing(x) && java.util.Arrays.asList(texts).contains(x.getText()), "button " + texts[0]);
        Fx.run(b::fire);
        Fx.settle();
    }

    boolean isContinueToServices(Button b) {
        String t = b.getText();
        return t != null && (t.equals("Select Clinic") || t.startsWith("Continue"));
    }

    ComboBox<?> timeCombo() {
        return Fx.require(stage, ComboBox.class, c -> !c.isDisabled() && c.getItems().size() > 2 && showing(c), "time combo");
    }

    /** Clicks a button whose handler opens a modal dialog, and returns the dialog. */
    Stage clickForDialog(Predicate<Button> which, String what) {
        closeDialogs();
        Button b = Fx.require(stage, Button.class, which, what);
        Fx.later(b::fire);
        return Fx.awaitOtherStage(stage);
    }

    void dialogButton(Stage dialog, String text) {
        Fx.run(() -> {
            for (Node n : Fx.all(dialog.getScene().getRoot())) {
                if (n instanceof Button b && (text == null ? b.isCancelButton() : text.equals(b.getText()))) {
                    b.fire();
                    return;
                }
            }
            dialog.close();
        });
        Fx.settle();
    }

    Stage waitOther(long ms) {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            var s = Fx.otherStage(stage);
            if (s.isPresent()) {
                Fx.settle();
                return s.get();
            }
            Fx.sleep(100);
        }
        return null;
    }

    void closeDialogs() {
        for (int i = 0; i < 5; i++) {
            var s = Fx.otherStage(stage);
            if (s.isEmpty()) return;
            Fx.run(() -> s.get().close());
            Fx.sleep(200);
        }
    }

    /** True if the node and all its ancestors are visible (i.e. on screen, not in a hidden tab). */
    static boolean showing(Node n) {
        for (Node p = n; p != null; p = p.getParent()) {
            if (!p.isVisible()) return false;
        }
        return n.getScene() != null;
    }

    void scrollTo(double v) {
        Fx.run(() -> {
            for (Node n : Fx.all(stage.getScene().getRoot())) {
                if (n instanceof ScrollPane sp && showing(sp) && sp.getContent() != null
                        && sp.getContent().getLayoutBounds().getHeight() > sp.getViewportBounds().getHeight() + 1) {
                    sp.setVvalue(v);
                }
            }
        });
        Fx.settle();
    }

    void pressTab() {
        Fx.run(() -> {
            Scene sc = stage.getScene();
            Node target = sc.getFocusOwner() != null ? sc.getFocusOwner() : sc.getRoot();
            javafx.event.Event.fireEvent(target, new javafx.scene.input.KeyEvent(javafx.scene.input.KeyEvent.KEY_PRESSED,
                    "", "", javafx.scene.input.KeyCode.TAB, false, false, false, false));
        });
        Fx.settle();
    }

    javafx.scene.web.WebView webView() {
        return Fx.require(stage, javafx.scene.web.WebView.class, w -> true, "map");
    }

    Object mapScript(String js) {
        return webView().getEngine().executeScript(js);
    }

    void waitForMap() {
        Fx.waitFor(() -> view.ClinicMap.availableProperty().get()
                && webView().getEngine().getLoadWorker().getState() == javafx.concurrent.Worker.State.SUCCEEDED, 20000);
        Fx.sleep(4000);
    }

    void openFirstMapPopup() {
        Fx.run(() -> mapScript("(function(){var done=false; layer.eachLayer(function(l){"
                + "if(!done && l.getPopup && l.getPopup() && l.getTooltip && l.getTooltip() && !l.getTooltip().options.permanent)"
                + "{l.openPopup(); done=true;}});})();"));
    }

    void setMapAvailable(boolean value) {
        Fx.run(() -> {
            try {
                var f = view.ClinicMap.class.getDeclaredField("available");
                f.setAccessible(true);
                ((javafx.beans.property.ReadOnlyBooleanWrapper) f.get(null)).set(value);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        });
        Fx.settle();
    }

    void shot(String name) {
        Fx.settle();
        Fx.save(Fx.snapshotWithPopups(stage), out.resolve(name + ".png"));
    }

    void shotDialog(String name, Stage dialog) {
        Fx.settle();
        BufferedImage main = Fx.snapshot(stage.getScene());
        BufferedImage d = Fx.snapshot(dialog.getScene());
        Fx.save(Fx.withDialog(main, d), out.resolve(name + ".png"));
        Fx.save(d, out.resolve("dialogs").resolve(name + ".png"));
    }

    void dumpWindow(String name, Stage w) {
        Stage saved = stage;
        stage = w;
        try {
            dump(name);
        } finally {
            stage = saved;
        }
    }

    /** Writes the visible text of the current screen, to help write and debug the script. */
    void dump(String name) {
        String text = Fx.call(() -> {
            StringBuilder sb = new StringBuilder();
            for (Node n : Fx.all(stage.getScene().getRoot())) {
                if (!showing(n)) continue;
                String cls = n.getClass().getSimpleName();
                if (n instanceof Labeled l && l.getText() != null && !l.getText().isBlank()) {
                    sb.append(cls).append(" ").append(n.getStyleClass()).append(": ").append(l.getText()).append('\n');
                } else if (n instanceof TextInputControl t) {
                    sb.append(cls).append(" prompt=").append(t.getPromptText()).append(" text=").append(t.getText()).append('\n');
                } else if (n instanceof Button b) {
                    sb.append(cls).append(" acc=").append(b.getAccessibleText()).append('\n');
                }
            }
            return sb.toString();
        });
        try {
            Files.createDirectories(out.resolve("dump"));
            Files.writeString(out.resolve("dump").resolve(name + ".txt"), text);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
