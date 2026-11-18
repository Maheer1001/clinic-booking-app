package view;

import app.Data;
import controller.BookingController;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import model.AvailableTimeslot;
import model.Clinic;
import model.Item;
import model.Service;
import view.ui.UI;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DateTime {

    public static Node getPage(BookingController controller) {

        Clinic clinic = Data.getBooking().getClinic();
        List<Item> items = Data.getBooking().getItems();

        if (clinic == null || items == null || items.isEmpty()) {
            return UI.page(UI.muted("Please select clinic and service first."));
        }

        VBox container = new VBox(15);
        container.getChildren().addAll(UI.steps(3, 5, "Time"), new VBox(4,
                UI.pageHeading("When suits you?"),
                UI.intro("Free times at " + clinic.getName() + ", worked out from its opening hours.")));

        List<ComboBox<AvailableTimeslot>> comboBoxes = new ArrayList<>();

        for (Item item : items) {

            Service service = item.getService();
            Label serviceLabel = UI.detailTitle(service.getName());
            Label duration = UI.muted(service.getDurationMinutes() + " minute appointment");

            int clinicId = clinic.getId();
            int serviceId = service.getId();

            TimeslotProvider provider = new TimeslotProvider() {

                @Override
                public Set<LocalDate> getAvailableDates() {
                    return new HashSet<>(
                            service.getAvailableDates(clinicId, serviceId)
                    );
                }

                @Override
                public List<AvailableTimeslot> getTimeslots(LocalDate date) {
                    return service.getAvailableTimeslots(clinicId, serviceId, date);
                }
            };

            DateTimeSelector selector = new DateTimeSelector(provider, slot -> {

                if (slot == null) return;

                item.setTimeslotId(slot.timeslotId());
                item.setDate(slot.date());
                item.setTime(slot.startTime());

                var booking = Data.getBooking();
                booking.setTimeslotId(slot.timeslotId());
                booking.setDate(slot.date());
                booking.setTime(slot.startTime());
            });

            comboBoxes.add(selector.getTimeCombo());

            container.getChildren().add(UI.card(10,
                    new VBox(2, serviceLabel, duration),
                    selector
            ));
        }

        Observable[] values = comboBoxes.stream()
                .map(ComboBox::valueProperty)
                .toArray(Observable[]::new);

        BooleanBinding allSelected = Bindings.createBooleanBinding(() ->
                        comboBoxes.stream().allMatch(cb -> cb.getValue() != null),
                values
        );

        // Two services booked for overlapping times can't both be attended.
        StringBinding clash = Bindings.createStringBinding(() -> {
            for (int i = 0; i < comboBoxes.size(); i++) {
                for (int j = i + 1; j < comboBoxes.size(); j++) {
                    AvailableTimeslot a = comboBoxes.get(i).getValue();
                    AvailableTimeslot b = comboBoxes.get(j).getValue();
                    if (a != null && b != null && overlaps(a, b)) {
                        return items.get(i).getService().getName() + " and " + items.get(j).getService().getName()
                                + " are at overlapping times. Please choose different times.";
                    }
                }
            }
            return "";
        }, values);

        Label clashMessage = UI.error("");
        clashMessage.setWrapText(true);
        clashMessage.textProperty().bind(clash);
        clashMessage.managedProperty().bind(clashMessage.textProperty().isNotEmpty());
        clashMessage.visibleProperty().bind(clashMessage.textProperty().isNotEmpty());

        Button next = UI.primaryButton("Continue");
        next.setOnAction(e -> controller.goToDetails());
        next.disableProperty().bind(allSelected.not().or(clash.isNotEmpty()));
        VBox nextBox = new VBox(10, clashMessage, next);
        nextBox.setAlignment(Pos.CENTER);

        container.getChildren().add(nextBox);

        ScrollPane sp = new ScrollPane(UI.pageTop(container));
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        return sp;
    }

    /** True if two slots on the same day share any time. */
    static boolean overlaps(AvailableTimeslot a, AvailableTimeslot b) {
        return a.date().equals(b.date())
                && a.startTime().isBefore(b.endTime())
                && b.startTime().isBefore(a.endTime());
    }
}
