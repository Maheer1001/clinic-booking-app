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
import java.util.HashMap;
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
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yy");
        text += pres.endDate().isEmpty() || LocalDate.parse(pres.endDate(), formatter).isAfter(LocalDate.now()) ? " active current" : " inactive past history";
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

        if (prescriptionsCurrent.isEmpty() && prescriptionsHistory.isEmpty()) {
            return UI.page(UI.muted("You have no prescriptions."));
        }

        VBox listCurrent = new VBox(10);
        listCurrent.setAlignment(Pos.CENTER);

        VBox listHistory = new VBox(10);
        listHistory.setAlignment(Pos.CENTER);

        listCurrent.visibleProperty().bind(Bindings.isNotEmpty(filteredCurrent));
        listCurrent.managedProperty().bind(listCurrent.visibleProperty());

        listHistory.visibleProperty().bind(Bindings.isNotEmpty(filteredHistory));
        listHistory.managedProperty().bind(listHistory.visibleProperty());

        Map<PrescriptionSummary, Button> buttonCurrent = new HashMap<>();
        Map<PrescriptionSummary, Button> buttonHistory = new HashMap<>();

        for (PrescriptionSummary prescription : sortedCurrent) {
            String left = prescription.name();
            String right = prescription.startDate();
            Button button = view.getEntryButton(left, right);
            button.setOnAction(e -> nav.goToPage(getDetailsPage(prescription), "View Prescription Details", false));
            buttonCurrent.put(prescription, button);
            listCurrent.getChildren().add(button);
        }

        for (PrescriptionSummary prescription : sortedHistory) {
            String left = prescription.name();
            String right = prescription.startDate();
            Button button = view.getEntryButton(left, right);
            button.setOnAction(e -> nav.goToPage(getDetailsPage(prescription), "View Prescription Details", false));
            buttonHistory.put(prescription, button);
            listHistory.getChildren().add(button);
        }

        filteredCurrent.addListener((ListChangeListener<PrescriptionSummary>) c ->
                listCurrent.getChildren().setAll(filteredCurrent.stream().map(buttonCurrent::get).toList()));

        sortedCurrent.addListener((ListChangeListener<PrescriptionSummary>) c ->
                listCurrent.getChildren().setAll(sortedCurrent.stream().map(buttonCurrent::get).toList()));

        filteredHistory.addListener((ListChangeListener<PrescriptionSummary>) c ->
                listHistory.getChildren().setAll(filteredHistory.stream().map(buttonHistory::get).toList()));

        sortedHistory.addListener((ListChangeListener<PrescriptionSummary>) c ->
                listHistory.getChildren().setAll(sortedHistory.stream().map(buttonHistory::get).toList()));

        HBox current = UI.separator("  Current  ");
        HBox history = UI.separator("  History  ");

        current.visibleProperty().bind(Bindings.isNotEmpty(filteredCurrent));
        current.managedProperty().bind(current.visibleProperty());

        history.visibleProperty().bind(Bindings.isNotEmpty(filteredHistory));
        history.managedProperty().bind(history.visibleProperty());

        /* ----SEARCH SORT FILTER----  */

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
        Button clearButton = UI.clearButton("Clear All");
        clearButton.setOnAction(e -> {from.setValue(null); to.setValue(null); search.clear(); sort.getSelectionModel().clearSelection();});

        from.valueProperty().addListener((obs, oldVal, newVal) -> new Update().updateFilteredPrescriptions());
        to.valueProperty().addListener((obs, oldVal, newVal) -> new Update().updateFilteredPrescriptions());
        search.textProperty().addListener((obs, oldVal, newVal) -> new Update().updateFilteredPrescriptions());
        sort.valueProperty().addListener((obs, oldVal, newVal) -> new Update().updateSortedPrescriptions());

        ScrollPane sp = new ScrollPane(UI.pageTop(searchBox, filterGrid, clearButton, current, listCurrent, history, listHistory));
        sp.setFitToWidth(true);
        return sp;
    }

    public Predicate<PrescriptionSummary> getPredicate(String searchText, LocalDate dateFrom, LocalDate dateTo) {
        return pres -> {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yy");

            //uses both start and end date - so long as active time overlaps search it will appear
            String startDateStr = pres.startDate();
            String endDateStr = pres.endDate();
            LocalDate startDate = LocalDate.parse(startDateStr, formatter);

            //to control for null values representing long-term ongoing medication
            boolean afterFrom;
            if (endDateStr.isEmpty()) {
                afterFrom = true;
            } else {
                LocalDate endDate = LocalDate.parse(endDateStr, formatter);
                afterFrom = (dateFrom == null) || !endDate.isBefore(dateFrom);
            }

            boolean beforeTo = (dateTo == null) || !startDate.isAfter(dateTo);
            boolean matchesSearch = getSearchableText(pres).toLowerCase().contains(searchText);

            return afterFrom && beforeTo && matchesSearch;
        };
    }

    public Comparator<PrescriptionSummary> getComparator(String val) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yy");
        if (val == null) {
            return Comparator.comparing((PrescriptionSummary pres) -> LocalDate.parse(pres.startDate(), formatter));
        } else {
            return switch (val) {
                case "Date (Oldest)" -> Comparator.comparing(pres -> LocalDate.parse(pres.startDate(), formatter));
                case "Name (A-Z)" -> Comparator.comparing(PrescriptionSummary::name);
                default -> Comparator.comparing((PrescriptionSummary pres) -> LocalDate.parse(pres.startDate(), formatter)).reversed();
            };
        }
    }

    @Override
    public Node getDetailsPage(TrackingData td) {
        PrescriptionSummary prescription = (PrescriptionSummary) td;

        HBox graphic = (HBox) view.getEntryButton(prescription.name(), prescription.startDate()).getGraphic();

        VBox info = new VBox(10,
                new VBox(UI.heading("Dosage:"),
                UI.body(prescription.dosage())),
                new VBox(UI.heading("Course:"),
                UI.body(prescription.getCourse())),
                new VBox(UI.heading("Start Date:"),
                UI.body(prescription.startDate())),
                new VBox(UI.heading("End Date:"),
                UI.body(prescription.endDate() == null || prescription.endDate().isEmpty() ? "Indefinite" : prescription.endDate()))
        );

        List<String> lines = prescription.getInstructionLines();
        if (!lines.isEmpty()) {
            VBox instructions = new VBox(UI.heading("Instructions:"));
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
            Label bookingReminderLabel = UI.body("Remember to take your prescription on-time.");
            bookingReminderLabel.setWrapText(true);

            Button bookingReminderButton = UI.shortcutButton("Manage treatment reminders");
            bookingReminderButton.setOnAction(e -> nav.goToPageNewTab("reminders", new RemindersView(Data.getCurrentUserId()).getRemindersScene("prescriptions"), "Reminders", true));

            HBox reminder = UI.equalSpacing(new HBox(10, bookingReminderLabel, bookingReminderButton));
            reminder.getStyleClass().add(UI.PAGE);

            return UI.page(UI.card(10, graphic, info), reminder);
        } else {
            return UI.page(UI.card(10, graphic, info));
        }
    }
}
