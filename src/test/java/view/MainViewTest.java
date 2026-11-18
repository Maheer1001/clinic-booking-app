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

    @Test
    void summary_describesNextAppointment() {
        java.time.LocalDate today = java.time.LocalDate.of(2026, 11, 18);
        var sti = new model.UpcomingAppointmentDetails(1, "19/11/26", "10:00", "10:30", "STI test", "Clinic A", null);
        var hiv = new model.UpcomingAppointmentDetails(2, "27/11/26", "14:00", "14:15", "HIV test", "Clinic B", null);

        assertEquals("You have no upcoming appointments.", MainView.summary(java.util.List.of(), today));
        assertEquals("Your STI test is tomorrow at 10:00.", MainView.summary(java.util.List.of(sti), today));
        assertEquals("Your STI test is tomorrow at 10:00. 2 appointments booked.",
                MainView.summary(java.util.List.of(sti, hiv), today));
    }
}
