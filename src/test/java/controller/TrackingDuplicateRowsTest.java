package controller;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import model.AppointmentDetails;
import model.PrescriptionSummary;
import model.TestResult;
import model.TestResultStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import service.TrackingService;
import view.TrackingView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Two records with identical details (e.g. two visits to the same service at the same time, or a
 * repeated course) are equal records. The lists used to key their buttons by value, so the first
 * search or filter change tried to add one button twice and the whole list went blank.
 */
class TrackingDuplicateRowsTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already initialized in another test class.
        }
    }

    private static final AppointmentDetails VISIT =
            new AppointmentDetails("12/11/26", "14:00", "14:15", "Contraception Services", "Clinic 6", null);
    private static final model.Test URINE =
            new model.Test("Urine test", "12/11/26", null, null, List.of(new TestResult("Gonorrhoea", TestResultStatus.NEGATIVE)));
    private static final PrescriptionSummary SHOT =
            new PrescriptionSummary("Ceftriaxone", "500mg", 1, 0, "24/08/26", "24/08/26", "", false);

    private static class Fake extends TrackingService {
        Fake() { super(1, null, null, null); }
        @Override public List<model.Test> getTestResults() {
            return List.of(URINE, new model.Test("Urine test", "12/11/26", null, null,
                    List.of(new TestResult("Gonorrhoea", TestResultStatus.NEGATIVE))));
        }
        @Override public List<AppointmentDetails> getPastAppointments() {
            return List.of(VISIT, new AppointmentDetails("12/11/26", "14:00", "14:15", "Contraception Services", "Clinic 6", null));
        }
        @Override public List<PrescriptionSummary> getPrescriptions() {
            return List.of(SHOT, new PrescriptionSummary("Ceftriaxone", "500mg", 1, 0, "24/08/26", "24/08/26", "", false));
        }
    }

    private static <T> T onFx(Callable<T> work) throws Exception {
        CompletableFuture<T> f = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                f.complete(work.call());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        return f.get(10, TimeUnit.SECONDS);
    }

    private static void collect(Node n, List<Node> out) {
        out.add(n);
        // Without a scene there is no skin, so a ScrollPane's content is not yet among its children.
        if (n instanceof javafx.scene.control.ScrollPane sp && sp.getContent() != null) {
            collect(sp.getContent(), out);
        } else if (n instanceof Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) collect(c, out);
        }
    }

    private static List<Node> all(Node root) {
        List<Node> out = new ArrayList<>();
        collect(root, out);
        return out;
    }

    /** Entry rows currently in the page (list buttons carry an HBox graphic). */
    private static long rows(Node page) {
        return all(page).stream().filter(n -> n instanceof Button b && b.getGraphic() instanceof HBox).count();
    }

    private static TextField search(Node page) {
        return (TextField) all(page).stream().filter(n -> n instanceof TextField).findFirst().orElseThrow();
    }

    /** Types a search that matches both, then clears it: both rows must still be listed. */
    private static void assertBothRowsSurviveSearch(Node page) {
        assertEquals(2, rows(page));
        search(page).setText("e");
        assertEquals(2, rows(page));
        search(page).setText("");
        assertEquals(2, rows(page));
    }

    @Test
    void identicalAppointments_bothStayListedWhenSearching() throws Exception {
        onFx(() -> {
            Node page = new TrackingAppointmentController(null, new TrackingView(), new Fake()).getMenuPage();
            assertBothRowsSurviveSearch(page);
            return null;
        });
    }

    @Test
    void identicalTests_bothStayListedWhenSearching() throws Exception {
        onFx(() -> {
            Node page = new TrackingTestController(null, new TrackingView(), new Fake()).getMenuPage();
            assertBothRowsSurviveSearch(page);
            return null;
        });
    }

    @Test
    void identicalPrescriptions_bothStayListedWhenSearching() throws Exception {
        onFx(() -> {
            Node page = new TrackingPrescriptionController(null, new TrackingView(), new Fake()).getMenuPage();
            assertBothRowsSurviveSearch(page);
            return null;
        });
    }
}
