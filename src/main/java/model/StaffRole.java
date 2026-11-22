package model;

/** "User".role. Patients and staff are separate accounts; staff only see their own clinic(s). */
public enum StaffRole {
    PATIENT, CLINICIAN, CLINIC_ADMIN;

    /** True for the two staff roles. */
    public boolean isStaff() {
        return this != PATIENT;
    }

    /** Unknown or missing values read as PATIENT, so nothing is ever granted by a bad value. */
    public static StaffRole fromDb(String value) {
        if (value == null) return PATIENT;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return PATIENT;
        }
    }
}
