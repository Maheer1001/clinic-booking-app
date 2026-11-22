package controller;

import app.Data;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import model.Test;
import model.TestResult;
import model.TestResultStatus;
import model.TrackingData;
import service.TrackingService;
import view.Navigator;
import view.TrackingView;
import view.reminders.RemindersView;
import view.ui.UI;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Handles tracking test results by building pages and
 * controlling search sort filter logic
 */
public class TrackingTestController extends TrackingSectionController {

    public TrackingTestController(Navigator nav, TrackingView view, TrackingService model) {
        super(nav, view, model);
    }

    @Override
    public String getSearchableText(TrackingData td) {
        Test test = (Test) td;
        StringBuilder text = new StringBuilder(test.testType() + " ");
        text.append(test.isTreatment() ? "treatment " + test.treatment() + " " : "");
        text.append(test.isClinicianNotes() ? "notes " + test.clinicianNotes() : "");
        for (TestResult res : test.testResults()) {
            // Spaced, so "chlamydia positive" matches (it used to read "ChlamydiaPOSITIVE").
            TestResultStatus status = res.result() != null ? res.result() : TestResultStatus.PENDING;
            text.append(" ").append(res.condition()).append(" ").append(status.label()).append(" ");
        }
        return text.toString();
    }

    @Override
    public Node getMenuPage() {
        ObservableList<Test> tests = FXCollections.observableList(model.getTestResults());
        FilteredList<Test> filteredTests = new FilteredList<>(tests, p -> true);
        SortedList<Test> sortedTests = new SortedList<>(filteredTests);
        // Newest first until the user picks another order (the list was previously in raw DB order).
        sortedTests.setComparator(getComparator(null));

        if (tests.isEmpty()) {
            return UI.page(UI.emptyState(UI.ICON_FLASK, "No test results yet",
                    "When the clinic adds a result it appears here, and we'll let you know."));
        }
        VBox list = new VBox(10);
        list.setAlignment(Pos.CENTER);

        // Keyed by identity: two identical test records must still get a button each.
        Map<Test, Button> buttonMap = new IdentityHashMap<>();

        for (Test test : sortedTests) {
            String left = test.testType();
            String right = TrackingDates.display(test.testDate());
            Button button = view.getEntryButton(left, right);
            button.setOnAction(e -> nav.goToPage(getDetailsPage(test), "Test result", false));
            buttonMap.put(test, button);
            list.getChildren().add(button);
        }

        // Redraw from the sorted view only: it wraps the filtered list, so it also fires when the search
        // or dates change. Redrawing from the filtered list as well undid the chosen sort order.
        sortedTests.addListener((ListChangeListener<Test>) c ->
                list.getChildren().setAll(sortedTests.stream().map(buttonMap::get).toList()));

        TextField search = new TextField();
        search.setPromptText("Search");
        search.setAccessibleText("Search");
        UI.searchField(search);

        ObservableList<String> items = FXCollections.observableArrayList("Date (Newest)", "Date (Oldest)", "Name (A-Z)");
        ComboBox<String> sort = new ComboBox<>(items);
        sort.setPromptText("Sort");
        UI.sort(sort);
        sort.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText("Sort");
                } else {
                    setText(item);
                }
            }
        });

        HBox searchBox = new HBox(10, search, sort);
        searchBox.setAlignment(Pos.CENTER);

        DatePicker from = new DatePicker();
        from.setShowWeekNumbers(false);
        UI.datePicker(from);
        DatePicker to = new DatePicker();
        to.setShowWeekNumbers(false);
        UI.datePicker(to);

        Label fromLabel = new Label("From: ");
        Label toLabel = new Label("To: ");
        HBox.setHgrow(fromLabel, Priority.NEVER);
        HBox.setHgrow(toLabel, Priority.NEVER);
        fromLabel.setMinWidth(Region.USE_PREF_SIZE);
        fromLabel.setMinWidth(Region.USE_PREF_SIZE);

        GridPane filterGrid = new GridPane();
        filterGrid.setHgap(10);

        filterGrid.add(fromLabel, 0, 0);
        filterGrid.add(from,      1, 0);
        filterGrid.add(toLabel,   2, 0);
        filterGrid.add(to,        3, 0);

        ColumnConstraints c1 = new ColumnConstraints();
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);

        ColumnConstraints c3 = new ColumnConstraints();
        ColumnConstraints c4 = new ColumnConstraints();
        c4.setHgrow(Priority.ALWAYS);

        filterGrid.getColumnConstraints().addAll(c1, c2, c3, c4);
        filterGrid.setMaxWidth(Double.MAX_VALUE);

        class Update {
            private void updateFilteredTestEntries() {
                String searchText = search.getText() == null ? "" : search.getText().toLowerCase();
                LocalDate dateFrom = from.getValue();
                LocalDate dateTo = to.getValue();
                filteredTests.setPredicate(getPredicate(searchText, dateFrom, dateTo));
            }
            private void updateSortedTestEntries() {
                sortedTests.setComparator(getComparator(sort.getValue()));
            }
        }

        Button clearButton = UI.clearButton("Clear filters");
        clearButton.setOnAction(e -> {from.setValue(null); to.setValue(null); search.clear(); sort.getSelectionModel().clearSelection();});

        from.valueProperty().addListener((obs, oldVal, newVal) -> new Update().updateFilteredTestEntries());
        to.valueProperty().addListener((obs, oldVal, newVal) -> new Update().updateFilteredTestEntries());
        search.textProperty().addListener((obs, oldVal, newVal) -> new Update().updateFilteredTestEntries());
        sort.valueProperty().addListener((obs, oldVal, newVal) -> new Update().updateSortedTestEntries());

        ScrollPane sp = new ScrollPane(UI.pageTop(searchBox, filterGrid, clearButton, list, noMatchesLabel(filteredTests)));
        sp.setFitToWidth(true);
        return sp;
    }

    public Predicate<Test> getPredicate(String searchText, LocalDate dateFrom, LocalDate dateTo) {
        return test -> {
            // A missing / malformed date used to throw here and break the whole list.
            LocalDate date = TrackingDates.parse(test.testDate());
            boolean matchesSearch = getSearchableText(test).toLowerCase().contains(searchText);
            return TrackingDates.inRange(date, dateFrom, dateTo) && matchesSearch;
        };
    }

    public Comparator<Test> getComparator(String val) {
        if (val == null) {
            return TrackingDates.newestFirst(Test::testDate);
        }
        return switch (val) {
            case "Date (Oldest)" -> TrackingDates.oldestFirst(Test::testDate);
            case "Name (A-Z)" -> TrackingDates.byName(Test::testType);
            default -> TrackingDates.newestFirst(Test::testDate);
        };
    }

    @Override
    public Node getDetailsPage(TrackingData td) {
        Test test = (Test) td;

        HBox graphic = view.getEntryHeader(test.testType(), TrackingDates.display(test.testDate()));

        Label title = UI.heading("Results");
        VBox resultsInfo = new VBox(6, title);

        boolean isPositive = false;

        List<TestResult> results = test.testResults();
        if (results.isEmpty()) {
            resultsInfo.getChildren().add(UI.muted("No results have been recorded for this test yet."));
        }
        for (TestResult result : results) {
            if (result.result() == TestResultStatus.POSITIVE) {
                isPositive = true;
            }
            resultsInfo.getChildren().add(resultRow(result));
        }

        VBox info = new VBox(10, resultsInfo);

        if (test.isTreatment()) {
            VBox treatmentInfo = UI.detail("TREATMENT", test.treatment());
            info.getChildren().add(treatmentInfo);
        }
        if (test.isClinicianNotes()) {
            VBox notesInfo = UI.detail("CLINICIAN NOTES", test.clinicianNotes());
            info.getChildren().add(notesInfo);
        }

        Button bookingReminderButton = UI.shortcutButton("Check-up reminders");
        bookingReminderButton.setOnAction(e -> nav.goToPageNewTab("reminders", new RemindersView(Data.getCurrentUserId()).getRemindersScene("booking"), "Reminders", true));
        HBox reminder = UI.callout("Regular check-ups keep you on top of your sexual health.", bookingReminderButton);

        if (isPositive) {
            BookingController bc = new BookingController(nav);

            Button newBookingButton = UI.shortcutButton("Book a retest");
            newBookingButton.setOnAction(e -> {
                nav.goToPageNewTab("booking", bc.getClinicSearchPage(), "Find a clinic", false);
                nav.clearTabStack("booking");
                // Same offer as "+ Book Appointment": book at the saved default clinic, or choose another.
                try { bc.checkForDefaultClinic(); } catch (java.sql.SQLException ignored) {}
            });
            HBox booking = UI.callout("A positive result usually needs a retest. Book a follow-up when you are ready.", newBookingButton);

            return withTrackingStyles(UI.page(UI.card(12, graphic, info), booking, reminder));
        } else {
            return withTrackingStyles(UI.page(UI.card(12, graphic, info), reminder));
        }
    }

    /** "Chlamydia   [Negative]" with a coloured status label instead of the raw enum name. */
    private static HBox resultRow(TestResult result) {
        Label condition = UI.body(result.condition());
        condition.setWrapText(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(10, condition, spacer, statusLabel(result.result()));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    static Label statusLabel(TestResultStatus status) {
        TestResultStatus s = status != null ? status : TestResultStatus.PENDING;
        return statusPill(s.label(), s.name().toLowerCase());
    }
}
