package service.email;

import model.ReminderTemplateOverrides;

import java.util.HashMap;
import java.util.Map;

/**
 * Email sent when an appointment is successfully rescheduled.
 */
public class RescheduleConfirmationEmail extends BaseEmail {

    private final String appointmentRef;
    private final String serviceName;
    private final String clinicName;
    private final String oldDate;
    private final String newDate;
    private final boolean discreet;
    private final ReminderTemplateOverrides templateOverrides;

    public RescheduleConfirmationEmail(String recipientEmail, String recipientName,
                                       String appointmentRef, String serviceName, String clinicName,
                                       String oldDate, String newDate, boolean discreet,
                                       ReminderTemplateOverrides templateOverrides) {
        super(recipientEmail, recipientName);
        this.appointmentRef = appointmentRef;
        this.serviceName = serviceName;
        this.clinicName = clinicName;
        this.oldDate = oldDate;
        this.newDate = newDate;
        this.discreet = discreet;
        this.templateOverrides = templateOverrides;
    }

    private ReminderTemplateOverrides ov() {
        return templateOverrides != null ? templateOverrides : ReminderTemplateOverrides.fromString("");
    }

    @Override
    public String getSubject() {
        String custom = ov().getEmailSubject();
        if (custom != null && !custom.isBlank()) {
            return custom.trim();
        }
        if (discreet) {
            return "Appointment updated";
        }
        return "Your appointment has been rescheduled - Ref: " + appointmentRef;
    }

    @Override
    public String getTemplateName() {
        return "reschedule-confirmation";
    }

    @Override
    public Map<String, Object> getTemplateContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("appointmentRef", appointmentRef);
        context.put("serviceName", serviceName);
        context.put("clinicName", clinicName);
        context.put("oldDate", oldDate);
        context.put("newDate", newDate);
        context.put("discreet", discreet);
        ReminderTemplateOverrides o = ov();
        String intro = o.getEmailBodyIntro();
        if (intro != null && !intro.isBlank()) {
            context.put("customIntro", intro.trim());
        }
        return context;
    }

    @Override
    public String getFromName() {
        String from = ov().getEmailFromName();
        if (from != null && !from.isBlank()) {
            return from.trim();
        }
        return null;
    }

    @Override
    public boolean isDiscreet() {
        return discreet;
    }
}
