package service.email;

import java.util.HashMap;
import java.util.Map;

/**
 * Email sent to remind a patient to schedule an appointment
 * (e.g. for a routine check-up or follow-up).
 */
public class MakeAppointmentReminderEmail extends BaseEmail {

    private final String reminderReason;
    private final String suggestedAppointmentType;
    private final String overrideSubject;
    private final String introText;
    private final String fromNameOverride;

    public MakeAppointmentReminderEmail(String recipientEmail, String recipientName,
                                        String reminderReason, String suggestedAppointmentType) {
        this(recipientEmail, recipientName, reminderReason, suggestedAppointmentType, null, null, null);
    }

    public MakeAppointmentReminderEmail(String recipientEmail, String recipientName,
                                        String reminderReason, String suggestedAppointmentType,
                                        String overrideSubject, String introText, String fromNameOverride) {
        super(recipientEmail, recipientName);
        this.reminderReason = reminderReason;
        this.suggestedAppointmentType = suggestedAppointmentType;
        this.overrideSubject = overrideSubject != null && !overrideSubject.isBlank() ? overrideSubject.trim() : null;
        this.introText = introText != null && !introText.isBlank() ? introText.trim() : null;
        this.fromNameOverride = fromNameOverride != null && !fromNameOverride.isBlank() ? fromNameOverride.trim() : null;
    }

    @Override
    public String getSubject() {
        if (overrideSubject != null) {
            return overrideSubject;
        }
        return "Reminder: It may be time to book a check-up";
    }

    @Override
    public String getTemplateName() {
        return "make-appointment-reminder";
    }

    @Override
    public Map<String, Object> getTemplateContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("reminderReason", reminderReason);
        context.put("suggestedAppointmentType", suggestedAppointmentType);
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