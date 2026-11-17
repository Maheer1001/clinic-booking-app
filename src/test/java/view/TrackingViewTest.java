package view;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TrackingViewTest {

    TrackingView view;

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already initialized; safe to proceed.
        }
    }

    @BeforeEach
    void setUp() {
        view = new TrackingView();
    }

    @AfterEach
    void tearDown() {
        view = null;
    }

    @Test
    void testAppointmentButton() {
        assertNotNull(view.getAppointmentsButton());
        assertEquals("Appointments", view.getAppointmentsButton().getText());
    }

    @Test
    void testTestResultsButton() {
        assertNotNull(view.getTestResultsButton());
        assertEquals("Test Results", view.getTestResultsButton().getText());
    }

    @Test
    void testPrescriptionsButton() {
        assertNotNull(view.getPrescriptionsButton());
        assertEquals("Prescriptions", view.getPrescriptionsButton().getText());
    }

    @Test
    void testButtonsAppearOnTrackingMenu() {
        assertTrue(containsNode(view.getTrackingMainScene(), view.getAppointmentsButton()));
        assertTrue(containsNode(view.getTrackingMainScene(), view.getTestResultsButton()));
        assertTrue(containsNode(view.getTrackingMainScene(), view.getPrescriptionsButton()));
    }

    @Test
    void testTrackingMenuCanIncludeReminderShortcut() {
        Button remindersShortcut = new Button("Go to Reminders");
        Parent scene = view.getTrackingMainScene(remindersShortcut);
        assertTrue(containsNode(scene, remindersShortcut));
    }

    @Test
    void testEntryButtonContainsCorrectText() {
        var button = view.getEntryButton("hello", "world");
        Parent content = (Parent) button.getGraphic();
        assertNotNull(content);
        assertTrue(containsLabelWithGivenText(content, "hello"));
        assertTrue(containsLabelWithGivenText(content, "world"));
    }

    private boolean containsNode(Parent root, Node target) {
        for (Node node : root.getChildrenUnmodifiable()) {
            if (node == target) return true;
            if (node instanceof Parent parent) {
                if (containsNode(parent, target)) return true;
            }
        }
        return false;
    }

    public boolean containsLabelWithGivenText(Parent root, String labelText) {
        for (Node node : root.getChildrenUnmodifiable()) {
            if (node instanceof Label label) {
                if (label.getText().equals(labelText)) return true;
            } else if (node instanceof Parent parent) {
                if (containsLabelWithGivenText(parent, labelText)) return true;
            }
        }
        return false;
    }
}
