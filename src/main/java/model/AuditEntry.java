package model;

/**
 * One row of the append-only "AuditLog": who (staff user) did what, at which clinic, to which record
 * and which patient. Ids only: record contents (results, notes, personal details) are never logged.
 */
public record AuditEntry(int staffUserId, int clinicId, Action action, String entity, Integer entityId,
                         Integer patientUserId) {

    /** Stored in AuditLog.action. Reads start with VIEW_, everything else is a write. */
    public enum Action {
        VIEW_DAY_LIST, VIEW_APPOINTMENT, SEARCH_PATIENTS, VIEW_PATIENT, VIEW_TEST,
        MARK_COMPLETED, MARK_NO_SHOW, CLEAR_OUTCOME, SAVE_STAFF_NOTES,
        CREATE_TEST, UPDATE_TEST, RELEASE_TEST,
        PRESCRIBE, STOP_PRESCRIPTION,
        STAFF_SIGN_UP, STAFF_SEEDED, REMOVE_STAFF, PROMOTE_STAFF, DEMOTE_STAFF, ROTATE_STAFF_CODE,
        CREATE_TIMESLOT, UPDATE_TIMESLOT
    }

    public static final String APPOINTMENT = "Appointment";
    public static final String PATIENT = "User";
    public static final String TEST = "Test";
    public static final String PRESCRIPTION = "Prescription";
    public static final String STAFF = "StaffClinic";
    public static final String STAFF_CODE = "ClinicStaffCode";
    public static final String TIMESLOT = "Timeslot";
    public static final String CLINIC = "Clinic";

    public static AuditEntry of(int staffUserId, int clinicId, Action action, String entity, Integer entityId,
                                Integer patientUserId) {
        return new AuditEntry(staffUserId, clinicId, action, entity, entityId, patientUserId);
    }
}
