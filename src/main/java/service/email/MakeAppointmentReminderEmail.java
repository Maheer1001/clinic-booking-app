package service.email;

import java.util.HashMap;
import java.util.Map;

/**
 * Email sent to remind a patient to schedule an appointment
 * (e.g. for a routine check-up or follow-up).
 * In discreet mode the suggested appointment type is left out.
 */
public class MakeAppointmentReminderEmail extends BaseEmail {

    private final String reminderReason;
    private final String suggestedAppointmentType;
    private final String overrideSubject;
    private final String introText;
    private final String fromNameOverride;
    private final boolean discreet;

    public MakeAppointmentReminderEmail(String recipientEmail, String recipientName,
                                        String reminderReason, String suggestedAppointmentType) {
        this(recipientEmail, recipientName, reminderReason, suggestedAppointmentType, null, null, null);
    }

    public MakeAppointmentReminderEmail(String recipientEmail, String recipientName,
                                        String reminderReason, String suggestedAppointmentType,
                                        String overrideSubject, String introText, String fromNameOverride) {
        this(recipientEmail, recipientName, reminderReason, suggestedAppointmentType,
                overrideSubject, introText, fromNameOverride, false);
    }

    public MakeAppointmentReminderEmail(String recipientEmail, String recipientName,
                                        String reminderReason, String suggestedAppointmentType,
                                        String overrideSubject, String introText, String fromNameOverride,
                                        boolean discreet) {
        super(recipientEmail, recipientName);
        this.reminderReason = reminderReason;
        this.suggestedAppointmentType = suggestedAppointmentType;
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
        if (discreet) {
            context.put("discreet", true);
        } else {
            context.put("suggestedAppointmentType", suggestedAppointmentType);
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
