package controller;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The chosen sort order must survive typing in the search box. Each list redrew itself from the
 * unsorted filtered list after every search change, so "Name (A-Z)" (or the default newest-first
 * order) was lost as soon as the user searched or cleared the search.
 */
class TrackingListOrderTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already initialized in another test class.
        }
    }

    private static model.Test test(String type, String date) {
        return new model.Test(type, date, null, null, List.of(new TestResult("HIV", TestResultStatus.NEGATIVE)));
    }

    /** Records deliberately in neither name nor date order, as the database may return them. */
    private static class Fake extends TrackingService {
        Fake() { super(1, null, null, null); }
        @Override public List<model.Test> getTestResults() {
            return List.of(test("Syphilis test", "01/10/26"), test("Chlamydia test", "01/11/26"), test("HIV test", "01/09/26"));
        }
        @Override public List<AppointmentDetails> getPastAppointments() {
            return List.of(
                    new AppointmentDetails("01/10/26", "10:00", "10:30", "STI Test", "Clinic 1", null),
                    new AppointmentDetails("01/11/26", "10:00", "10:30", "PrEP Consultation", "Clinic 1", null),
                    new AppointmentDetails("01/09/26", "10:00", "10:30", "HPV Vaccine", "Clinic 1", null));
        }
        @Override public List<PrescriptionSummary> getPrescriptions() {
            return List.of(
                    new PrescriptionSummary("Fluconazole", "150mg", 1, 0, "01/10/26", null, "", true),
                    new PrescriptionSummary("Aciclovir", "400mg", 3, 0, "01/11/26", null, "", true),
                    new PrescriptionSummary("Doxycycline", "100mg", 2, 0, "01/09/26", null, "", true));
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

    /** Titles of the entry rows, top to bottom. */
    private static List<String> rowTitles(Node page) {
        return all(page).stream()
                .filter(n -> n instanceof Button b && b.getGraphic() instanceof HBox)
                .map(n -> ((Label) ((HBox) ((Button) n).getGraphic()).getChildren().getFirst()).getText())
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static void sortBy(Node page, String option) {
        ComboBox<String> sort = (ComboBox<String>) all(page).stream().filter(n -> n instanceof ComboBox).findFirst().orElseThrow();
        sort.setValue(option);
    }

    private static void searchThenClear(Node page) {
        TextField search = (TextField) all(page).stream().filter(n -> n instanceof TextField).findFirst().orElseThrow();
        search.setText("e");
        search.setText("");
    }

    private static void assertOrderKept(Node page, List<String> byName, List<String> newestFirst) {
        assertEquals(newestFirst, rowTitles(page));
        searchThenClear(page);
        assertEquals(newestFirst, rowTitles(page), "default newest-first order after searching");
        sortBy(page, "Name (A-Z)");
        assertEquals(byName, rowTitles(page));
        searchThenClear(page);
        assertEquals(byName, rowTitles(page), "Name (A-Z) order after searching");
    }

    @Test
    void tests_keepSortOrderWhileSearching() throws Exception {
        onFx(() -> {
            Node page = new TrackingTestController(null, new TrackingView(), new Fake()).getMenuPage();
            assertOrderKept(page, List.of("Chlamydia test", "HIV test", "Syphilis test"),
                    List.of("Chlamydia test", "Syphilis test", "HIV test"));
            return null;
        });
    }

    @Test
    void appointments_keepSortOrderWhileSearching() throws Exception {
        onFx(() -> {
            Node page = new TrackingAppointmentController(null, new TrackingView(), new Fake()).getMenuPage();
            assertOrderKept(page, List.of("HPV Vaccine", "PrEP Consultation", "STI Test"),
                    List.of("PrEP Consultation", "STI Test", "HPV Vaccine"));
            return null;
        });
    }

    @Test
    void prescriptions_keepSortOrderWhileSearching() throws Exception {
        onFx(() -> {
            Node page = new TrackingPrescriptionController(null, new TrackingView(), new Fake()).getMenuPage();
            assertOrderKept(page, List.of("Aciclovir", "Doxycycline", "Fluconazole"),
                    List.of("Aciclovir", "Fluconazole", "Doxycycline"));
            return null;
        });
    }
}
