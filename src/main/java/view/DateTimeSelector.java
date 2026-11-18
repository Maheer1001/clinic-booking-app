package view;

import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import model.AvailableTimeslot;
import service.SlotSchedule;
import view.ui.UI;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class DateTimeSelector extends VBox {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEEE d MMMM", java.util.Locale.UK);

    private final DatePicker datePicker = new DatePicker();
    private final ComboBox<AvailableTimeslot> timeCombo = new ComboBox<>();
    private final Label message = UI.muted("");

    public DateTimeSelector(TimeslotProvider provider,
                            Consumer<AvailableTimeslot> onSelect) {
        setSpacing(10);
        Set<LocalDate> availableDates = provider.getAvailableDates();
        LocalDate first = availableDates.stream()
                .sorted()
                .findFirst()
                .orElse(null);

        datePicker.setShowWeekNumbers(false);
        datePicker.setConverter(new StringConverter<>() {
            private final DateTimeFormatter shown = DateTimeFormatter.ofPattern("EEE d MMM yyyy", java.util.Locale.UK);
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : date.format(shown);
            }

            @Override
            public LocalDate fromString(String text) {
                return null; // not editable: dates are picked from the calendar
            }
        });
        // Typing a date would skip the day-cell rules below, so dates are picked from the calendar only.
        datePicker.setEditable(false);
        datePicker.setPromptText("Choose a date");
        datePicker.setMaxWidth(Double.MAX_VALUE);
        timeCombo.setPromptText("Choose a time");
        timeCombo.setMaxWidth(Double.MAX_VALUE);
        message.setWrapText(true);

        // Restrict dates. Cells are reused as the month changes, so every cell's state is set each time.
        datePicker.setDayCellFactory(dp -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || date == null
                        || date.isBefore(LocalDate.now())
                        || !availableDates.contains(date));
            }
        });

        datePicker.valueProperty().addListener((obs, oldVal, newVal) -> showSlots(provider, newVal));

        timeCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(AvailableTimeslot slot) {
                if (slot == null) return "";
                return slot.startTime().format(TIME_FMT)
                        + " – "
                        + slot.endTime().format(TIME_FMT);
            }

            @Override
            public AvailableTimeslot fromString(String string) {
                return null;
            }
        });

        timeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                message.setText(newVal.date().format(DATE_FMT) + ", "
                        + newVal.startTime().format(TIME_FMT)
                        + " – "
                        + newVal.endTime().format(TIME_FMT));
                message.getStyleClass().setAll("label", "detail-value-strong");

                onSelect.accept(newVal);
            }
        });

        Label dateLabel = new Label("Date");
        dateLabel.getStyleClass().add("form-label");
        dateLabel.setLabelFor(datePicker);
        Label timeLabel = new Label("Time");
        timeLabel.getStyleClass().add("form-label");
        timeLabel.setLabelFor(timeCombo);
        datePicker.setAccessibleText("Appointment date");
        timeCombo.setAccessibleText("Appointment time");

        getChildren().addAll(dateLabel, datePicker, timeLabel, timeCombo, message);

        if (first == null) {
            datePicker.setDisable(true);
            timeCombo.setDisable(true);
            message.setText("No appointments are available in the next "
                    + (SlotSchedule.BOOKING_WINDOW_DAYS / 7) + " weeks. Please try another clinic or check back later.");
        } else {
            datePicker.setValue(first); // fires the listener, which loads the first day's slots
        }
    }

    private void showSlots(TimeslotProvider provider, LocalDate date) {
        timeCombo.getItems().clear();
        timeCombo.setValue(null);
        message.getStyleClass().setAll("label", UI.MUTED);
        message.setText("");
        if (date == null) return;

        List<AvailableTimeslot> slots = provider.getTimeslots(date);
        if (slots.isEmpty()) {
            timeCombo.setDisable(true);
            message.setText("No times left on " + date.format(DATE_FMT) + ". Please choose another date.");
        } else {
            timeCombo.setDisable(false);
            timeCombo.getItems().addAll(slots);
            message.setText(slots.size() == 1 ? "1 time free on this day." : slots.size() + " times free on this day.");
        }
    }

    public LocalDate getSelectedDate() {
        return datePicker.getValue();
    }

    public AvailableTimeslot getSelectedSlot() {
        return timeCombo.getValue();
    }

    public ComboBox<AvailableTimeslot> getTimeCombo() {return timeCombo;}
}
