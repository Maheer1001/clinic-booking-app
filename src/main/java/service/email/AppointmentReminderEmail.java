package service.email;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Email sent to remind a patient of an upcoming appointment.
 */
public class AppointmentReminderEmail extends BaseEmail {

    private final String appointmentType;
    private final LocalDateTime appointmentDateTime;
    private final String clinicLocation;
    private final String doctorName;
    private final String overrideSubject;
    private final String introText;
    private final String fromNameOverride;

    public AppointmentReminderEmail(String recipientEmail, String recipientName,
                                    String appointmentType, LocalDateTime appointmentDateTime,
                                    String clinicLocation, String doctorName) {
        this(recipientEmail, recipientName, appointmentType, appointmentDateTime, clinicLocation, doctorName, null, null, null);
    }

    public AppointmentReminderEmail(String recipientEmail, String recipientName,
                                    String appointmentType, LocalDateTime appointmentDateTime,
                                    String clinicLocation, String doctorName,
                                    String overrideSubject, String introText, String fromNameOverride) {
        super(recipientEmail, recipientName);
        this.appointmentType = appointmentType;
        this.appointmentDateTime = appointmentDateTime;
        this.clinicLocation = clinicLocation;
        this.doctorName = doctorName;
        this.overrideSubject = overrideSubject != null && !overrideSubject.isBlank() ? overrideSubject.trim() : null;
        this.introText = introText != null && !introText.isBlank() ? introText.trim() : null;
        this.fromNameOverride = fromNameOverride != null && !fromNameOverride.isBlank() ? fromNameOverride.trim() : null;
    }

    @Override
    public String getSubject() {
        if (overrideSubject != null) {
            return overrideSubject;
        }
        return "Reminder: Your appointment on "
                + appointmentDateTime.format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
    }

    @Override
    public String getTemplateName() {
        return "appointment-reminder";
    }

    @Override
    public Map<String, Object> getTemplateContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("appointmentType", appointmentType);
        context.put("appointmentDate", appointmentDateTime.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")));
        context.put("appointmentTime", appointmentDateTime.format(DateTimeFormatter.ofPattern("HH:mm")));
        context.put("clinicLocation", clinicLocation);
        context.put("doctorName", doctorName);
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