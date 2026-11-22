package controller;

import model.AppointmentStatus;
import model.StaffRole;
import model.TestDraft;
import model.TestResult;
import model.TestResultStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Text and form rules for the staff screens, kept free of JavaFX so they can be unit tested. */
public final class StaffForms {

    private StaffForms() {}

    /** A status pill: the words shown and the style suffix (staff-pill-&lt;kind&gt;). */
    public record Pill(String text, String kind) {}

    public static Pill appointmentPill(AppointmentStatus status) {
        if (status == null) return new Pill("Unknown", "neutral");
        return switch (status) {
            case CONFIRMED -> new Pill("Booked", "booked");
            case COMPLETED -> new Pill("Attended", "good");
            case NO_SHOW -> new Pill("Did not attend", "warn");
            case CANCELLED -> new Pill("Cancelled", "neutral");
        };
    }

    public static Pill testPill(boolean released) {
        return released ? new Pill("Released", "good") : new Pill("Draft", "draft");
    }

    public static Pill resultPill(TestResultStatus status) {
        TestResultStatus s = status == null ? TestResultStatus.PENDING : status;
        return switch (s) {
            case POSITIVE -> new Pill(s.label(), "bad");
            case NEGATIVE -> new Pill(s.label(), "good");
            case PENDING -> new Pill(s.label(), "neutral");
        };
    }

    public static Pill prescriptionPill(boolean active) {
        return active ? new Pill("Current", "good") : new Pill("Stopped", "neutral");
    }

    public static Pill rolePill(StaffRole role) {
        return role == StaffRole.CLINIC_ADMIN ? new Pill("Admin", "booked") : new Pill("Clinician", "neutral");
    }

    /** Attendance can be recorded on the day or after; cancelled appointments never change. */
    public static boolean canRecordOutcome(AppointmentStatus status, LocalDate date, LocalDate today) {
        return status != AppointmentStatus.CANCELLED && (date == null || !date.isAfter(today));
    }

    /** "Clear" only when there is an outcome to undo. */
    public static boolean canClearOutcome(AppointmentStatus status) {
        return status == AppointmentStatus.COMPLETED || status == AppointmentStatus.NO_SHOW;
    }

    /** One line of the results editor as typed. */
    public record ResultRow(String condition, TestResultStatus result) {}

    /**
     * The editor's fields as a {@link TestDraft}. Rows left completely empty are dropped; anything
     * else is passed on for {@code StaffService} to validate (so its messages are the ones shown).
     */
    public static TestDraft testDraft(Integer appId, String testType, LocalDate testDate, List<ResultRow> rows,
                                      String clinicianNotes, String treatmentInstructions) {
        List<TestResult> results = new ArrayList<>();
        for (ResultRow row : rows) {
            String condition = row.condition() == null ? "" : row.condition().strip();
            if (condition.isEmpty()) continue;
            results.add(new TestResult(condition, row.result() == null ? TestResultStatus.PENDING : row.result()));
        }
        return new TestDraft(appId, testType, testDate, clinicianNotes, treatmentInstructions, results);
    }

    private static final Pattern TIME = Pattern.compile("^(\\d{1,2})(?:[:.]?(\\d{2}))?\\s*(am|pm)?$");

    /** "9:30", "09:30", "0930", "9.30", "9am", "2:15pm" -> a time; anything else null. */
    public static LocalTime parseTime(String text) {
        if (text == null) return null;
        Matcher m = TIME.matcher(text.strip().toLowerCase(Locale.ROOT));
        if (!m.matches()) return null;
        int hour = Integer.parseInt(m.group(1));
        int minute = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
        String half = m.group(3);
        if (half != null) {
            if (hour < 1 || hour > 12) return null;
            hour = hour % 12 + ("pm".equals(half) ? 12 : 0);
        }
        if (hour > 23 || minute > 59) return null;
        return LocalTime.of(hour, minute);
    }

    /** "Booked 2 of 5" style capacity line for a timeslot. */
    public static String capacityText(int booked, int capacity) {
        return booked + " of " + capacity + (capacity == 1 ? " place" : " places") + " booked";
    }

    /**
     * Words for an in-place save: every one starts "Saved." like the patient screens, followed by the
     * service's own note unless it already says so.
     */
    public static String savedText(String serviceMessage) {
        if (serviceMessage == null || serviceMessage.isBlank()) return "Saved.";
        String m = serviceMessage.strip();
        if (m.startsWith("Saved")) return m;
        return "Saved. " + m;
    }
}
