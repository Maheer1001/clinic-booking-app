package view.notification;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import view.ui.UI;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Notification popup to remind a patient of an upcoming appointment.
 *
 * Usage:
 *   new AppointmentReminderNotification(
 *       "John Doe", "STI Screening",
 *       LocalDateTime.of(2026, 2, 15, 10, 30),
 *       "Southampton Sexual Health Clinic", "Dr. Smith"
 *   ).show();
 */
public class AppointmentReminderNotification extends BaseNotification {

    private final String patientName;
    private final String appointmentType;
    private final LocalDateTime appointmentDateTime;
    private final String clinicLocation;
    private final String doctorName;
    private final String overrideTitle;
    private final String overridePreview;
    private final String overrideBody;
    private final Boolean discreetOverride;

    public AppointmentReminderNotification(String patientName, String appointmentType,
                                           LocalDateTime appointmentDateTime,
                                           String clinicLocation, String doctorName) {
        this(patientName, appointmentType, appointmentDateTime, clinicLocation, doctorName, null, null, null, null);
    }

    public AppointmentReminderNotification(String patientName, String appointmentType,
                                           LocalDateTime appointmentDateTime,
                                           String clinicLocation, String doctorName,
                                           String overrideTitle, String overridePreview, String overrideBody,
                                           Boolean discreetOverride) {
        this.patientName = patientName;
        this.appointmentType = appointmentType;
        this.appointmentDateTime = appointmentDateTime;
        this.clinicLocation = clinicLocation;
        this.doctorName = doctorName;
        this.overrideTitle = (overrideTitle != null && !overrideTitle.isBlank()) ? overrideTitle.trim() : null;
        this.overridePreview = (overridePreview != null && !overridePreview.isBlank()) ? overridePreview.trim() : null;
        this.overrideBody = (overrideBody != null && !overrideBody.isBlank()) ? overrideBody.trim() : null;
        this.discreetOverride = discreetOverride;
    }

    @Override
    public String getTitle() {
        if (overrideTitle != null) {
            return overrideTitle;
        }
        return "Appointment Reminder";
    }

    @Override
    public String getMessage() {
        return getFullText();
    }

    @Override
    protected boolean supportsDiscreetToggle() {
        return true;
    }

    @Override
    protected boolean isDiscreetEnabled() {
        boolean global = super.isDiscreetEnabled();
        if (discreetOverride == null) {
            return global;
        }
        return discreetOverride;
    }

    @Override
    protected Node buildPreviewContent() {
        String date = appointmentDateTime.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy"));
        String time = appointmentDateTime.format(DateTimeFormatter.ofPattern("HH:mm"));

        Label dateLabel = UI.body("Date: " + date);
        Label timeLabel = UI.body("Time: " + time);

        VBox card = UI.card(4, dateLabel, timeLabel);
        return new VBox(8, UI.titleSm(getTitle()), card);
    }

    @Override
    protected Node buildFullContent() {
        if (overrideBody != null) {
            Label body = new Label(overrideBody);
            body.setWrapText(true);
            return new VBox(8, UI.titleSm(getTitle()), body);
        }

        String date = appointmentDateTime.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy"));
        String time = appointmentDateTime.format(DateTimeFormatter.ofPattern("HH:mm"));

        Label dateLabel = UI.body("Date: " + date);
        Label timeLabel = UI.body("Time: " + time);
        Label typeLabel = UI.body("Type: " + appointmentType);
        Label locationLabel = UI.body("Location: " + clinicLocation);
        Label clinicianLabel = UI.body("Clinician: " + doctorName);

        VBox detailsCard = UI.card(4, dateLabel, timeLabel, typeLabel, locationLabel, clinicianLabel);
        Label cta = UI.muted("⚠ Arrive 10 minutes early.");

        return new VBox(8, UI.titleSm(getTitle()), detailsCard, cta);
    }

    @Override
    public String getType() {
        return "APPOINTMENT_REMINDER";
    }
}