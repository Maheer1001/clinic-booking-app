package view.notification;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import view.ui.Dates;
import view.ui.UI;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Notification popup to remind a patient about ongoing treatment.
 *
 * Usage:
 *   new TreatmentReminderNotification(
 *       "John Doe", "Doxycycline Course",
 *       "Take 100mg twice daily with food",
 *       LocalDate.of(2026, 2, 17)
 *   ).show();
 */
public class TreatmentReminderNotification extends BaseNotification {

    private final String patientName;
    private final String treatmentName;
    private final String treatmentInstructions;
    private final LocalDate nextDueDate;
    private final String overrideTitle;
    private final String overridePreview;
    private final String overrideBody;
    private final Boolean discreetOverride;

    public TreatmentReminderNotification(String patientName, String treatmentName,
                                         String treatmentInstructions, LocalDate nextDueDate) {
        this(patientName, treatmentName, treatmentInstructions, nextDueDate, null, null, null, null);
    }

    public TreatmentReminderNotification(String patientName, String treatmentName,
                                         String treatmentInstructions, LocalDate nextDueDate,
                                         String overrideTitle, String overridePreview, String overrideBody,
                                         Boolean discreetOverride) {
        this.patientName = patientName;
        this.treatmentName = treatmentName;
        this.treatmentInstructions = treatmentInstructions;
        this.nextDueDate = nextDueDate;
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
        return "Treatment Reminder";
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
                NotificationLayout.detailsCard("Next due", Dates.full(nextDueDate)), null);
    }

    @Override
    protected Node buildFullContent() {
        if (overrideBody != null) {
            return NotificationLayout.custom(getTitle(), overrideBody);
        }
        // The "instructions" passed in are the dose (e.g. "400mg"), so they are labelled as such.
        return NotificationLayout.page(getTitle(), patientName, null,
                NotificationLayout.detailsCard("Treatment", treatmentName, "Dose", treatmentInstructions,
                        "Next due", Dates.full(nextDueDate)),
                "Please complete your full course as prescribed.");
    }

    @Override
    public String getType() {
        return "TREATMENT_REMINDER";
    }
}