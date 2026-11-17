package view.notification;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import model.ReminderTemplateOverrides;
import view.ui.UI;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
                + "Reference: " + bookingReference + "\n"
                + "Type: " + appointmentType + "\n"
                + "Date: " + appointmentDateTime.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")) + "\n"
                + "Time: " + appointmentDateTime.format(DateTimeFormatter.ofPattern("HH:mm")) + "\n"
                + "Location: " + clinicLocation + "\n"
                + "Clinician: " + doctorName;
    }

    @Override
    public String getType() {
        return "BOOKING_CONFIRMATION";
    }

    @Override
    protected Node buildPreviewContent() {
        String custom = overrideNotificationText();
        if (custom != null) {
            Label body = new Label(custom);
            body.setWrapText(true);
            return new VBox(10, UI.titleSm(getTitle()), UI.card(10, body));
        }

        Label heading = UI.titleSm(getTitle());
        Label hi = UI.body("Hi " + patientName + ",");
        if (discreet) {
            Label msg = UI.body("Your appointment has been confirmed.");
            Label hint = UI.muted("Open the app for full details.");
            return new VBox(10, heading, hi, msg, UI.card(8, hint));
        }

        String dateStr = appointmentDateTime.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy"));
        String timeStr = appointmentDateTime.format(DateTimeFormatter.ofPattern("HH:mm"));
        VBox card = UI.card(8,
                NotificationLayout.detailRow("Reference", bookingReference),
                NotificationLayout.detailRow("Type", appointmentType),
                NotificationLayout.detailRow("Date", dateStr),
                NotificationLayout.detailRow("Time", timeStr),
                NotificationLayout.detailRow("Location", clinicLocation),
                NotificationLayout.detailRow("Clinician", doctorName)
        );
        return new VBox(10, heading, hi, card);
    }
}