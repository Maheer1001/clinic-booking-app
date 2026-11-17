package service.email;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Email sent to remind a patient about an ongoing treatment
 * (e.g. medication schedule, follow-up treatment session).
 * In discreet mode the treatment name and instructions are left out and the subject is neutral.
 */
public class TreatmentReminderEmail extends BaseEmail {

    static final String DISCREET_SUBJECT = "A reminder from your account";

    private final String treatmentName;
    private final String treatmentInstructions;
    private final LocalDate nextDueDate;
    private final String overrideSubject;
    private final String introText;
    private final String fromNameOverride;
    private final boolean discreet;

    public TreatmentReminderEmail(String recipientEmail, String recipientName,
                                  String treatmentName, String treatmentInstructions,
                                  LocalDate nextDueDate) {
        this(recipientEmail, recipientName, treatmentName, treatmentInstructions, nextDueDate, null, null, null);
    }

    public TreatmentReminderEmail(String recipientEmail, String recipientName,
                                  String treatmentName, String treatmentInstructions,
                                  LocalDate nextDueDate,
                                  String overrideSubject, String introText, String fromNameOverride) {
        this(recipientEmail, recipientName, treatmentName, treatmentInstructions, nextDueDate,
                overrideSubject, introText, fromNameOverride, false);
    }

    /**
     * @param nextDueDate may be null (e.g. ongoing medication with no fixed date); the row is then omitted
     */
    public TreatmentReminderEmail(String recipientEmail, String recipientName,
                                  String treatmentName, String treatmentInstructions,
                                  LocalDate nextDueDate,
                                  String overrideSubject, String introText, String fromNameOverride,
                                  boolean discreet) {
        super(recipientEmail, recipientName);
        this.treatmentName = treatmentName;
        this.treatmentInstructions = treatmentInstructions;
        this.nextDueDate = nextDueDate;
        this.overrideSubject = overrideSubject != null && !overrideSubject.isBlank() ? overrideSubject.trim() : null;
        this.introText = introText != null && !introText.isBlank() ? introText.trim() : null;
        this.fromNameOverride = fromNameOverride != null && !fromNameOverride.isBlank() ? fromNameOverride.trim() : null;
        this.discreet = discreet;
    }

    @Override
    public String getSubject() {
        if (overrideSubject != null) {
            return overrideSubject;
        }
        if (discreet) {
            return DISCREET_SUBJECT;
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
        if (discreet) {
            context.put("discreet", true);
        } else {
            context.put("treatmentName", treatmentName);
            context.put("treatmentInstructions", treatmentInstructions);
        }
        if (nextDueDate != null) {
            context.put("nextDueDate", nextDueDate.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")));
        }
        if (introText != null) {
            context.put("customIntro", introText);
        }
        return context;
    }

    @Override
    public String getFromName() {
        return fromNameOverride;
    }

    @Override
    public boolean isDiscreet() {
        return discreet;
    }
}
