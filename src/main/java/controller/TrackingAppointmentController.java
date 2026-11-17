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
import model.AppointmentDetails;
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
 * Handles tracking appointments by building pages and
 * controlling search sort filter logic
 */
public class TrackingAppointmentController extends TrackingSectionController {

    public TrackingAppointmentController(Navigator nav, TrackingView view, TrackingService model) {
        super(nav, view, model);
    }

    @Override
    public String getSearchableText(TrackingData td) {
        AppointmentDetails app = (AppointmentDetails) td;
        // notes may be null; don't let the word "null" become searchable
        return app.serviceName() + " " + app.clinicName() + " " + (app.notes() != null ? app.notes() : "");
    }

    @Override
    public Node getMenuPage() {
        ObservableList<AppointmentDetails> appointments = FXCollections.observableList(model.getPastAppointments());
        FilteredList<AppointmentDetails> filteredAppointments = new FilteredList<>(appointments, p -> true);
        SortedList<AppointmentDetails> sortedAppointments = new SortedList<>(filteredAppointments);
        sortedAppointments.setComparator(getComparator(null));
        if (appointments.isEmpty()) {
            return UI.page(UI.muted("You have no past appointments."));
        }
        VBox list = new VBox(10);
        list.setAlignment(Pos.CENTER);

        Map<AppointmentDetails, Button> buttonMap = new HashMap<>();

        for (AppointmentDetails appointment : sortedAppointments) {
            String left = appointment.serviceName();
            String right = when(appointment);
            Button button = view.getEntryButton(left, right);
            button.setOnAction(e -> nav.goToPage(getDetailsPage(appointment), "View Appointment Details", false));
            buttonMap.put(appointment, button);
            list.getChildren().add(button);
        }

        filteredAppointments.addListener((ListChangeListener<AppointmentDetails>) c ->
                list.getChildren().setAll(filteredAppointments.stream().map(buttonMap::get).toList()));

        sortedAppointments.addListener((ListChangeListener<AppointmentDetails>) c ->
                list.getChildren().setAll(sortedAppointments.stream().map(buttonMap::get).toList()));

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
            private void updateFilteredAppointments() {
                String searchText = search.getText() == null ? "" : search.getText().toLowerCase();
                LocalDate dateFrom = from.getValue();
                LocalDate dateTo = to.getValue();
                filteredAppointments.setPredicate(getPredicate(searchText, dateFrom, dateTo));
            }
            private void updateSortedAppointments() {
                sortedAppointments.setComparator(getComparator(sort.getValue()));
            }
        }
        Button clearButton = UI.clearButton("Clear All");
        clearButton.setOnAction(e -> {from.setValue(null); to.setValue(null); search.clear(); sort.getSelectionModel().clearSelection();});

        from.valueProperty().addListener((obs, oldVal, newVal) -> new Update().updateFilteredAppointments());
        to.valueProperty().addListener((obs, oldVal, newVal) -> new Update().updateFilteredAppointments());
        search.textProperty().addListener((obs, oldVal, newVal) -> new Update().updateFilteredAppointments());
        sort.valueProperty().addListener((obs, oldVal, newVal) -> new Update().updateSortedAppointments());


        ScrollPane sp = new ScrollPane(UI.pageTop(searchBox, filterGrid, clearButton, list, noMatchesLabel(filteredAppointments)));
        sp.setFitToWidth(true);
        return sp;
    }

    public Predicate<AppointmentDetails> getPredicate(String searchText, LocalDate dateFrom, LocalDate dateTo) {
        return app -> {
            LocalDate date = TrackingDates.parse(app.date());
            boolean matchesSearch = getSearchableText(app).toLowerCase().contains(searchText);
            return TrackingDates.inRange(date, dateFrom, dateTo) && matchesSearch;
        };
    }

    public Comparator<AppointmentDetails> getComparator(String val) {
        if (val == null) {
            return TrackingDates.newestFirst(AppointmentDetails::date);
        }
        return switch (val) {
            case "Date (Oldest)" -> TrackingDates.oldestFirst(AppointmentDetails::date);
            case "Name (A-Z)" -> TrackingDates.byName(AppointmentDetails::serviceName);
            default -> TrackingDates.newestFirst(AppointmentDetails::date);
        };
    }

    /** "5 Mar 2026, 14:00" instead of "14:00 05/03/26". */
    private static String when(AppointmentDetails app) {
        String time = app.startTime() == null || app.startTime().isBlank() ? "" : ", " + app.startTime();
        return TrackingDates.display(app.date()) + time;
    }

    @Override
    public Node getDetailsPage(TrackingData td) {
        AppointmentDetails appointment = (AppointmentDetails) td;
        HBox graphic = (HBox) view.getEntryButton(appointment.serviceName(), when(appointment)).getGraphic();

        VBox info = new VBox(
                UI.heading("Clinic:"),
                UI.body(appointment.clinicName())
        );

        List<String> lines = appointment.getNotesLines();
        if (!lines.isEmpty()) {
            info.getChildren().add(UI.heading("Notes:"));
            if (lines.size() > 1) {
                for (String line : lines) {
                    HBox hbox = bullet(line);
                    info.getChildren().add(hbox);
                }
            } else {
                info.getChildren().add(UI.body(lines.getFirst()));
            }
        }

        Label bookingReminderLabel = UI.body("Keep on top of your sexual health by booking regular appointments.");
        bookingReminderLabel.setWrapText(true);

        Button bookingReminderButton = UI.shortcutButton("Manage booking reminders");
        bookingReminderButton.setOnAction(e -> nav.goToPageNewTab("reminders", new RemindersView(Data.getCurrentUserId()).getRemindersScene("booking"), "Reminders", true));

        HBox reminder = UI.equalSpacing(new HBox(10, bookingReminderLabel, bookingReminderButton));
        reminder.getStyleClass().add(UI.PAGE);

        return UI.page(UI.card(10, graphic, info), reminder);
    }
}
