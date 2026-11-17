package view;

import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import model.AvailableTimeslot;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.function.Consumer;

public class DateTimeSelector extends VBox {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final DatePicker datePicker = new DatePicker();
    private final ComboBox<AvailableTimeslot> timeCombo = new ComboBox<>();
    private final Label message = new Label();

    public DateTimeSelector(TimeslotProvider provider,
                            Consumer<AvailableTimeslot> onSelect) {
        setSpacing(10);
        Set<LocalDate> availableDates = provider.getAvailableDates();
        LocalDate first = availableDates.stream()
                .sorted()
                .findFirst()
                .orElse(null);
        datePicker.setValue(first);
        datePicker.setShowWeekNumbers(false);

        var firstSlots = provider.getTimeslots(first);

        if (!firstSlots.isEmpty()) {
            timeCombo.getItems().addAll(firstSlots);
        }

        // Restrict dates
        datePicker.setDayCellFactory(dp -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);

                if (empty || date == null
                        || date.isBefore(LocalDate.now())
                        || !availableDates.contains(date)) {

                    setDisable(true);
                    setStyle("-fx-background-color: #c1c1c1;");
                }
            }
        });

        datePicker.valueProperty().addListener((obs, oldVal, newVal) -> {
            timeCombo.getItems().clear();
            timeCombo.setValue(null);
            message.setText("");

            if (newVal == null) return;

            var slots = provider.getTimeslots(newVal);

            if (!slots.isEmpty()) {
                timeCombo.getItems().addAll(slots);
            } else {
                message.setText("No available slots.");
            }
        });

        timeCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(AvailableTimeslot slot) {
                if (slot == null) return "";
                return slot.startTime().format(TIME_FMT)
                        + " - "
                        + slot.endTime().format(TIME_FMT);
            }

            @Override
            public AvailableTimeslot fromString(String string) {
                return null;
            }
        });

        timeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                message.setText("Selected: "
                        + newVal.startTime().format(TIME_FMT)
                        + " - "
                        + newVal.endTime().format(TIME_FMT));

                onSelect.accept(newVal);
            } else {
                message.setText("");
            }
        });

        getChildren().addAll(
                new Label("Select date"),
                datePicker,
                new Label("Available time slots"),
                timeCombo,
                message
        );
    }

    public LocalDate getSelectedDate() {
        return datePicker.getValue();
    }

    public AvailableTimeslot getSelectedSlot() {
        return timeCombo.getValue();
    }

    public ComboBox<AvailableTimeslot> getTimeCombo() {return timeCombo;}
}
