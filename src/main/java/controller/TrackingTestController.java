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
import java.util.HashMap;
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
            text.append(res.condition()).append(res.result()).append(" ");
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
            return UI.page(UI.muted("You have no test results yet. Results added by the clinic will appear here."));
        }
        VBox list = new VBox(10);
        list.setAlignment(Pos.CENTER);

        Map<Test, Button> buttonMap = new HashMap<>();

        for (Test test : sortedTests) {
            String left = test.testType();
            String right = TrackingDates.display(test.testDate());
            Button button = view.getEntryButton(left, right);
            button.setOnAction(e -> nav.goToPage(getDetailsPage(test), "View Test Details", false));
            buttonMap.put(test, button);
            list.getChildren().add(button);
        }

        filteredTests.addListener((ListChangeListener<Test>) c ->
                list.getChildren().setAll(filteredTests.stream().map(buttonMap::get).toList()));

        sortedTests.addListener((ListChangeListener<Test>) c ->
                list.getChildren().setAll(sortedTests.stream().map(buttonMap::get).toList()));

        TextField search = new TextField();
        search.setPromptText("Search");
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

        Button clearButton = UI.clearButton("Clear All");
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

        HBox graphic = (HBox) view.getEntryButton(test.testType(), TrackingDates.display(test.testDate())).getGraphic();

        Label title = UI.heading("Results:");
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
            VBox treatmentInfo = new VBox(UI.heading("Treatment:"), UI.body(test.treatment()));
            info.getChildren().add(treatmentInfo);
        }
        if (test.isClinicianNotes()) {
            VBox notesInfo = new VBox(UI.heading("Clinician Notes:"), UI.body(test.clinicianNotes()));
            info.getChildren().add(notesInfo);
        }

        Label bookingReminderLabel = UI.body("Keep on top of your sexual health by booking regular appointments.");
        bookingReminderLabel.setWrapText(true);

        Button bookingReminderButton = UI.shortcutButton("Manage booking reminders");
        bookingReminderButton.setOnAction(e -> nav.goToPageNewTab("reminders", new RemindersView(Data.getCurrentUserId()).getRemindersScene("booking"), "Reminders", true));

        HBox reminder = UI.equalSpacing(new HBox(10, bookingReminderLabel, bookingReminderButton));
        reminder.getStyleClass().add(UI.PAGE);

        if (isPositive) {
            BookingController bc = new BookingController(nav);

            Label newBookingLabel = UI.body("Remember to book a follow up appointment to retest yourself.");
            newBookingLabel.setWrapText(true);

            Button newBookingButton = UI.shortcutButton("Make new booking");
            newBookingButton.setOnAction(e -> {nav.goToPageNewTab("booking", bc.getClinicSearchPage(), "Select Clinic", false); nav.clearTabStack("booking");});

            HBox booking = UI.equalSpacing(new HBox(10, newBookingLabel, newBookingButton));
            booking.getStyleClass().add(UI.PAGE);

            return withTrackingStyles(UI.page(UI.card(10, graphic, info), new VBox(booking, reminder)));
        } else {
            return withTrackingStyles(UI.page(UI.card(10, graphic, info), reminder));
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
        Label label = new Label(s.label());
        label.getStyleClass().addAll("tracking-status", "tracking-status-" + s.name().toLowerCase());
        label.setMinWidth(Region.USE_PREF_SIZE);
        return label;
    }
}
