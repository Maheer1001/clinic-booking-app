package controller;

import app.Data;
import app.Main;
import dao.UserDAO;
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

    public MainController(Navigator nav, MainView view, int userID) {

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
        view.getDeleteUser().setOnAction(e -> {
            try {
                new DeleteController(userID);
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        });

        //log out
        view.getLogOutButton().setOnAction(e -> {
            Data.clearCurrentUser();
            Scene currScene = Data.getScene();
            Stage stage = (Stage) currScene.getWindow();
            FXMLLoader loader = new FXMLLoader(Main.class.getResource("/fxml/login.fxml"));
            try {
                Scene scene = new Scene(loader.load(), 420, 720);
                scene.getStylesheets().add(UI.STYLE);
                stage.setScene(scene);
            } catch (Exception err) {
                //if user cannot be logged out, exit app instead
                Platform.exit();
            }

        });

        //new booking - maybe move to more appropriate class?
        view.getQuickBookButton().setOnAction(e -> {
            BookingController bc = new BookingController(nav);
            nav.goToPage(bc.getClinicSearchPage(), "Select Clinic", false);
            try {
                bc.checkForDefaultClinic();
            } catch (SQLException ignored) {}
        });

        //initial navigation states
        booking.selectedProperty().set(true);
        nav.handleTabClick("booking");
    }

    public void setUpSettingsListeners(int userId) {
        //create debounce - prevents db from updating everytime a change occurs
        PauseTransition debounce = new PauseTransition(Duration.seconds(2.5));
        debounce.setOnFinished(e -> new Thread(() -> {
            try {
                UserDAO.setAccessibilitySettingsByUserId(userId, Data.getSettings());
            } catch (SQLException ex) {
                System.err.println("Could not save accessibility settings: " + ex.getMessage());
            }
        }).start());

        AccessibilitySettings settings = Data.getSettings();
        ChangeListener<Object> listener = (obs, o, n) -> debounce.playFromStart();

        //applies debounce listener
        settings.highContrastProperty().addListener(listener);
        settings.boldTextProperty().addListener(listener);
        settings.textSizeProperty().addListener(listener);

        //applies stylesheet change listeners
        settings.highContrastProperty().addListener((obs, old, isOn) -> {
            if (isOn) {
                if (!Data.getScene().getStylesheets().contains(UI.CONTRAST_STYLE)) {
                    Data.getScene().getStylesheets().add(UI.CONTRAST_STYLE);
                }
            } else {
                Data.getScene().getStylesheets().remove(UI.CONTRAST_STYLE);
            }
        });
        settings.boldTextProperty().addListener((obs, old, isOn) -> {
            if (isOn) {
                if (!Data.getScene().getStylesheets().contains(UI.BOLD_STYLE)) {
                    Data.getScene().getStylesheets().add(UI.BOLD_STYLE);
                }
            } else {
                Data.getScene().getStylesheets().remove(UI.BOLD_STYLE);
            }
        });
        settings.textSizeProperty().addListener((obs, o, n) -> {
            Scene scene = Data.getScene();
            scene.getStylesheets().removeAll(UI.TEXT_SIZE_DOWN_STYLE, UI.TEXT_SIZE_UP_STYLE);
            switch (n) {
                case SMALL -> scene.getStylesheets().add(UI.TEXT_SIZE_DOWN_STYLE);
                case LARGE -> scene.getStylesheets().add(UI.TEXT_SIZE_UP_STYLE);
                default -> {}
            }
        });

        //applies user accessibility settings
        if (settings.isHighContrast()) {
            Data.getScene().getStylesheets().add(UI.CONTRAST_STYLE);
        }
        if (settings.isBoldText()) {
            Data.getScene().getStylesheets().add(UI.BOLD_STYLE);
        }
        switch (settings.getTextSize()) {
            case SMALL -> Data.getScene().getStylesheets().add(UI.TEXT_SIZE_DOWN_STYLE);
            case LARGE -> Data.getScene().getStylesheets().add(UI.TEXT_SIZE_UP_STYLE);
            default -> {}
        }
    }
}
