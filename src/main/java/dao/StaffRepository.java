package dao;

import model.AppointmentStatus;
import model.AuditEntry;
import model.Medicine;
import model.PrescriptionDraft;
import model.StaffAccess;
import model.StaffAppointment;
import model.StaffMember;
import model.StaffPatient;
import model.StaffPrescription;
import model.StaffRole;
import model.StaffTest;
import model.StaffTimeslot;
import model.TestDraft;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Data access for clinic staff. Only {@link service.StaffService} should call it: the service checks
 * role and clinic membership first. Every query still filters by {@code clinicId} in SQL, and every
 * write takes the {@link AuditEntry} to append in the same transaction (for a create, the new id is
 * filled in). {@code now} values are UTC.
 */
public interface StaffRepository {

    Optional<StaffAccess> access(int userId) throws SQLException;

    void audit(List<AuditEntry> entries) throws SQLException;

    // ------------------------------------------------------------ appointments

    List<StaffAppointment> dayList(int clinicId, LocalDate date) throws SQLException;

    Optional<StaffAppointment> appointment(int clinicId, int appId) throws SQLException;

    /** COMPLETED sets attended_at = now, NO_SHOW / CONFIRMED clear it. Never changes a CANCELLED one. */
    boolean setOutcome(int clinicId, int appId, AppointmentStatus status, LocalDateTime now, AuditEntry audit)
            throws SQLException;

    boolean setStaffNotes(int clinicId, int appId, String notes, LocalDateTime now, AuditEntry audit)
            throws SQLException;

    // ------------------------------------------------------------ patients

    /** True only if the patient has an appointment at the clinic. */
    boolean patientVisible(int clinicId, int patientUserId) throws SQLException;

    List<StaffPatient> searchPatients(int clinicId, String query, int limit) throws SQLException;

    Optional<StaffPatient> patient(int clinicId, int patientUserId) throws SQLException;

    List<StaffAppointment> patientAppointments(int clinicId, int patientUserId) throws SQLException;

    List<StaffTest> patientTests(int clinicId, int patientUserId) throws SQLException;

    List<StaffPrescription> patientPrescriptions(int clinicId, int patientUserId) throws SQLException;

    // ------------------------------------------------------------ tests

    Optional<StaffTest> test(int clinicId, int testId) throws SQLException;

    /** @return the new test id, or 0 if the patient is not visible at the clinic */
    int createTest(int clinicId, int patientUserId, int clinicianId, TestDraft draft, LocalDateTime now,
                   AuditEntry audit) throws SQLException;

    boolean updateTest(int clinicId, int testId, TestDraft draft, LocalDateTime now, AuditEntry audit)
            throws SQLException;

    /** Sets released_at; false if not found at the clinic or already released. */
    boolean releaseTest(int clinicId, int testId, LocalDateTime now, AuditEntry audit) throws SQLException;

    // ------------------------------------------------------------ prescriptions

    List<Medicine> medicines() throws SQLException;

    Optional<StaffPrescription> prescription(int clinicId, int prescriptionId) throws SQLException;

    /** @return the new prescription id, or 0 if the patient is not visible at the clinic */
    int prescribe(int clinicId, int patientUserId, int clinicianId, PrescriptionDraft draft, LocalDateTime now,
                  AuditEntry audit) throws SQLException;

    /** Marks it no longer taken and ends it today (if it was due to end later). */
    boolean stopPrescription(int clinicId, int prescriptionId, LocalDate today, LocalDateTime now,
                             AuditEntry audit) throws SQLException;

    // ------------------------------------------------------------ clinic admin

    /** ACTIVE staff at the clinic (pending sign-ups are not staff there yet). */
    List<StaffMember> staff(int clinicId) throws SQLException;

    /** Staff who signed up with the clinic's code and are waiting for an admin's approval. */
    List<StaffMember> pendingStaff(int clinicId) throws SQLException;

    /** PENDING -> ACTIVE (records who approved and when); false if there is no pending membership. */
    boolean approveStaff(int clinicId, int staffUserId, int byUserId, LocalDateTime now, AuditEntry audit)
            throws SQLException;

    /** Deletes a PENDING membership (the account stays, with no clinic); false if none. */
    boolean rejectStaff(int clinicId, int staffUserId, AuditEntry audit) throws SQLException;

    /** Removes an ACTIVE membership. */
    boolean removeStaff(int clinicId, int staffUserId, AuditEntry audit) throws SQLException;

    /** Only changes a staff account that is an ACTIVE member of the clinic. */
    boolean setRole(int clinicId, int staffUserId, StaffRole role, AuditEntry audit) throws SQLException;

    /** Empty if the clinic has no staff code; otherwise whether it is still the seeded demo code. */
    Optional<Boolean> staffCodeIsDemo(int clinicId) throws SQLException;

    /** Stores a new code (clears the demo flag). */
    void setStaffCodeHash(int clinicId, String codeHash, int byUserId, LocalDateTime now, AuditEntry audit)
            throws SQLException;

    List<StaffTimeslot> timeslots(int clinicId, LocalDate date) throws SQLException;

    Optional<StaffTimeslot> timeslot(int clinicId, int timeslotId) throws SQLException;

    boolean clinicOffersService(int clinicId, int serviceId) throws SQLException;

    int createTimeslot(int clinicId, int serviceId, LocalDate date, LocalTime start, LocalTime end, int capacity,
                       AuditEntry audit) throws SQLException;

    /** False if not at the clinic or if {@code capacity} is below the slot's booked_count (checked in SQL). */
    boolean updateTimeslot(int clinicId, int timeslotId, LocalDate date, LocalTime start, LocalTime end,
                           int capacity, AuditEntry audit) throws SQLException;
}
