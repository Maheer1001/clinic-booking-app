package view;

import app.Data;
import controller.ViewAppointmentController;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import model.AvailableTimeslot;
import model.BookingCommunicationPreferences;
import model.ReminderTemplateOverrides;
import model.UpcomingAppointmentDetails;
import service.AsyncNotifier;
import service.BookingCommunicationPrefs;
import service.NotificationDispatcher;
import service.email.RescheduleConfirmationEmail;
import view.notification.RescheduleConfirmationNotification;
import view.ui.Dates;
import view.ui.UI;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class Reschedule {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public static Node getPageWithAppId(Navigator nav, int appId) {
        return buildPage(nav, String.valueOf(appId));
    }

    public static Node getPage(Navigator nav) {
        return buildPage(nav, "");
    }

    private static Label sectionHeading(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section-heading");
        return l;
    }

    private static Node buildPage(Navigator nav, String initialAppId) {

        int userId = Data.getCurrentUserId();
        if (userId < 0) {
            return UI.page(UI.muted("Please log in to reschedule."));
        }

        ViewAppointmentController controller = new ViewAppointmentController(userId);
        NotificationDispatcher dispatcher = new NotificationDispatcher();
        boolean prefilled = !initialAppId.isBlank();

        TextField appointmentId = new TextField(initialAppId);
        appointmentId.setPromptText("Appointment ID");
        Label idLabel = new Label("Appointment ID");
        idLabel.setLabelFor(appointmentId);

        Label result = UI.error("");
        result.setWrapText(true);
        result.managedProperty().bind(result.textProperty().isNotEmpty());
        result.visibleProperty().bind(result.textProperty().isNotEmpty());

        Button rescheduleBtn = UI.primaryButton("Move appointment");
        rescheduleBtn.setDisable(true);

        VBox currentBox = new VBox(10);
        VBox selectorBox = new VBox();

        final UpcomingAppointmentDetails[] currentApt = new UpcomingAppointmentDetails[1];
        final DateTimeSelector[] selectorRef = new DateTimeSelector[1];

        Runnable loadAppointment = () -> {
            result.setText("");
            rescheduleBtn.setDisable(true);
            currentApt[0] = null;
            selectorRef[0] = null;
            currentBox.getChildren().clear();
            selectorBox.getChildren().clear();

            String raw = appointmentId.getText() != null ? appointmentId.getText().trim() : "";
            if (raw.isEmpty()) return;

            int id;
            try {
                id = Integer.parseInt(raw);
            } catch (NumberFormatException ex) {
                result.setText("Please enter a valid appointment ID.");
                return;
            }

            Optional<UpcomingAppointmentDetails> aptOpt = controller.getAppointmentDetails(id);
            if (aptOpt.isEmpty()) {
                result.setText("No upcoming appointment with that ID was found.");
                return;
            }
            UpcomingAppointmentDetails apt = aptOpt.get();
            currentApt[0] = apt;
            java.time.LocalDate currentDate = Dates.parseStored(apt.date());
            currentBox.getChildren().setAll(
                    UI.detailTitle(apt.serviceName()),
                    UI.detail("CURRENTLY", (currentDate != null ? Dates.full(currentDate) : apt.date())
                            + ", " + Dates.timeRange(apt.startTime(), apt.endTime())),
                    UI.detail("CLINIC", apt.clinicName()));

            TimeslotProvider provider = new TimeslotProvider() {
                @Override
                public Set<LocalDate> getAvailableDates() {
                    return new HashSet<>(controller.getAvailableDatesForReschedule(id));
                }

                @Override
                public List<AvailableTimeslot> getTimeslots(LocalDate date) {
                    return controller.getAvailableTimeslotsForReschedule(id, date);
                }
            };
            DateTimeSelector sel = new DateTimeSelector(provider, slot -> rescheduleBtn.setDisable(false));
            sel.getTimeCombo().valueProperty().addListener((obs, o, n) -> rescheduleBtn.setDisable(n == null));
            selectorRef[0] = sel;
            selectorBox.getChildren().setAll(UI.card(10, sectionHeading("Choose a new time"), sel));
        };

        appointmentId.textProperty().addListener((obs, oldVal, newVal) -> loadAppointment.run());
        // If the appointment ID is prefilled, the listener won't fire, so load once here.
        loadAppointment.run();

        rescheduleBtn.setOnAction(e -> {
            result.setText("");
            UpcomingAppointmentDetails apt = currentApt[0];
            if (apt == null) {
                result.setText("No upcoming appointment with that ID was found.");
                return;
            }
            AvailableTimeslot slot = selectorRef[0] != null ? selectorRef[0].getSelectedSlot() : null;
            if (slot == null) {
                result.setText("Please choose a new date and time.");
                return;
            }

            rescheduleBtn.setDisable(true); // stop a double click from moving the appointment twice
            int id = apt.appId();
            String oldDate = apt.date() + " " + apt.startTime() + " - " + apt.endTime();

            if (!controller.rescheduleToSlot(id, slot)) {
                result.setText("Sorry, that time is no longer available. Please choose another.");
                loadAppointment.run(); // refresh the available times
                return;
            }

            String email = Data.getCurrentUserEmail();
            String name = Data.getCurrentUserName() != null ? Data.getCurrentUserName() : "Patient";
            String newDateStr = slot.date().format(DATE_FMT) + " "
                    + slot.startTime().format(TIME_FMT) + " - "
                    + slot.endTime().format(TIME_FMT);

            BookingCommunicationPreferences commPrefs = BookingCommunicationPrefs.load(userId);
            boolean rescheduleDiscreet = commPrefs.rescheduleDiscreet();
            ReminderTemplateOverrides rescheduleTpl = commPrefs.rescheduleTemplate();
            boolean hasEmail = email != null && !email.isBlank();

            AsyncNotifier.notifyUser(
                    dispatcher,
                    userId,
                    hasEmail ? new RescheduleConfirmationEmail(
                            email, name, "APP-" + id, apt.serviceName(), apt.clinicName(),
                            oldDate, newDateStr, rescheduleDiscreet, rescheduleTpl) : null,
                    new RescheduleConfirmationNotification(
                            name, "APP-" + id, apt.serviceName(), apt.clinicName(),
                            oldDate, newDateStr, rescheduleDiscreet, rescheduleTpl)
            );
            // Leave the page whether or not the user has an email address.
            nav.clearTabStack("booking");
            nav.goToHome();
        });

        VBox page = UI.page();
        if (!prefilled) {
            page.getChildren().addAll(idLabel, appointmentId);
        }
        VBox currentCard = UI.card(10, currentBox);
        // No empty card before an appointment is loaded.
        currentCard.visibleProperty().bind(Bindings.isNotEmpty(currentBox.getChildren()));
        currentCard.managedProperty().bind(currentCard.visibleProperty());
        page.getChildren().addAll(currentCard, selectorBox, result, rescheduleBtn);
        if (currentApt[0] == null && prefilled && result.getText().isEmpty()) {
            result.setText("No upcoming appointment with that ID was found.");
        }
        return page;
    }
}
