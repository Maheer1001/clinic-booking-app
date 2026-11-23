package controller;

import dao.UserDAO;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import service.AccountSecurityService.ChangePasswordResult;
import service.AuthService;
import view.ui.UI;

import java.sql.SQLException;
import java.util.function.Consumer;

/**
 * The two auth-style pages staff can meet between login and the staff tabs: "waiting for approval"
 * (no clinic yet, or removed) and "choose a new password" (must_reset_password, e.g. a seeded admin).
 * Built with the same classes as the login / sign-up screens.
 */
final class StaffGate {

    static final String PENDING_TITLE = "Waiting for approval";
    static final String PENDING_MESSAGE = "Your staff account is waiting for your clinic admin to approve it.";
    static final String RESET_TITLE = "Choose a new password";

    private StaffGate() {}

    /** Heading block and card, as in login.fxml. */
    private static VBox authScreen(String title, String subtitle, VBox card) {
        Label brand = new Label("HEALTH TRACKING & APPOINTMENTS");
        brand.getStyleClass().add("auth-brand");
        Label heading = new Label(title);
        heading.getStyleClass().add("title");
        Label sub = new Label(subtitle);
        sub.getStyleClass().add("subtitle");
        sub.setWrapText(true);
        sub.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        VBox head = new VBox(brand, heading, sub);
        head.getStyleClass().add("auth-heading");
        card.getStyleClass().add("auth-card");
        VBox page = new VBox(head, card);
        page.getStyleClass().add("auth-page");
        page.getStylesheets().add(StaffGate.class.getResource("/css/auth.css").toExternalForm());
        return page;
    }

    private static Button logOutButton(Runnable logOut) {
        Button b = new Button("Log out");
        b.getStyleClass().add("secondaryBtn");
        b.setOnAction(e -> logOut.run());
        return b;
    }

    /** "Your staff account is waiting for your clinic admin to approve it." with Log out only. */
    static Parent pendingPage(Runnable logOut) {
        Label body = new Label(PENDING_MESSAGE);
        body.setWrapText(true);
        body.getStyleClass().add("muted");
        // Removed or rejected staff land here too (they have no clinic either), so say so.
        Label hint = new Label("Once you're approved, log in again to open the staff app. "
                + "If you've been removed from your clinic, ask your clinic admin.");
        hint.setWrapText(true);
        hint.getStyleClass().add("auth-hint");
        HBox footer = new HBox(logOutButton(logOut));
        footer.getStyleClass().add("auth-footer");
        return UI.authPage(authScreen(PENDING_TITLE, "Clinic staff", new VBox(body, hint, footer)));
    }

    /**
     * Asks for a new password and saves it with {@link AccountController#changePassword}, using the
     * password just typed at login as the current one, then clears must_reset_password and calls
     * {@code done}. Log out leaves without changing anything.
     */
    static Parent passwordResetPage(int userId, String loginPassword, Runnable done, Runnable logOut) {
        return passwordResetPage((pw, onResult) -> Background.run(() -> {
            ChangePasswordResult r = new AccountController().changePassword(userId, loginPassword, pw);
            if (r == ChangePasswordResult.SUCCESS) {
                try {
                    new UserDAO().clearMustResetPassword(userId);
                } catch (SQLException e) {
                    // the new password is saved; at worst they are asked once more next time
                    System.err.println("[StaffLogin] Could not clear must_reset_password (SQLState " + e.getSQLState() + ")");
                }
            }
            return r;
        }, onResult, ex -> onResult.accept(ChangePasswordResult.DB_ERROR)), done, logOut);
    }

    /** Saves a password off the FX thread and reports the result back on it. */
    interface PasswordSaver {
        void save(String newPassword, Consumer<ChangePasswordResult> onResult);
    }

    static Parent passwordResetPage(PasswordSaver saver, Runnable done, Runnable logOut) {
        PasswordField password = new PasswordField();
        password.setPromptText("New password");
        password.setAccessibleText("New password");
        password.getStyleClass().add("input");
        Label rules = new Label(AuthService.PASSWORD_RULES);
        rules.getStyleClass().add("auth-hint");
        rules.setWrapText(true);
        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Confirm new password");
        confirm.setAccessibleText("Confirm new password");
        confirm.getStyleClass().add("input");

        Label message = new Label();
        message.getStyleClass().add(UI.ERROR);
        message.setWrapText(true);
        message.managedProperty().bind(message.textProperty().isNotEmpty());
        message.visibleProperty().bind(message.textProperty().isNotEmpty());
        password.textProperty().addListener((o, a, b) -> message.setText(""));
        confirm.textProperty().addListener((o, a, b) -> message.setText(""));

        BooleanProperty busy = new SimpleBooleanProperty(false);
        Button save = new Button("Save new password");
        save.getStyleClass().add("primaryBtn");
        save.setMaxWidth(Double.MAX_VALUE);
        save.setDefaultButton(true);
        save.disableProperty().bind(busy.or(password.textProperty().isEmpty()).or(confirm.textProperty().isEmpty()));
        password.disableProperty().bind(busy);
        confirm.disableProperty().bind(busy);

        save.setOnAction(e -> {
            if (save.isDisabled()) return;
            String pw = password.getText();
            if (!pw.equals(confirm.getText())) {
                message.setText("Passwords don't match.");
                return;
            }
            if (!AuthService.isStrongEnoughPassword(pw)) {
                message.setText(ChangePasswordResult.WEAK_PASSWORD.message());
                return;
            }
            busy.set(true);
            save.setText("Saving…");
            saver.save(pw, r -> {
                busy.set(false);
                save.setText("Save new password");
                if (r == ChangePasswordResult.SUCCESS) done.run();
                else message.setText(r.message());
            });
        });

        HBox footer = new HBox(logOutButton(logOut));
        footer.getStyleClass().add("auth-footer");
        VBox card = new VBox(password, rules, confirm, save, message, footer);
        Platform.runLater(password::requestFocus);
        return UI.authPage(authScreen(RESET_TITLE,
                "Your clinic set up this account for you. Choose your own password to continue.", card));
    }
}
