package view.reminders;

import controller.ReminderController;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import view.ui.UI;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Main Reminders screen: tabbed Appointments, Prescriptions, and booking reminders on one page.
 */
public class RemindersView {

    private final int userId;

    public RemindersView(int userId) {
        this.userId = userId;
    }

    public Node getRemindersScene(String tab) {
        ReminderController controller = new ReminderController(userId);

        ToggleGroup group = new ToggleGroup();
        ToggleButton tabAppointments = new ToggleButton("Appointments");
        ToggleButton tabPrescriptions = new ToggleButton("Prescriptions");
        ToggleButton tabBookingReminders = new ToggleButton("Booking");
        tabAppointments.setToggleGroup(group);
        tabPrescriptions.setToggleGroup(group);
        tabBookingReminders.setToggleGroup(group);
        tabAppointments.getStyleClass().add(UI.REMINDER_TAB);
        tabPrescriptions.getStyleClass().add(UI.REMINDER_TAB);
        tabBookingReminders.getStyleClass().add(UI.REMINDER_TAB);

        Node appointmentsPane = AppointmentRemindersView.createAppointmentTabBody(controller);
        Node prescriptionsPane = TreatmentRemindersView.createPrescriptionTabBody(controller);
        Node bookingPane = BookingRemindersView.createBookingReminderTabBody(controller);

        StackPane stack = new StackPane(appointmentsPane, prescriptionsPane, bookingPane);
        for (Node layer : stack.getChildren()) {
            layer.setVisible(false);
            StackPane.setAlignment(layer, Pos.TOP_CENTER);
            if (layer instanceof Region r) {
                r.setMaxWidth(Double.MAX_VALUE);
            }
        }

        Runnable syncVisibility = () -> {
            var selToggle = group.getSelectedToggle();
            ToggleButton sel = selToggle instanceof ToggleButton tb ? tb : tabAppointments;
            appointmentsPane.setVisible(sel == tabAppointments);
            prescriptionsPane.setVisible(sel == tabPrescriptions);
            bookingPane.setVisible(sel == tabBookingReminders);
        };

        AtomicBoolean suppressSubTabListeners = new AtomicBoolean(false);
        group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (!suppressSubTabListeners.get() && newToggle == null) {
                suppressSubTabListeners.set(true);
                try {
                    group.selectToggle(oldToggle != null ? oldToggle : tabAppointments);
                } finally {
                    suppressSubTabListeners.set(false);
                }
            }
            syncVisibility.run();
        });

        Region tabLead = new Region();
        Region tabTrail = new Region();
        HBox.setHgrow(tabLead, Priority.ALWAYS);
        HBox.setHgrow(tabTrail, Priority.ALWAYS);
        HBox tabRow = new HBox(8, tabLead, tabAppointments, tabPrescriptions, tabBookingReminders, tabTrail);
        tabRow.setAlignment(Pos.CENTER);
        tabRow.setMaxWidth(Double.MAX_VALUE);

        VBox root = new VBox(12, tabRow, stack);
        root.getStyleClass().add(UI.PAGE);
        VBox.setVgrow(stack, Priority.ALWAYS);

        switch (tab) {
            case "booking" -> tabBookingReminders.setSelected(true);
            case "prescriptions" -> tabPrescriptions.setSelected(true);
            default -> tabAppointments.setSelected(true);
        }
        syncVisibility.run();

        return root;
    }
}
