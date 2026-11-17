package view;

import app.Data;
import controller.ViewAppointmentController;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import model.AvailableTimeslot;
import model.BookingCommunicationPreferences;
import model.ReminderTemplateOverrides;
import model.UpcomingAppointmentDetails;
import service.BookingCommunicationPrefs;
import service.NotificationDispatcher;
import service.email.RescheduleConfirmationEmail;
import view.DateTimeSelector;
import view.TimeslotProvider;
import view.notification.RescheduleConfirmationNotification;
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

    private static Node buildPage(Navigator nav, String initialAppId) {

        int userId = Data.getCurrentUserId();
        if (userId < 0) {
            return UI.page(UI.muted("Please log in to reschedule."));
        }

        ViewAppointmentController controller = new ViewAppointmentController(userId);

        TextField appointmentId = new TextField(initialAppId);
        appointmentId.setPromptText("Appointment ID");

        Label result = new Label();

        Button rescheduleBtn = UI.primaryButton("Reschedule");
        rescheduleBtn.setDisable(true);

        final UpcomingAppointmentDetails[] currentApt = new UpcomingAppointmentDetails[1];
        final DateTimeSelector[] selectorRef = new DateTimeSelector[1];
        VBox selectorBox = new VBox();

        TimeslotProvider provider = new TimeslotProvider() {

            @Override
            public Set<LocalDate> getAvailableDates() {
                try {
                    int id = Integer.parseInt(appointmentId.getText().trim());
                    return new HashSet<>(controller.getAvailableDatesForReschedule(id));
                } catch (Exception e) {
                    return Set.of();
                }
            }

            @Override
            public List<AvailableTimeslot> getTimeslots(LocalDate date) {
                try {
                    int id = Integer.parseInt(appointmentId.getText().trim());
                    return controller.getAvailableTimeslotsForReschedule(id, date);
                } catch (Exception e) {
                    return List.of();
                }
            }
        };

        Runnable rebuildSelector = () -> {
            DateTimeSelector sel = new DateTimeSelector(provider, slot -> {
                var booking = Data.getBooking();
                if (booking != null) {
                    booking.setTimeslotId(slot.timeslotId());
                    booking.setDate(slot.date());
                    booking.setTime(slot.startTime());
                }
                rescheduleBtn.setDisable(false);
            });
            selectorRef[0] = sel;
            selectorBox.getChildren().setAll(sel);
        };

        rebuildSelector.run();

        Runnable loadAppointmentAndRefresh = () -> {
            result.setText("");
            rescheduleBtn.setDisable(true);
            currentApt[0] = null;

            String raw = appointmentId.getText() != null ? appointmentId.getText().trim() : "";
            if (raw.isEmpty()) {
                rebuildSelector.run();
                return;
            }

            try {
                int id = Integer.parseInt(raw);
                Optional<UpcomingAppointmentDetails> aptOpt = controller.getAppointmentDetails(id);
                if (aptOpt.isEmpty()) {
                    rebuildSelector.run();
                    return;
                }

                currentApt[0] = aptOpt.get();
                rebuildSelector.run();
            } catch (Exception ignored) {
                rebuildSelector.run();
            }
        };

        appointmentId.textProperty().addListener((obs, oldVal, newVal) -> loadAppointmentAndRefresh.run());

        // Important: if the appointment ID is prefilled, the listener won't fire — load once here.
        loadAppointmentAndRefresh.run();

        DateTimeSelector selector = selectorRef[0];
        // Keep a stable node reference for the UI tree.
        // (selectorRef[0] holds the current selector instance.)
        // selectorBox will be swapped as the appointment ID changes.
        //
        // NOTE: do not use `selector` after this point; use selectorRef[0].
        @SuppressWarnings("unused")
        DateTimeSelector ignore = selector;

        // legacy block (kept for minimal diff) removed by rebuildSelector/loadAppointmentAndRefresh
        /*
        DateTimeSelector selector = new DateTimeSelector(provider, slot -> {

            var booking = Data.getBooking();
            if (booking != null) {
                booking.setTimeslotId(slot.timeslotId());
                booking.setDate(slot.date());
                booking.setTime(slot.startTime());
            }

            rescheduleBtn.setDisable(false);
        });
        */

        NotificationDispatcher dispatcher = new NotificationDispatcher();

        rescheduleBtn.setOnAction(e -> {

            result.setText("");

            try {
                int id = Integer.parseInt(appointmentId.getText().trim());

                if (currentApt[0] == null) {
                    result.setText("Appointment not found.");
                    return;
                }

                AvailableTimeslot slot = selectorRef[0] != null ? selectorRef[0].getSelectedSlot() : null;
                LocalDate date = selectorRef[0] != null ? selectorRef[0].getSelectedDate() : null;

                if (slot == null || date == null) {
                    result.setText("Please select a valid date and time.");
                    return;
                }

                String oldDate = currentApt[0].date() + " "
                        + currentApt[0].startTime() + " - "
                        + currentApt[0].endTime();

                boolean success =
                        controller.rescheduleToNewTimeslot(id, slot.timeslotId());

                if (!success) {
                    result.setText("Reschedule failed.");
                    return;
                }

                result.setText("Rescheduled successfully.");

                String email = Data.getCurrentUserEmail();
                String name = Data.getCurrentUserName() != null
                        ? Data.getCurrentUserName()
                        : "Patient";

                if (email != null && !email.isBlank()) {

                    String newDateStr =
                            slot.date().format(DATE_FMT) + " "
                                    + slot.startTime().format(TIME_FMT)
                                    + " - "
                                    + slot.endTime().format(TIME_FMT);

                    BookingCommunicationPreferences commPrefs =
                            BookingCommunicationPrefs.load(Data.getCurrentUserId());
                    boolean rescheduleDiscreet = commPrefs.rescheduleDiscreet();
                    ReminderTemplateOverrides rescheduleTpl = commPrefs.rescheduleTemplate();

                    dispatcher.notifyUser(
                            Data.getCurrentUserId(),
                            new RescheduleConfirmationEmail(
                                    email,
                                    name,
                                    "APP-" + id,
                                    currentApt[0].serviceName(),
                                    currentApt[0].clinicName(),
                                    oldDate,
                                    newDateStr,
                                    rescheduleDiscreet,
                                    rescheduleTpl
                            ),
                            new RescheduleConfirmationNotification(
                                    name,
                                    "APP-" + id,
                                    currentApt[0].serviceName(),
                                    currentApt[0].clinicName(),
                                    oldDate,
                                    newDateStr,
                                    rescheduleDiscreet,
                                    rescheduleTpl
                            )
                    );
                nav.clearTabStack("booking");
                nav.goToHome();
                }

            } catch (Exception ex) {
                result.setText("Invalid appointment ID.");
            }
        });

        return UI.page(
                new Label("Appointment ID"), appointmentId,
                selectorBox,
                rescheduleBtn,
                result
        );
    }
}