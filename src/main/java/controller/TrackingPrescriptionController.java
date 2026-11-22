package controller;

import app.Data;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import model.PrescriptionSummary;
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
import java.util.stream.Collectors;

/**
 * Handles tracking prescriptions by building pages and
 * controlling search sort filter logic
 */
public class TrackingPrescriptionController extends TrackingSectionController {

    public TrackingPrescriptionController(Navigator nav, TrackingView view, TrackingService model) {
        super(nav, view, model);
    }

    @Override
    public String getSearchableText(TrackingData td) {
        PrescriptionSummary pres = (PrescriptionSummary) td;
        String text = pres.name() + " " + pres.dosage() + " " + pres.getCourse() + " " + pres.instructions();
        // Same rule as the CURRENT / PAST sections, so searching "current" finds what is listed there.
        text += pres.active() ? " active current" : " inactive past history finished";
        return text;
    }

    @Override
    public Node getMenuPage() {
        Map<Boolean, List<PrescriptionSummary>> splitPres = model.getPrescriptions().stream().collect(Collectors.partitioningBy(PrescriptionSummary::active));

        ObservableList<PrescriptionSummary> prescriptionsCurrent = FXCollections.observableList(splitPres.get(true));
        FilteredList<PrescriptionSummary> filteredCurrent = new FilteredList<>(prescriptionsCurrent, p -> true);
        SortedList<PrescriptionSummary> sortedCurrent = new SortedList<>(filteredCurrent);

        ObservableList<PrescriptionSummary> prescriptionsHistory = FXCollections.observableList(splitPres.get(false));
        FilteredList<PrescriptionSummary> filteredHistory = new FilteredList<>(prescriptionsHistory, p -> true);
        SortedList<PrescriptionSummary> sortedHistory = new SortedList<>(filteredHistory);
        sortedCurrent.setComparator(getComparator(null));
        sortedHistory.setComparator(getComparator(null));

        if (prescriptionsCurrent.isEmpty() && prescriptionsHistory.isEmpty()) {
            return UI.page(UI.emptyState(UI.ICON_PILL, "No prescriptions",
                    "Medicine prescribed by the clinic will be listed here with its dose and course."));
        }

        VBox listCurrent = new VBox(10);
        listCurrent.setAlignment(Pos.CENTER);

        VBox listHistory = new VBox(10);
        listHistory.setAlignment(Pos.CENTER);

        listCurrent.visibleProperty().bind(Bindings.isNotEmpty(filteredCurrent));
        listCurrent.managedProperty().bind(listCurrent.visibleProperty());

        listHistory.visibleProperty().bind(Bindings.isNotEmpty(filteredHistory));
        listHistory.managedProperty().bind(listHistory.visibleProperty());

        // Keyed by identity: a repeated course with identical details must still get a button each.
        Map<PrescriptionSummary, Button> buttonCurrent = new IdentityHashMap<>();
        Map<PrescriptionSummary, Button> buttonHistory = new IdentityHashMap<>();

        for (PrescriptionSummary prescription : sortedCurrent) {
            String left = prescription.name();
            String right = TrackingDates.display(prescription.startDate());
            Button button = view.getEntryButton(left, right);
            button.setOnAction(e -> nav.goToPage(getDetailsPage(prescription), "Prescription", false));
            buttonCurrent.put(prescription, button);
            listCurrent.getChildren().add(button);
        }

        for (PrescriptionSummary prescription : sortedHistory) {
            String left = prescription.name();
            String right = TrackingDates.display(prescription.startDate());
            Button button = view.getEntryButton(left, right);
            button.setOnAction(e -> nav.goToPage(getDetailsPage(prescription), "Prescription", false));
            buttonHistory.put(prescription, button);
            listHistory.getChildren().add(button);
        }

        // Redraw from the sorted views only: each wraps its filtered list, so it also fires when the search
        // or dates change. Redrawing from the filtered lists as well undid the chosen sort order.
        sortedCurrent.addListener((ListChangeListener<PrescriptionSummary>) c ->
                listCurrent.getChildren().setAll(sortedCurrent.stream().map(buttonCurrent::get).toList()));

        sortedHistory.addListener((ListChangeListener<PrescriptionSummary>) c ->
                listHistory.getChildren().setAll(sortedHistory.stream().map(buttonHistory::get).toList()));

        HBox current = sectionLabel("CURRENT");
        HBox history = sectionLabel("PAST");

        current.visibleProperty().bind(Bindings.isNotEmpty(filteredCurrent));
        current.managedProperty().bind(current.visibleProperty());

        history.visibleProperty().bind(Bindings.isNotEmpty(filteredHistory));
        history.managedProperty().bind(history.visibleProperty());

        /* ----SEARCH SORT FILTER----  */

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
            private void updateFilteredPrescriptions() {
                filteredCurrent.setPredicate(this::filterPredicate);
                filteredHistory.setPredicate(this::filterPredicate);
            }
            private void updateSortedPrescriptions() {
                String val = sort.getValue();
                sortedCurrent.setComparator(getComparator(val));
                sortedHistory.setComparator(getComparator(val));
            }

            private Boolean filterPredicate(PrescriptionSummary pres) {
                String searchText = search.getText() == null ? "" : search.getText().toLowerCase();
                LocalDate dateFrom = from.getValue();
                LocalDate dateTo = to.getValue();

                return getPredicate(searchText, dateFrom, dateTo).test(pres);
            }
        }
        Button clearButton = UI.clearButton("Clear filters");
        clearButton.setOnAction(e -> {from.setValue(null); to.setValue(null); search.clear(); sort.getSelectionModel().clearSelection();});

