package view.notification;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import model.ReminderTemplateOverrides;
import view.ui.Dates;
import view.ui.UI;

import java.time.LocalDateTime;

/**
 * Notification popup to confirm a newly booked appointment.
 */
public class BookingConfirmationNotification extends BaseNotification {

    private final String patientName;
    private final String bookingReference;
    private final String appointmentType;
    private final LocalDateTime appointmentDateTime;
    private final String clinicLocation;
    private final String doctorName;
    private final boolean discreet;
    private final ReminderTemplateOverrides templateOverrides;

    public BookingConfirmationNotification(String patientName, String bookingReference,
                                           String appointmentType, LocalDateTime appointmentDateTime,
                                           String clinicLocation, String doctorName, boolean discreet,
                                           ReminderTemplateOverrides templateOverrides) {
        this.patientName = patientName;
        this.bookingReference = bookingReference;
        this.appointmentType = appointmentType;
        this.appointmentDateTime = appointmentDateTime;
        this.clinicLocation = clinicLocation;
        this.doctorName = doctorName;
        this.discreet = discreet;
        this.templateOverrides = templateOverrides;
    }

    private ReminderTemplateOverrides ov() {
        return templateOverrides != null ? templateOverrides : ReminderTemplateOverrides.fromString("");
    }

    private String overrideNotificationText() {
        ReminderTemplateOverrides o = ov();
        if (o.getNotificationBody() != null && !o.getNotificationBody().isBlank()) {
            return o.getNotificationBody().trim();
        }
        if (o.getNotificationPreview() != null && !o.getNotificationPreview().isBlank()) {
            return o.getNotificationPreview().trim();
        }
        return null;
    }

    @Override
    public String getTitle() {
        String t = ov().getNotificationTitle();
        if (t != null && !t.isBlank()) {
            return t.trim();
        }
        return "Booking Confirmed";
    }

    @Override
    public String getMessage() {
        String custom = overrideNotificationText();
        if (custom != null) {
            return custom;
        }
        if (discreet) {
            return "Hi " + patientName + ",\n\n"
                    + "Your appointment has been confirmed. Open the app for full details.";
        }
        return "Hi " + patientName + ",\n\n"
                + "Your appointment has been confirmed:\n\n"
                + NotificationLayout.detailsText(rows());
    }

    @Override
    public String getType() {
        return "BOOKING_CONFIRMATION";
    }

    @Override
    protected Node buildPreviewContent() {
        String custom = overrideNotificationText();
        if (custom != null) {
            return NotificationLayout.custom(getTitle(), custom);
        }
        if (discreet) {
            return NotificationLayout.page(getTitle(), patientName, "Your appointment has been confirmed.",
                    null, "Open the app for full details.");
        }
        return NotificationLayout.page(getTitle(), patientName, null, NotificationLayout.detailsCard(rows()), null);
    }

    private String[] rows() {
        return new String[]{
                "Reference", bookingReference,
                "Type", appointmentType,
                "Date", Dates.full(appointmentDateTime.toLocalDate()),
                "Time", Dates.time(appointmentDateTime.toLocalTime()),
                "Location", clinicLocation,
                "Clinician", doctorName};
    }
}