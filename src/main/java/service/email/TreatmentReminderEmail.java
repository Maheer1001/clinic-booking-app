package service.email;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Email sent to remind a patient about an ongoing treatment
 * (e.g. medication schedule, follow-up treatment session).
 */
public class TreatmentReminderEmail extends BaseEmail {

    private final String treatmentName;
    private final String treatmentInstructions;
    private final LocalDate nextDueDate;
    private final String overrideSubject;
    private final String introText;
    private final String fromNameOverride;

    public TreatmentReminderEmail(String recipientEmail, String recipientName,
                                  String treatmentName, String treatmentInstructions,
                                  LocalDate nextDueDate) {
        this(recipientEmail, recipientName, treatmentName, treatmentInstructions, nextDueDate, null, null, null);
    }

    public TreatmentReminderEmail(String recipientEmail, String recipientName,
                                  String treatmentName, String treatmentInstructions,
                                  LocalDate nextDueDate,
                                  String overrideSubject, String introText, String fromNameOverride) {
        super(recipientEmail, recipientName);
        this.treatmentName = treatmentName;
        this.treatmentInstructions = treatmentInstructions;
        this.nextDueDate = nextDueDate;
        this.overrideSubject = overrideSubject != null && !overrideSubject.isBlank() ? overrideSubject.trim() : null;
        this.introText = introText != null && !introText.isBlank() ? introText.trim() : null;
        this.fromNameOverride = fromNameOverride != null && !fromNameOverride.isBlank() ? fromNameOverride.trim() : null;
    }

    @Override
    public String getSubject() {
        if (overrideSubject != null) {
            return overrideSubject;
        }
        return "Treatment reminder: you have a dose or step due soon";
    }

    @Override
    public String getTemplateName() {
        return "treatment-reminder";
    }

    @Override
    public Map<String, Object> getTemplateContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("treatmentName", treatmentName);
        context.put("treatmentInstructions", treatmentInstructions);
        context.put("nextDueDate", nextDueDate.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")));
        if (introText != null) {
            context.put("customIntro", introText);
        }
        return context;
    }

    @Override
    public String getFromName() {
        return fromNameOverride;
    }
}