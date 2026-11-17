package view;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MainViewTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already initialised by another test class.
        }
    }

    @Test
    void copiesShareTheActionSetLater() {
        MainView view = new MainView();
        Button template = view.getLogOutButton();
        Button first = MainView.copyOf(template);
        Button second = MainView.copyOf(template);

        AtomicInteger clicks = new AtomicInteger();
        template.setOnAction(e -> clicks.incrementAndGet()); // set after the copies, as MainController does

        first.fire();
        second.fire();
        assertEquals(2, clicks.get());
        assertEquals(template.getText(), first.getText());
        assertTrue(first.getStyleClass().containsAll(template.getStyleClass()));
    }

    @Test
    void eachPageCanHoldItsOwnCopy() {
        MainView view = new MainView();
        VBox pageA = new VBox(MainView.copyOf(view.getLogOutButton()));
        VBox pageB = new VBox(MainView.copyOf(view.getLogOutButton()));
        assertEquals(1, pageA.getChildren().size(), "building page B must not steal page A's button");
        assertEquals(1, pageB.getChildren().size());
    }
}
