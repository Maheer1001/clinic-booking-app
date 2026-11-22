package controller;

import app.Data;
import app.Main;
import dao.UserDAO;
import javafx.animation.Animation;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.util.Duration;
import model.AccessibilitySettings;
import view.*;
import javafx.stage.Stage;
import view.ui.UI;

import java.sql.SQLException;

public class MainController {

    private PauseTransition settingsSave;
    private AccessibilitySettings settings;
    private int settingsUserId;
    private final Navigator nav;

    public MainController(Navigator nav, MainView view, int userID) {
        this.nav = nav;

        //sets up toggle group for navigation bar buttons
        ToggleGroup group = new ToggleGroup();
        ToggleButton booking = view.getBookingButton();
        ToggleButton tracking = view.getTrackingButton();
        ToggleButton reminders = view.getRemindersButton();
        booking.setToggleGroup(group);
        tracking.setToggleGroup(group);
        reminders.setToggleGroup(group);

        //sets actions for each - set selected property to true everytime to ensure clicking a button twice stays selected
        booking.setOnAction(e -> {booking.selectedProperty().set(true); nav.handleTabClick("booking");});

        tracking.setOnAction(e -> {tracking.selectedProperty().set(true); nav.handleTabClick("tracking");});

        reminders.setOnAction(e -> {reminders.selectedProperty().set(true); nav.handleTabClick("reminders");});

        setUpSettingsListeners(userID);

        //delete user
        view.getDeleteUser().setOnAction(e -> new DeleteController(userID, msg -> {
            // the account is gone; drop any pending settings write rather than saving to a deleted row
            settingsSave.stop();
            logOut(msg);
        }));

        //log out
        // Unsaved Settings edits get a "Discard your changes?" first (Navigator's guard isn't asked on logout).
        view.getLogOutButton().setOnAction(e -> {
            if (!nav.confirmLeave()) return;
            flushSettings();
            logOut(null);
        });

        //new booking - maybe move to more appropriate class?
        view.getQuickBookButton().setOnAction(e -> {
            BookingController bc = new BookingController(nav);
            nav.goToPage(bc.getClinicSearchPage(), "Find a clinic", false);
            try {
                bc.checkForDefaultClinic();
            } catch (SQLException ignored) {}
        });

        //initial navigation states
        booking.selectedProperty().set(true);
        nav.handleTabClick("booking");
    }

    /** Clears the session and returns to the login screen, optionally showing a message there. */
    private void logOut(String infoMessage) {
        Scene currScene = Data.getScene();
        Data.clearCurrentUser();
        // every tab's pages and history belong to this session
        nav.reset();
        // the in-progress booking holds personal details; don't let the next user see them
        Data.reset();
        Data.setSettings(null);
        Data.getSignUpDraft().password = "";
        Data.getSignUpDraft().confirmPassword = "";
        Data.getSignUpDraft().securityAnswer = "";
        Stage stage = (Stage) currScene.getWindow();
        FXMLLoader loader = new FXMLLoader(Main.class.getResource("/fxml/login.fxml"));
        try {
            Scene scene = new Scene(UI.authPage(loader.load()), 420, 720);
            scene.getStylesheets().add(UI.STYLE);
            if (infoMessage != null) {
                LoginController login = loader.getController();
                login.showInfo(infoMessage);
            }
            stage.setScene(scene);
        } catch (Exception err) {
            //if user cannot be logged out, exit app instead
            Platform.exit();
        }
    }

    /** Saves a settings change that is still waiting for its debounce, e.g. when the user logs out. */
    public void flushSettings() {
        if (settingsSave != null && settingsSave.getStatus() == Animation.Status.RUNNING) {
            settingsSave.stop();
            saveSettingsAsync();
        }
    }

    private void saveSettingsAsync() {
        // capture now: by the time the thread runs, another user may have logged in
        int userId = settingsUserId;
        AccessibilitySettings snapshot = new AccessibilitySettings(
                settings.isHighContrast(), settings.isBoldText(), settings.getTextSize());
        Thread t = new Thread(() -> {
            try {
                UserDAO.setAccessibilitySettingsByUserId(userId, snapshot);
            } catch (SQLException ex) {
                System.err.println("Could not save accessibility settings: " + ex.getMessage());
            }
        }, "save-accessibility-settings");
        t.setDaemon(false);
        t.start();
    }

    public void setUpSettingsListeners(int userId) {
        if (Data.getSettings() == null) {
            Data.setSettings(new AccessibilitySettings());
        }
        settings = Data.getSettings();
        settingsUserId = userId;

        //create debounce - prevents db from updating everytime a change occurs
        settingsSave = new PauseTransition(Duration.seconds(2.5));
        settingsSave.setOnFinished(e -> saveSettingsAsync());

        PauseTransition debounce = settingsSave;
        ChangeListener<Object> listener = (obs, o, n) -> debounce.playFromStart();

        //applies debounce listener
        settings.highContrastProperty().addListener(listener);
        settings.boldTextProperty().addListener(listener);
        settings.textSizeProperty().addListener(listener);

        // the main scene these settings style (captured so a later login's scene isn't affected)
        Scene scene = Data.getScene();
        ChangeListener<Object> restyle = (obs, o, n) -> applySettings(scene, settings);
        settings.highContrastProperty().addListener(restyle);
        settings.boldTextProperty().addListener(restyle);
        settings.textSizeProperty().addListener(restyle);

        //applies user accessibility settings
        applySettings(scene, settings);
    }

    /**
     * Makes the scene's accessibility stylesheets match the settings. They are added after
     * the base stylesheet so they override it, and re-applying is idempotent.
     */
    static void applySettings(Scene scene, AccessibilitySettings settings) {
        if (scene == null || settings == null) return;
        var sheets = scene.getStylesheets();
        sheets.removeAll(UI.CONTRAST_STYLE, UI.BOLD_STYLE, UI.TEXT_SIZE_DOWN_STYLE, UI.TEXT_SIZE_UP_STYLE);
        if (settings.isHighContrast()) sheets.add(UI.CONTRAST_STYLE);
        if (settings.isBoldText()) sheets.add(UI.BOLD_STYLE);
        switch (settings.getTextSize() != null ? settings.getTextSize() : AccessibilitySettings.TextSize.MEDIUM) {
            case SMALL -> sheets.add(UI.TEXT_SIZE_DOWN_STYLE);
            case LARGE -> sheets.add(UI.TEXT_SIZE_UP_STYLE);
            default -> {}
        }
    }
}