        from.valueProperty().addListener((obs, oldVal, newVal) -> new Update().updateFilteredPrescriptions());
        to.valueProperty().addListener((obs, oldVal, newVal) -> new Update().updateFilteredPrescriptions());
        search.textProperty().addListener((obs, oldVal, newVal) -> new Update().updateFilteredPrescriptions());
        sort.valueProperty().addListener((obs, oldVal, newVal) -> new Update().updateSortedPrescriptions());

        VBox noMatches = UI.emptyState(UI.ICON_SEARCH, "No matches", "Nothing matches your search or dates. Try clearing the filters.");
        noMatches.visibleProperty().bind(Bindings.isEmpty(filteredCurrent).and(Bindings.isEmpty(filteredHistory)));
        noMatches.managedProperty().bind(noMatches.visibleProperty());

        ScrollPane sp = new ScrollPane(UI.pageTop(searchBox, filterGrid, clearButton, current, listCurrent, history, listHistory, noMatches));
        sp.setFitToWidth(true);
        return sp;
    }

    private static HBox sectionLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section-label");
        HBox row = new HBox(l);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setPadding(new javafx.geometry.Insets(6, 2, 0, 2));
        return row;
    }

    public Predicate<PrescriptionSummary> getPredicate(String searchText, LocalDate dateFrom, LocalDate dateTo) {
        return pres -> {
            //uses both start and end date - so long as active time overlaps search it will appear
            LocalDate startDate = TrackingDates.parse(pres.startDate());
            //a missing end date represents long-term ongoing medication
            LocalDate endDate = TrackingDates.parse(pres.endDate());

            boolean afterFrom = endDate == null || dateFrom == null || !endDate.isBefore(dateFrom);
            boolean beforeTo = startDate == null || dateTo == null || !startDate.isAfter(dateTo);
            boolean matchesSearch = getSearchableText(pres).toLowerCase().contains(searchText);

            return afterFrom && beforeTo && matchesSearch;
        };
    }

    public Comparator<PrescriptionSummary> getComparator(String val) {
        // No selection now means newest first, like the other tracking lists (it used to sort oldest first).
        if (val == null) {
            return TrackingDates.newestFirst(PrescriptionSummary::startDate);
        }
        return switch (val) {
            case "Date (Oldest)" -> TrackingDates.oldestFirst(PrescriptionSummary::startDate);
            case "Name (A-Z)" -> TrackingDates.byName(PrescriptionSummary::name);
            default -> TrackingDates.newestFirst(PrescriptionSummary::startDate);
        };
    }

    /**
     * The end of the course for the details card. A finished course whose end date has not come
     * (the clinic stopped it early) says "Stopped early" rather than "Finished" next to a future date.
     */
    static String endText(PrescriptionSummary prescription, LocalDate today) {
        LocalDate end = TrackingDates.parse(prescription.endDate());
        if (!prescription.active() && (end == null || !end.isBefore(today))) {
            return "Stopped early";
        }
        if (end == null) {
            return "Ongoing";
        }
        return TrackingDates.display(prescription.endDate());
    }

    @Override
    public Node getDetailsPage(TrackingData td) {
        PrescriptionSummary prescription = (PrescriptionSummary) td;

        HBox graphic = view.getEntryHeader(prescription.name(), "");
        // Same coloured pill as a test result's status (it was plain text here).
        Label status = prescription.active() ? statusPill("Current", "current") : statusPill("Finished", "finished");
        graphic.getChildren().set(graphic.getChildren().size() - 1, status);
        graphic.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        HBox doseRow = new HBox(16, UI.detail("DOSE", prescription.dosage()), UI.detail("HOW OFTEN", prescription.getCourse()));
        HBox dateRow = new HBox(16, UI.detail("STARTED", TrackingDates.display(prescription.startDate())),
                UI.detail(prescription.active() ? "ENDS" : "ENDED", endText(prescription, LocalDate.now())));
        for (HBox row : List.of(doseRow, dateRow)) {
            for (Node n : row.getChildren()) {
                HBox.setHgrow(n, Priority.ALWAYS);
                ((VBox) n).setMaxWidth(Double.MAX_VALUE);
                ((VBox) n).setPrefWidth(1);
            }
        }
        VBox info = new VBox(12, doseRow, dateRow);

        List<String> lines = prescription.getInstructionLines();
        if (!lines.isEmpty()) {
            Label instructionsLabel = new Label("INSTRUCTIONS");
            instructionsLabel.getStyleClass().add("detail-label");
            VBox instructions = new VBox(2, instructionsLabel);
            if (lines.size() != 1) {
                for (String line : lines) {
                    HBox hbox = bullet(line);
                    instructions.getChildren().add(hbox);
                }
            } else {
                instructions.getChildren().add(UI.body(lines.getFirst()));
            }
            info.getChildren().add(instructions);
        }

        if (prescription.active()) {
            Button bookingReminderButton = UI.shortcutButton("Dose reminders");
            bookingReminderButton.setOnAction(e -> nav.goToPageNewTab("reminders", new RemindersView(Data.getCurrentUserId()).getRemindersScene("prescriptions"), "Reminders", true));
            HBox reminder = UI.callout("A reminder makes it easier to take every dose on time.", bookingReminderButton);

            return withTrackingStyles(UI.page(UI.card(12, graphic, info), reminder));
        } else {
            return withTrackingStyles(UI.page(UI.card(12, graphic, info)));
        }
    }
}
