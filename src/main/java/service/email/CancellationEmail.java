package service.email;

import model.ReminderTemplateOverrides;

import java.util.HashMap;
import java.util.Map;

/**
 * Email sent when an appointment is successfully cancelled.
 */
public class CancellationEmail extends BaseEmail {

    private final String appointmentRef;
    private final String serviceName;
    private final String clinicName;
    private final String dateTime;
    private final boolean discreet;
    private final ReminderTemplateOverrides templateOverrides;

    public CancellationEmail(String recipientEmail, String recipientName,
                             String appointmentRef, String serviceName, String clinicName,
                             String dateTime, boolean discreet,
                             ReminderTemplateOverrides templateOverrides) {
        super(recipientEmail, recipientName);
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

    @Override
    public String getSubject() {
        String custom = ov().getEmailSubject();
        if (custom != null && !custom.isBlank()) {
            return custom.trim();
        }
        if (discreet) {
            return "Appointment cancelled";
        }
        return "Your appointment has been cancelled - Ref: " + appointmentRef;
    }

    @Override
    public String getTemplateName() {
        return "cancellation";
    }

    @Override
    public Map<String, Object> getTemplateContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("appointmentRef", appointmentRef);
        context.put("serviceName", serviceName);
        context.put("clinicName", clinicName);
        context.put("dateTime", dateTime);
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
}
