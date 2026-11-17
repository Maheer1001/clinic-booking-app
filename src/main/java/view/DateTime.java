package view;

import app.Data;
import controller.BookingController;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
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

        if (clinic == null || items.isEmpty()) {
            return UI.page(UI.muted("Please select clinic and service first."));
        }

        VBox container = new VBox(15);

        List<ComboBox<AvailableTimeslot>> comboBoxes = new ArrayList<>();

        for (Item item : items) {

            Service service = item.getService();
            Label serviceLabel = new Label("Service: " + service.getName());

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
                    serviceLabel,
                    selector
            ));
        }

        BooleanBinding allSelected = Bindings.createBooleanBinding(() ->
                        comboBoxes.stream().allMatch(cb -> cb.getValue() != null),
                comboBoxes.stream()
                        .map(ComboBox::valueProperty)
                        .toArray(javafx.beans.Observable[]::new)
        );

        Button next = UI.primaryButton("Next");
        next.setOnAction(e -> controller.goToDetails());
        next.disableProperty().bind(allSelected.not());
        VBox nextBox = new VBox(next);
        nextBox.setAlignment(Pos.CENTER);

        container.getChildren().add(nextBox);

        ScrollPane sp = new ScrollPane(UI.pageTop(container));
        sp.setFitToWidth(true);

        return sp;


    }
}