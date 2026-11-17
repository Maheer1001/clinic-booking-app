package service.email;

import model.ReminderTemplateOverrides;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Email sent to confirm a newly booked appointment.
 */
public class BookingConfirmationEmail extends BaseEmail {

    private final String bookingReference;
    private final String appointmentType;
    private final LocalDateTime appointmentDateTime;
    private final String clinicLocation;
    private final String doctorName;
    private final boolean discreet;
    private final ReminderTemplateOverrides templateOverrides;

    public BookingConfirmationEmail(String recipientEmail, String recipientName,
                                    String bookingReference, String appointmentType,
                                    LocalDateTime appointmentDateTime,
                                    String clinicLocation, String doctorName,
                                    boolean discreet, ReminderTemplateOverrides templateOverrides) {
        super(recipientEmail, recipientName);
        this.bookingReference = bookingReference;
        this.appointmentType = appointmentType;
        this.appointmentDateTime = appointmentDateTime;
        this.clinicLocation = clinicLocation;
        this.doctorName = doctorName;
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
            return "Your appointment is confirmed";
        }
        return "Your appointment is confirmed - Ref: " + bookingReference;
    }

    @Override
    public String getTemplateName() {
        return "booking-confirmation";
    }

    @Override
    public Map<String, Object> getTemplateContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("bookingReference", bookingReference);
        context.put("appointmentType", appointmentType);
        context.put("appointmentDate", appointmentDateTime.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy")));
        context.put("appointmentTime", appointmentDateTime.format(DateTimeFormatter.ofPattern("HH:mm")));
        context.put("clinicLocation", clinicLocation);
        context.put("doctorName", doctorName);
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