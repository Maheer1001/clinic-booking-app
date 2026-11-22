package controller;

import app.Data;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.VBox;
import model.AppointmentDetails;
import model.PrescriptionSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import service.TrackingService;
import view.MainView;
import view.Navigator;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Coming back to Tracking reloads the data but keeps what the user had chosen on the page. */
class TrackingControllerRefreshTest {

    private static class FakeTrackingService extends TrackingService {
        final List<model.Test> tests;

        FakeTrackingService(List<model.Test> tests) {
            super(1, null, null, null);
            this.tests = tests;
        }

        @Override public List<model.Test> getTestResults() { return tests; }
        @Override public List<AppointmentDetails> getPastAppointments() { return List.of(); }
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

    @BeforeEach
    void setUp() {
        Data.clearCurrentUser();
        Data.setLastSeenTestCount(-1);
    }

    @AfterEach
    void tearDown() {
        Data.clearCurrentUser();
        Data.setLastSeenTestCount(-1);
    }

    private static model.Test test(String type, String date) {
        return new model.Test(type, date, null, null, List.of());
    }

    private static <T extends Node> List<T> find(Node node, Class<T> type, List<T> out) {
        if (type.isInstance(node)) out.add(type.cast(node));
        if (node instanceof ScrollPane sp) find(sp.getContent(), type, out);
        else if (node instanceof Parent p && !(node instanceof javafx.scene.control.Control)) {
            for (Node c : p.getChildrenUnmodifiable()) find(c, type, out);
        }
        return out;
    }

    private static List<String> visibleEntryTitles(Node hub) {
        List<String> titles = new ArrayList<>();
        for (Label l : findLabels(hub)) {
            // buttons filtered out are taken out of the list, so only listed entries are reached
            if (l.getStyleClass().contains("entry-title")) {
                titles.add(l.getText());
            }
        }
        return titles;
    }

    private static List<Label> findLabels(Node node) {
        List<Label> out = new ArrayList<>();
        if (node instanceof Label l) out.add(l);
        if (node instanceof ScrollPane sp) out.addAll(findLabels(sp.getContent()));
        else if (node instanceof javafx.scene.control.Button b && b.getGraphic() != null) out.addAll(findLabels(b.getGraphic()));
        else if (node instanceof Parent p && !(node instanceof javafx.scene.control.Control)) {
            for (Node c : p.getChildrenUnmodifiable()) out.addAll(findLabels(c));
        }
        return out;
    }

    @Test
    void refreshKeepsSubTabAndSearchAndShowsNewEntries() {
        Navigator nav = new Navigator(new MainView());
        TrackingController controller = new TrackingController(nav,
                new FakeTrackingService(List.of(test("Blood test", "01/03/26"), test("Urine test", "02/03/26"))));
        Node hub = controller.getTrackingMainScene();

        ToggleButton testsTab = find(hub, ToggleButton.class, new ArrayList<>()).stream()
                .filter(b -> b.getText().equals("Test Results")).findFirst().orElseThrow();
        testsTab.setSelected(true);
        TextField search = find(hub, TextField.class, new ArrayList<>()).stream()
                .filter(f -> f.isVisible() && isShowing(f, hub)).findFirst().orElseThrow();
        search.setText("blood");

        controller.applyRefresh(new FakeTrackingService(List.of(
                test("Blood test (repeat)", "05/03/26"), test("Blood test", "01/03/26"), test("Urine test", "02/03/26"))));

        assertSame(hub, controller.getTrackingMainScene(), "the page is kept, not rebuilt");
        assertTrue(testsTab.isSelected(), "the chosen sub-tab is kept");
        TextField newSearch = find(hub, TextField.class, new ArrayList<>()).stream()
                .filter(f -> isShowing(f, hub)).findFirst().orElseThrow();
        assertNotSame(search, newSearch, "the list was rebuilt from the new data");
        assertEquals("blood", newSearch.getText());
        List<String> shown = visibleEntryTitles(hub);
        assertTrue(shown.contains("Blood test (repeat)"), "the result added meanwhile appears: " + shown);
        assertFalse(shown.contains("Urine test"), "the search still filters: " + shown);
    }

    /** True if every ancestor up to the hub is visible (the hub hides the sub-tabs not chosen). */
    private static boolean isShowing(Node node, Node hub) {
        for (Node n = node; n != null && n != hub; n = n.getParent()) {
            if (!n.isVisible()) return false;
        }
        return true;
    }

    @Test
    void refreshFromEmptyShowsTheList() {
        Navigator nav = new Navigator(new MainView());
        TrackingController controller = new TrackingController(nav, new FakeTrackingService(List.of()));
        Node hub = controller.getTrackingMainScene();
        controller.applyRefresh(new FakeTrackingService(List.of(test("First result", "05/03/26"))));
        assertTrue(visibleEntryTitles(hub).contains("First result"));
    }

    @Test
    void newResultsSinceLastSeenCountsOnlyWhatArrivedSinceTheLastLook() {
        TrackingController controller = new TrackingController(new Navigator(new MainView()), new FakeTrackingService(List.of()));
        // the constructor's look set the baseline at 0 results
        assertEquals(0, Data.getLastSeenTestCount());
        List<model.Test> two = List.of(test("New", "05/03/26"), test("Old", "01/03/26"));
        assertEquals(List.of(two.getFirst()), controller.newResultsSinceLastSeen(new FakeTrackingService(two.subList(0, 1))));
        assertEquals(List.of(), controller.newResultsSinceLastSeen(new FakeTrackingService(two.subList(0, 1))),
                "already seen");
        assertEquals(1, controller.newResultsSinceLastSeen(new FakeTrackingService(two)).size());
    }

    @Test
    void pageStateIsCarriedOntoARebuiltPage() {
        VBox oldPage = page();
        ((TextField) oldPage.getChildren().get(0)).setText("chlamydia");
        @SuppressWarnings("unchecked")
        ComboBox<String> oldSort = (ComboBox<String>) oldPage.getChildren().get(1);
        oldSort.setValue("Name (A-Z)");
        ((DatePicker) oldPage.getChildren().get(2)).setValue(LocalDate.of(2026, 1, 1));
        ((DatePicker) oldPage.getChildren().get(3)).setValue(LocalDate.of(2026, 2, 1));

        TrackingPageState state = TrackingPageState.capture(new ScrollPane(oldPage));
        VBox newPage = page();
        state.applyTo(new ScrollPane(newPage));

        assertEquals("chlamydia", ((TextField) newPage.getChildren().get(0)).getText());
        assertEquals("Name (A-Z)", ((ComboBox<?>) newPage.getChildren().get(1)).getValue());
        assertEquals(LocalDate.of(2026, 1, 1), ((DatePicker) newPage.getChildren().get(2)).getValue());
        assertEquals(LocalDate.of(2026, 2, 1), ((DatePicker) newPage.getChildren().get(3)).getValue());
        // an empty-state page has no controls; nothing to carry and nothing breaks
        TrackingPageState.capture(new VBox()).applyTo(new VBox());
    }

    private static VBox page() {
        return new VBox(new TextField(),
                new ComboBox<>(javafx.collections.FXCollections.observableArrayList("Date (Newest)", "Name (A-Z)")),
                new DatePicker(), new DatePicker());
    }
}
