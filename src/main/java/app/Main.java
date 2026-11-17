package app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import view.ui.UI;

import java.awt.Taskbar;
import java.awt.Toolkit;
import java.sql.Connection;
import java.sql.SQLException;

public class Main extends Application {

    /** Size the screens are designed for; also the smallest size the window can be resized to. */
    public static final double WINDOW_WIDTH = 420;
    public static final double WINDOW_HEIGHT = 720;
    private static final double MIN_WIDTH = 420;
    private static final double MIN_HEIGHT = 600;

    private static final String[] FONT_FILES = {
            "Nunito-Light.ttf", "Nunito-Regular.ttf", "Nunito-Medium.ttf",
            "Nunito-SemiBold.ttf", "Nunito-Bold.ttf", "Nunito-ExtraBold.ttf"
    };

    @Override
    public void start(Stage stage) throws Exception {
        // Fonts must be registered before the first scene is styled, or CSS falls back to the system font.
        loadFonts();

        FXMLLoader loader = new FXMLLoader(Main.class.getResource("/fxml/login.fxml"));
        Scene scene = new Scene(loader.load(), WINDOW_WIDTH, WINDOW_HEIGHT);
        scene.getStylesheets().add(UI.STYLE);
        stage.setTitle("Health Tracking and Appointments");
        stage.setScene(scene);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);

        stage.getIcons().add(new Image("/icons/safe.png"));
        setTaskbarIcon();

        stage.show();

        ensureTablesExistInBackground();
    }

    /**
     * Called by JavaFX when the last window closes or {@link Platform#exit()} is called.
     * Background executors (e.g. the reminder scheduler) use non-daemon threads, which would
     * otherwise keep the JVM running invisibly after the window has gone, so end the process here.
     */
    @Override
    public void stop() {
        System.exit(0);
    }

    /**
     * Ensures DB tables exist (CREATE TABLE IF NOT EXISTS) without blocking the window from appearing.
     * If the database is not configured or unreachable the app still opens and the user is told why;
     * signing in will then report a database error.
     */
    private static void ensureTablesExistInBackground() {
        Thread init = new Thread(() -> {
            try (Connection c = DB.connect()) {
                DB.initTables(c);
            } catch (SQLException e) {
                System.err.println("[DB] Initialisation failed: " + e.getMessage());
                Platform.runLater(() -> showDatabaseWarning(e.getMessage()));
            }
        }, "db-init");
        init.setDaemon(true);
        init.start();
    }

    private static void showDatabaseWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Database unavailable");
        alert.setHeaderText("Can't connect to the database");
        Label body = new Label(message);
        body.setWrapText(true);
        body.setMaxWidth(360);
        alert.getDialogPane().setContent(body);
        alert.getDialogPane().getStylesheets().add(UI.STYLE);
        alert.setGraphic(null);
        alert.show();
    }

    private void loadFonts() {
        for (String file : FONT_FILES) {
            var in = getClass().getResourceAsStream("/fonts/" + file);
            if (in == null) {
                System.err.println("Missing font resource: " + file);
                continue;
            }
            try (in) {
                Font.loadFont(in, 14);
            } catch (Exception e) {
                System.err.println("Could not load font " + file + ": " + e.getMessage());
            }
        }
    }

    /** Sets the dock/taskbar icon where the platform supports it (macOS); harmless no-op elsewhere. */
    private static void setTaskbarIcon() {
        try {
            if (!Taskbar.isTaskbarSupported()) return;
            Taskbar taskbar = Taskbar.getTaskbar();
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                taskbar.setIconImage(Toolkit.getDefaultToolkit().getImage(Main.class.getResource("/icons/safe.png")));
            }
        } catch (RuntimeException e) {
            // Headless or restricted environments: the window icon above is enough.
        }
    }

    public static void main(String[] args) {
        launch();
    }
}
