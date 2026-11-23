package service;

import model.Appointment;
import model.Prescription;
import model.ReminderTemplateOverrides;

import java.time.LocalDate;
import java.util.Locale;

/**
 * A scheduled reminder that has just gone out, with everything needed to show it in the app as
 * well as by email: who it is for and the saved wording (title / preview line / body overrides and
 * the discreet choice).
 *
 * @param appointment set for {@link Kind#APPOINTMENT}
 * @param prescription set for {@link Kind#TREATMENT}
 * @param dueDate      dose date for {@link Kind#TREATMENT}
 * @param reason       e.g. "It has been at least 90 days since your last visit" for {@link Kind#BOOKING}
 */
public record ReminderAlert(Kind kind, String recipientEmail, String recipientName,
                            ReminderTemplateOverrides overrides,
                            Appointment appointment, Prescription prescription, LocalDate dueDate,
                            String reason) {

    public enum Kind { APPOINTMENT, TREATMENT, BOOKING }

    public ReminderAlert {
        overrides = overrides != null ? overrides : ReminderTemplateOverrides.fromString(null);
    }

    static ReminderAlert appointment(Appointment appt, String email, String name, ReminderTemplateOverrides overrides) {
        return new ReminderAlert(Kind.APPOINTMENT, email, name, overrides, appt, null, null, null);
    }

    static ReminderAlert treatment(Prescription rx, String email, String name, ReminderTemplateOverrides overrides,
                                   LocalDate dueDate) {
        return new ReminderAlert(Kind.TREATMENT, email, name, overrides, null, rx, dueDate, null);
    }

    static ReminderAlert booking(String email, String name, ReminderTemplateOverrides overrides, String reason) {
        return new ReminderAlert(Kind.BOOKING, email, name, overrides, null, null, null, reason);
    }

    /**
     * True when this reminder belongs to whoever is signed in now ({@code null} = nobody), so a
     * popup is never shown to a different user or after logging out.
     */
    public boolean isFor(String signedInEmail) {
        return signedInEmail != null && recipientEmail != null
                && !recipientEmail.isBlank()
                && normalise(signedInEmail).equals(normalise(recipientEmail));
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
