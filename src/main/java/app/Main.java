package app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import view.ui.UI;

import java.awt.*;
import java.sql.Connection;
import java.sql.SQLException;

public class Main extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        ensureTablesExist();

        FXMLLoader loader = new FXMLLoader(Main.class.getResource("/fxml/login.fxml"));
        Scene scene = new Scene(loader.load(), 420, 720);
        scene.getStylesheets().add(UI.STYLE);
        loadFonts();
        stage.setTitle("Health Tracking and Appointments");
        stage.setScene(scene);

        stage.getIcons().add(new Image("/icons/safe.png"));

        if (Taskbar.isTaskbarSupported()) {
            var taskbar = Taskbar.getTaskbar();
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                final Toolkit toolkit = Toolkit.getDefaultToolkit();
                var dockIcon = toolkit.getImage(Main.class.getResource("/icons/safe.png"));
                taskbar.setIconImage(dockIcon);
            }
        }


        stage.show();
    }

    /**
     * Ensures DB tables exist (CREATE TABLE IF NOT EXISTS). Safe to call with empty DB.
     * If the DB is unreachable, we still show the app; signup/login will then show a DB error.
     */
    private static void ensureTablesExist() {
        try (Connection c = DB.connect()) {
            DB.initTables(c);
        } catch (SQLException e) {
            System.err.println("Database init: " + e.getMessage());
        }
    }

    private void loadFonts() {
        Font font1 = Font.loadFont(
                getClass().getResourceAsStream("/fonts/Nunito-Regular.ttf"),
                14);
        Font font2 = Font.loadFont(
                getClass().getResourceAsStream("/fonts/Nunito-Bold.ttf"),
                14);
        Font font3 = Font.loadFont(
                getClass().getResourceAsStream("/fonts/Nunito-SemiBold.ttf"),
                14);
        Font font4 = Font.loadFont(
                getClass().getResourceAsStream("/fonts/Nunito-ExtraBold.ttf"),
                14);
    }

    public static void main(String[] args) {
        launch();
    }
}