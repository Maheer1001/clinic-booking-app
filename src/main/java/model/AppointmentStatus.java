package model;

/** Values stored in "Appointment".status. COMPLETED and NO_SHOW are set by clinic staff. */
public enum AppointmentStatus {
    CONFIRMED, CANCELLED, COMPLETED, NO_SHOW;

    /** The stored value, or null if it is not one of these. */
    public static AppointmentStatus fromDb(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
