package view.notification;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import model.ReminderTemplateOverrides;
import view.ui.UI;

/**
 * Notification popup when an appointment is successfully cancelled.
 */
public class CancellationNotification extends BaseNotification {

    private final String patientName;
    private final String appointmentRef;
    private final String serviceName;
    private final String clinicName;
    private final String dateTime;
    private final boolean discreet;
    private final ReminderTemplateOverrides templateOverrides;

    public CancellationNotification(String patientName, String appointmentRef,
                                    String serviceName, String clinicName, String dateTime,
                                    boolean discreet,
                                    ReminderTemplateOverrides templateOverrides) {
        this.patientName = patientName;
        this.appointmentRef = appointmentRef;
        this.serviceName = serviceName;
        this.clinicName = clinicName;
        this.dateTime = dateTime;
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
        return "Appointment Cancelled";
    }

    @Override
    public String getMessage() {
        String custom = overrideNotificationText();
        if (custom != null) {
            return custom;
        }
        if (discreet) {
            return "Hi " + patientName + ",\n\n"
                    + "Your appointment has been cancelled. Open the app for full details.";
        }
        return "Hi " + patientName + ",\n\n"
                + "Your appointment has been cancelled:\n\n"
                + "Reference: " + appointmentRef + "\n"
                + "Service: " + serviceName + "\n"
                + "Location: " + clinicName + "\n"
                + "Date: " + dateTime;
    }

    @Override
    public String getType() {
        return "CANCELLATION";
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
            Label msg = UI.body("Your appointment has been cancelled.");
            Label hint = UI.muted("Open the app for full details.");
            return new VBox(10, heading, hi, msg, UI.card(8, hint));
        }

        VBox card = UI.card(8,
                NotificationLayout.detailRow("Reference", appointmentRef),
                NotificationLayout.detailRow("Service", serviceName),
                NotificationLayout.detailRow("Location", clinicName),
                NotificationLayout.detailRow("Date", dateTime)
        );
        return new VBox(10, heading, hi, card);
    }
}
