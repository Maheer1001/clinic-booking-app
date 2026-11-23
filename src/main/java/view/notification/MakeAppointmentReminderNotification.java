package view.notification;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import view.ui.UI;

/**
 * Notification popup to remind a patient to schedule an appointment.
 *
 * Usage:
 *   new MakeAppointmentReminderNotification(
 *       "John Doe",
 *       "It has been 12 months since your last screening",
 *       "Annual STI Screening"
 *   ).show();
 */
public class MakeAppointmentReminderNotification extends BaseNotification {

    private final String patientName;
    private final String reminderReason;
    private final String suggestedAppointmentType;
    private final String overrideTitle;
    private final String overridePreview;
    private final String overrideBody;
    private final Boolean discreetOverride;

    public MakeAppointmentReminderNotification(String patientName, String reminderReason,
                                               String suggestedAppointmentType) {
        this(patientName, reminderReason, suggestedAppointmentType, null, null, null, null);
    }

    public MakeAppointmentReminderNotification(String patientName, String reminderReason,
                                               String suggestedAppointmentType,
                                               String overrideTitle, String overridePreview, String overrideBody,
                                               Boolean discreetOverride) {
        this.patientName = patientName;
        this.reminderReason = reminderReason;
        this.suggestedAppointmentType = suggestedAppointmentType;
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
        return "Book an Appointment";
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
        String previewLine = overridePreview != null ? overridePreview : "You have a booking reminder.";
        return NotificationLayout.page(getTitle(), patientName, previewLine, null, null);
    }

    @Override
    protected Node buildFullContent() {
        if (overrideBody != null) {
            return NotificationLayout.custom(getTitle(), overrideBody);
        }
        return NotificationLayout.page(getTitle(), patientName,
                overridePreview != null ? overridePreview : reminderReason,
                NotificationLayout.detailsCard("Suggested", suggestedAppointmentType),
                "You can book from the Booking tab.");
    }

    @Override
    public String getType() {
        return "MAKE_APPOINTMENT_REMINDER";
    }
}