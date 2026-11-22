package view.notification;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import view.ui.Dates;
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
        return NotificationLayout.page(getTitle(), patientName, overridePreview,
                NotificationLayout.detailsCard("Date", date(), "Time", time()), null);
    }

    @Override
    protected Node buildFullContent() {
        if (overrideBody != null) {
            return NotificationLayout.custom(getTitle(), overrideBody);
        }
        return NotificationLayout.page(getTitle(), patientName, null,
                NotificationLayout.detailsCard("Type", appointmentType, "Date", date(), "Time", time(),
                        "Location", clinicLocation, "Clinician", doctorName),
                "Please arrive 10 minutes early.");
    }

    private String date() {
        return Dates.full(appointmentDateTime.toLocalDate());
    }

    private String time() {
        return Dates.time(appointmentDateTime.toLocalTime());
    }

    @Override
    public String getType() {
        return "APPOINTMENT_REMINDER";
    }
}