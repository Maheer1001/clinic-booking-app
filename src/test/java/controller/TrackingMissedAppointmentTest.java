package controller;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import model.AppointmentDetails;
import model.AppointmentStatus;
import model.PrescriptionSummary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import service.TrackingService;
import view.TrackingView;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** A visit the clinic marked NO_SHOW stays in past appointments, with a "Missed" status pill. */
class TrackingMissedAppointmentTest {

    private static final AppointmentDetails MISSED = new AppointmentDetails("10/11/26", "09:00", "09:15",
            "STI Test", "Clinic 1", null, AppointmentStatus.NO_SHOW);
    private static final AppointmentDetails ATTENDED = new AppointmentDetails("01/11/26", "10:00", "10:15",
            "HPV Vaccine", "Clinic 1", null, AppointmentStatus.COMPLETED);

    private static class Fake extends TrackingService {
        Fake() { super(1, null, null, null); }
        @Override public List<model.Test> getTestResults() { return List.of(); }
        @Override public List<AppointmentDetails> getPastAppointments() { return List.of(MISSED, ATTENDED); }
        @Override public List<PrescriptionSummary> getPrescriptions() { return List.of(); }
    }

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already initialized in another test class.
        }
    }

    private static <T extends Node> List<T> find(Node node, Class<T> type, List<T> out) {
        if (type.isInstance(node)) out.add(type.cast(node));
        if (node instanceof ScrollPane sp) find(sp.getContent(), type, out);
        else if (node instanceof Button b && b.getGraphic() != null) find(b.getGraphic(), type, out);
        else if (node instanceof Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) find(c, type, out);
        }
        return out;
    }

    private static List<Label> pills(Node node) {
        return find(node, Label.class, new ArrayList<>()).stream()
                .filter(l -> l.getStyleClass().contains("tracking-status")).toList();
    }

    @Test
    void listMarksOnlyTheNoShowAsMissed() {
        TrackingAppointmentController controller = new TrackingAppointmentController(null, new TrackingView(), new Fake());
        Node page = controller.getMenuPage();

        List<Label> pills = pills(page);
        assertEquals(1, pills.size(), "only the missed visit has a pill");
        assertEquals("Missed", pills.getFirst().getText());
        assertTrue(pills.getFirst().getStyleClass().contains("tracking-status-missed"));
        List<Button> rows = find(page, Button.class, new ArrayList<>()).stream()
                .filter(b -> b.getAccessibleText() != null && b.getAccessibleText().startsWith("STI Test")).toList();
        assertEquals(1, rows.size());
        assertTrue(rows.getFirst().getAccessibleText().contains("Missed"));
    }

    @Test
    void detailsPageShowsTheMissedPill_andMissedIsSearchable() {
        TrackingAppointmentController controller = new TrackingAppointmentController(null, new TrackingView(), new Fake());
        assertEquals(List.of("Missed"), pills(controller.getDetailsPage(MISSED)).stream().map(Label::getText).toList());
        assertTrue(pills(controller.getDetailsPage(ATTENDED)).isEmpty());
        assertTrue(controller.getSearchableText(MISSED).contains("Missed"));
        assertFalse(controller.getSearchableText(ATTENDED).contains("Missed"));
    }

    @Test
    void sharedPillHelperIsUsedForTestsToo() {
        Label l = TrackingTestController.statusLabel(model.TestResultStatus.POSITIVE);
        assertTrue(l.getStyleClass().containsAll(List.of("tracking-status", "tracking-status-positive")));
    }
}
