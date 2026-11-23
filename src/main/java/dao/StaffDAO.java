package dao;

import app.DB;
import model.AppointmentStatus;
import model.AuditEntry;
import model.BookingDetails;
import model.ClinicBookingDetails;
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
import model.TestResult;
import model.TestResultStatus;
import model.User;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * SQL for clinic staff. Every query is scoped to one clinic in SQL; patients are only visible when
 * they have an appointment at that clinic. Writes append their audit row in the same transaction.
 * Never log the values read or written here.
 */
public class StaffDAO implements StaffRepository {

    /** True when patient (param 1) has an appointment at clinic (param 2). */
    private static final String PATIENT_VISIBLE = """
        EXISTS (SELECT 1 FROM "Appointment" va JOIN "Timeslot" vt ON vt.timeslot_id = va.timeslot_id
                WHERE va.user_id = ? AND vt.clinic_id = ?)
        """;

    private static final String APPOINTMENT_SELECT = """
        SELECT a.app_id, a.user_id, a.status, a.notes, a.staff_notes, a.attended_at,
               t.date, t.start_time, t.end_time, s.name AS service_name,
               u.first_name AS u_first, u.last_name AS u_last, u.email AS u_email,
               d.app_id AS d_app_id, d.first_name, d.last_name, d.dob, d.phone_number, d.contact_email,
               d.gender, d.sexuality, d.accessibility_needs
        FROM "Appointment" a
        JOIN "Timeslot" t ON t.timeslot_id = a.timeslot_id
        LEFT JOIN "Service" s ON s.service_id = t.service_id
        LEFT JOIN "User" u ON u.user_id = a.user_id
        LEFT JOIN "BookingDetails" d ON d.app_id = a.app_id
        WHERE t.clinic_id = ?
        """;

    private static final String TEST_SELECT = """
        SELECT test_id, user_id, clinic_id, app_id, test_type, test_date, clinician_notes, treatment_instructions,
               clinician_id, released_at
        FROM "Test"
        WHERE clinic_id = ?
        """;

    private static final String PRESCRIPTION_SELECT = """
        SELECT p.prescription_id, p.user_id, p.clinic_id, p.medicine_id, m.name AS medicine_name, p.test_id,
               p.dosage, p.doses_per_day, p.days_between_doses, p.start_date, p.end_date, p.instructions,
               p.actively_taking, p.prescribing_clinician_id
        FROM "Prescription" p
        LEFT JOIN "Medicine" m ON m.medicine_id = p.medicine_id
        WHERE p.clinic_id = ?
        """;

    @FunctionalInterface
    private interface Work<T> {
        T run(Connection c) throws SQLException;
    }

    /** Runs {@code work} in one transaction; rolls back on an exception. */
    private static <T> T inTransaction(Work<T> work) throws SQLException {
        try (Connection c = DB.connect()) {
            boolean autoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                T out = work.run(c);
                c.commit();
                return out;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(autoCommit);
            }
        }
    }

    /** Thrown inside a transaction to roll it back when a guarded update touched nothing. */
    private static final class NothingChanged extends RuntimeException {
        NothingChanged() {
            super(null, null, false, false);
        }
    }

    private static boolean guarded(Work<Boolean> work) throws SQLException {
        try {
            return inTransaction(work);
        } catch (NothingChanged e) {
            return false;
        }
    }

    // ================================================================ access + audit

    @Override
    public Optional<StaffAccess> access(int userId) throws SQLException {
        try (Connection c = DB.connect()) {
            return UserDAO.getStaffAccess(c, userId);
        }
    }

    @Override
    public void audit(List<AuditEntry> entries) throws SQLException {
        if (entries.isEmpty()) return;
        try (Connection c = DB.connect()) {
            AuditLogDAO.insertAll(c, entries);
        }
    }

    // ================================================================ appointments

    @Override
    public List<StaffAppointment> dayList(int clinicId, LocalDate date) throws SQLException {
        String sql = APPOINTMENT_SELECT + " AND t.date = ? ORDER BY t.start_time, a.app_id";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, clinicId);
            ps.setDate(2, Date.valueOf(date));
            return appointments(ps);
        }
    }

    @Override
    public Optional<StaffAppointment> appointment(int clinicId, int appId) throws SQLException {
        String sql = APPOINTMENT_SELECT + " AND a.app_id = ?";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, clinicId);
            ps.setInt(2, appId);
            return appointments(ps).stream().findFirst();
        }
    }

    @Override
    public boolean setOutcome(int clinicId, int appId, AppointmentStatus status, LocalDateTime now,
                              AuditEntry audit) throws SQLException {
        String sql = """
            UPDATE "Appointment" SET status = ?, attended_at = ?, updated_at = ?
            WHERE app_id = ? AND status IN ('CONFIRMED', 'COMPLETED', 'NO_SHOW')
              AND timeslot_id IN (SELECT timeslot_id FROM "Timeslot" WHERE clinic_id = ?)
            """;
        return guarded(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, status.name());
                if (status == AppointmentStatus.COMPLETED) ps.setTimestamp(2, Timestamp.valueOf(now));
                else ps.setNull(2, Types.TIMESTAMP);
                ps.setTimestamp(3, Timestamp.valueOf(now));
                ps.setInt(4, appId);
                ps.setInt(5, clinicId);
                if (ps.executeUpdate() != 1) throw new NothingChanged();
            }
            AuditLogDAO.insert(c, audit);
            return true;
        });
    }

    @Override
    public boolean setStaffNotes(int clinicId, int appId, String notes, LocalDateTime now, AuditEntry audit)
            throws SQLException {
        String sql = """
            UPDATE "Appointment" SET staff_notes = ?, updated_at = ?
            WHERE app_id = ? AND timeslot_id IN (SELECT timeslot_id FROM "Timeslot" WHERE clinic_id = ?)
            """;
        return guarded(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, notes);
                ps.setTimestamp(2, Timestamp.valueOf(now));
                ps.setInt(3, appId);
                ps.setInt(4, clinicId);
                if (ps.executeUpdate() != 1) throw new NothingChanged();
            }
            AuditLogDAO.insert(c, audit);
            return true;
        });
    }

    private static List<StaffAppointment> appointments(PreparedStatement ps) throws SQLException {
        List<StaffAppointment> out = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                BookingDetails details = rs.getObject("d_app_id") == null ? null : BookingDetailsDAO.map(rs);
                Date date = rs.getDate("date");
                Time start = rs.getTime("start_time");
                Time end = rs.getTime("end_time");
                ClinicBookingDetails booking = new ClinicBookingDetails(
                        rs.getInt("app_id"), rs.getInt("user_id"),
                        date == null ? null : date.toLocalDate(),
                        start == null ? null : start.toLocalTime(),
                        end == null ? null : end.toLocalTime(),
                        rs.getString("service_name"), rs.getString("status"), AppointmentDAO.notesOrNull(rs.getString("notes")), details);
                String name = bookingName(details);
                if (name == null) {
                    name = new User(rs.getInt("user_id"), rs.getString("u_email"), rs.getString("u_first"),
                            rs.getString("u_last")).getDisplayName();
                }
                out.add(new StaffAppointment(booking, name, rs.getString("staff_notes"),
                        toLocal(rs.getTimestamp("attended_at"))));
            }
        }
        return out;
    }

    private static String bookingName(BookingDetails d) {
        if (d == null) return null;
        String name = ((d.firstName() == null ? "" : d.firstName()) + " "
                + (d.lastName() == null ? "" : d.lastName())).strip();
        return name.isEmpty() ? null : name;
    }

    // ================================================================ patients

    @Override
    public boolean patientVisible(int clinicId, int patientUserId) throws SQLException {
        try (Connection c = DB.connect()) {
            return patientVisible(c, clinicId, patientUserId);
        }
    }

    private static boolean patientVisible(Connection c, int clinicId, int patientUserId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 WHERE " + PATIENT_VISIBLE)) {
            ps.setInt(1, patientUserId);
            ps.setInt(2, clinicId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static final String PATIENT_SELECT = """
        SELECT u.user_id, u.first_name, u.last_name, u.email, MAX(t.date) AS last_date,
               MAX(d.first_name) AS d_first, MAX(d.last_name) AS d_last
        FROM "Appointment" a
        JOIN "Timeslot" t ON t.timeslot_id = a.timeslot_id
        JOIN "User" u ON u.user_id = a.user_id
        LEFT JOIN "BookingDetails" d ON d.app_id = a.app_id
        WHERE t.clinic_id = ? AND COALESCE(u.role, 'PATIENT') = 'PATIENT'
        """;

    private static final String PATIENT_GROUP = " GROUP BY u.user_id, u.first_name, u.last_name, u.email ";

    @Override
    public List<StaffPatient> searchPatients(int clinicId, String query, int limit) throws SQLException {
        String sql = PATIENT_SELECT + """
             AND (LOWER(u.email) LIKE ? OR LOWER(u.first_name) LIKE ? OR LOWER(u.last_name) LIKE ?
                  OR LOWER(COALESCE(u.first_name, '') || ' ' || COALESCE(u.last_name, '')) LIKE ?
                  OR LOWER(d.first_name) LIKE ? OR LOWER(d.last_name) LIKE ?
                  OR LOWER(COALESCE(d.first_name, '') || ' ' || COALESCE(d.last_name, '')) LIKE ?)
            """ + PATIENT_GROUP + " ORDER BY last_date DESC, u.user_id LIMIT ?";
        String like = "%" + query.toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, clinicId);
            for (int i = 2; i <= 8; i++) ps.setString(i, like);
            ps.setInt(9, limit);
            return patients(ps);
        }
    }

    @Override
    public Optional<StaffPatient> patient(int clinicId, int patientUserId) throws SQLException {
        String sql = PATIENT_SELECT + " AND u.user_id = ? " + PATIENT_GROUP;
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, clinicId);
            ps.setInt(2, patientUserId);
            return patients(ps).stream().findFirst();
        }
    }

    private static List<StaffPatient> patients(PreparedStatement ps) throws SQLException {
        List<StaffPatient> out = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String first = rs.getString("first_name");
                String last = rs.getString("last_name");
                String email = rs.getString("email");
                boolean noAccountName = (first == null || first.isBlank()) && (last == null || last.isBlank());
                if (noAccountName) {
                    first = rs.getString("d_first");
                    last = rs.getString("d_last");
                }
                Date lastDate = rs.getDate("last_date");
                out.add(new StaffPatient(rs.getInt("user_id"),
                        new User(rs.getInt("user_id"), email, first, last).getDisplayName(), email,
                        lastDate == null ? null : lastDate.toLocalDate()));
            }
        }
        return out;
    }

    @Override
    public List<StaffAppointment> patientAppointments(int clinicId, int patientUserId) throws SQLException {
        String sql = APPOINTMENT_SELECT + " AND a.user_id = ? ORDER BY t.date DESC, t.start_time DESC, a.app_id DESC";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, clinicId);
            ps.setInt(2, patientUserId);
            return appointments(ps);
        }
    }

    @Override
    public List<StaffTest> patientTests(int clinicId, int patientUserId) throws SQLException {
        String sql = TEST_SELECT + " AND user_id = ? AND " + PATIENT_VISIBLE + " ORDER BY test_date DESC, test_id DESC";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, clinicId);
            ps.setInt(2, patientUserId);
            ps.setInt(3, patientUserId);
            ps.setInt(4, clinicId);
            return tests(c, ps);
        }
    }

    @Override
    public List<StaffPrescription> patientPrescriptions(int clinicId, int patientUserId) throws SQLException {
        String sql = PRESCRIPTION_SELECT + " AND p.user_id = ? AND " + PATIENT_VISIBLE
                + " ORDER BY p.start_date DESC, p.prescription_id DESC";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, clinicId);
            ps.setInt(2, patientUserId);
            ps.setInt(3, patientUserId);
            ps.setInt(4, clinicId);
            return prescriptions(ps);
        }
    }

    // ================================================================ tests

    @Override
    public Optional<StaffTest> test(int clinicId, int testId) throws SQLException {
        String sql = TEST_SELECT + " AND test_id = ?";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, clinicId);
            ps.setInt(2, testId);
            return tests(c, ps).stream().findFirst();
        }
    }

    @Override
    public int createTest(int clinicId, int patientUserId, int clinicianId, TestDraft draft, LocalDateTime now,
                          AuditEntry audit) throws SQLException {
        String sql = """
            INSERT INTO "Test" (user_id, clinic_id, app_id, clinician_id, test_type, test_date, clinician_notes,
                                treatment_instructions, created_at, updated_at)
            SELECT CAST(? AS INTEGER), CAST(? AS INTEGER), CAST(? AS INTEGER), CAST(? AS INTEGER),
                   CAST(? AS VARCHAR(50)), CAST(? AS DATE), CAST(? AS TEXT), CAST(? AS TEXT),
                   CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP)
            WHERE
            """ + PATIENT_VISIBLE;
        try {
            return inTransaction(c -> {
                int testId;
                try (PreparedStatement ps = c.prepareStatement(sql, new String[]{"test_id"})) {
                    ps.setInt(1, patientUserId);
                    ps.setInt(2, clinicId);
                    setInt(ps, 3, draft.appId());
                    ps.setInt(4, clinicianId);
                    ps.setString(5, draft.testType());
                    setDate(ps, 6, draft.testDate());
                    ps.setString(7, draft.clinicianNotes());
                    ps.setString(8, draft.treatmentInstructions());
                    ps.setTimestamp(9, Timestamp.valueOf(now));
                    ps.setTimestamp(10, Timestamp.valueOf(now));
                    ps.setInt(11, patientUserId);
                    ps.setInt(12, clinicId);
                    if (ps.executeUpdate() != 1) throw new NothingChanged();
                    testId = generatedId(ps);
                }
                for (TestResult r : draft.results()) insertResult(c, testId, r, now);
                AuditLogDAO.insert(c, withEntityId(audit, testId));
                return testId;
            });
        } catch (NothingChanged e) {
            return 0;
        }
    }

    @Override
    public boolean updateTest(int clinicId, int testId, TestDraft draft, LocalDateTime now, AuditEntry audit)
            throws SQLException {
        String sql = """
            UPDATE "Test" SET app_id = ?, test_type = ?, test_date = ?, clinician_notes = ?,
                              treatment_instructions = ?, updated_at = ?
            WHERE test_id = ? AND clinic_id = ?
            """;
        return guarded(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                setInt(ps, 1, draft.appId());
                ps.setString(2, draft.testType());
                setDate(ps, 3, draft.testDate());
                ps.setString(4, draft.clinicianNotes());
                ps.setString(5, draft.treatmentInstructions());
                ps.setTimestamp(6, Timestamp.valueOf(now));
                ps.setInt(7, testId);
                ps.setInt(8, clinicId);
                if (ps.executeUpdate() != 1) throw new NothingChanged();
            }
            Map<String, StaffTest.Result> existing = new HashMap<>();
            for (StaffTest.Result r : results(c, testId)) existing.put(key(r.condition()), r);
            Set<String> kept = new HashSet<>();
            for (TestResult r : draft.results()) {
                String k = key(r.condition());
                kept.add(k);
                StaffTest.Result old = existing.get(k);
                if (old == null) {
                    insertResult(c, testId, r, now);
                } else if (old.result() != r.result()) {
                    // updated_at moves only on a real change: it is what re-alerts the patient
                    try (PreparedStatement ps = c.prepareStatement(
                            "UPDATE \"TestResult\" SET result = ?, updated_at = ? WHERE result_id = ? AND test_id = ?")) {
                        ps.setString(1, r.result().name());
                        ps.setTimestamp(2, Timestamp.valueOf(now));
                        ps.setInt(3, old.resultId());
                        ps.setInt(4, testId);
                        ps.executeUpdate();
                    }
                }
            }
            for (Map.Entry<String, StaffTest.Result> e : existing.entrySet()) {
                if (kept.contains(e.getKey())) continue;
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM \"TestResult\" WHERE result_id = ? AND test_id = ?")) {
                    ps.setInt(1, e.getValue().resultId());
                    ps.setInt(2, testId);
                    ps.executeUpdate();
                }
            }
            AuditLogDAO.insert(c, audit);
            return true;
        });
    }

    @Override
    public boolean releaseTest(int clinicId, int testId, LocalDateTime now, AuditEntry audit) throws SQLException {
        String sql = """
            UPDATE "Test" SET released_at = ?, updated_at = ?
            WHERE test_id = ? AND clinic_id = ? AND released_at IS NULL
            """;
        return guarded(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setTimestamp(1, Timestamp.valueOf(now));
                ps.setTimestamp(2, Timestamp.valueOf(now));
                ps.setInt(3, testId);
                ps.setInt(4, clinicId);
                if (ps.executeUpdate() != 1) throw new NothingChanged();
            }
            AuditLogDAO.insert(c, audit);
            return true;
        });
    }

    private static String key(String condition) {
        return condition == null ? "" : condition.strip().toLowerCase(Locale.ROOT);
    }

    private static void insertResult(Connection c, int testId, TestResult r, LocalDateTime now) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO "TestResult" (test_id, condition, result, created_at, updated_at) VALUES (?, ?, ?, ?, ?)
                """)) {
            ps.setInt(1, testId);
            ps.setString(2, r.condition().strip());
            ps.setString(3, r.result().name());
            ps.setTimestamp(4, Timestamp.valueOf(now));
            ps.setTimestamp(5, Timestamp.valueOf(now));
            ps.executeUpdate();
        }
    }

    private static List<StaffTest> tests(Connection c, PreparedStatement ps) throws SQLException {
        List<StaffTest> out = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Date testDate = rs.getDate("test_date");
                out.add(new StaffTest(rs.getInt("test_id"), rs.getInt("user_id"), rs.getInt("clinic_id"),
                        getInt(rs, "app_id"), rs.getString("test_type"),
                        testDate == null ? null : testDate.toLocalDate(),
                        rs.getString("clinician_notes"), rs.getString("treatment_instructions"),
                        getInt(rs, "clinician_id"), toLocal(rs.getTimestamp("released_at")), List.of()));
            }
        }
        List<StaffTest> withResults = new ArrayList<>();
        for (StaffTest t : out) {
            withResults.add(new StaffTest(t.testId(), t.patientUserId(), t.clinicId(), t.appId(), t.testType(),
                    t.testDate(), t.clinicianNotes(), t.treatmentInstructions(), t.clinicianId(), t.releasedAt(),
                    results(c, t.testId())));
        }
        return withResults;
    }

    private static List<StaffTest.Result> results(Connection c, int testId) throws SQLException {
        List<StaffTest.Result> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT result_id, condition, result, updated_at FROM \"TestResult\" WHERE test_id = ? ORDER BY result_id")) {
            ps.setInt(1, testId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new StaffTest.Result(rs.getInt("result_id"), rs.getString("condition"),
                            parseResult(rs.getString("result")), toLocal(rs.getTimestamp("updated_at"))));
                }
            }
        }
        return out;
    }

    static TestResultStatus parseResult(String result) {
        if (result == null) return TestResultStatus.PENDING;
        return switch (result.strip().toUpperCase(Locale.ROOT)) {
            case "POSITIVE" -> TestResultStatus.POSITIVE;
            case "NEGATIVE" -> TestResultStatus.NEGATIVE;
            default -> TestResultStatus.PENDING;
        };
    }

    // ================================================================ prescriptions

    @Override
    public List<Medicine> medicines() throws SQLException {
        List<Medicine> out = new ArrayList<>();
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement("SELECT medicine_id, name FROM \"Medicine\" ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) out.add(new Medicine(rs.getInt(1), rs.getString(2)));
        }
        return out;
    }

    @Override
    public Optional<StaffPrescription> prescription(int clinicId, int prescriptionId) throws SQLException {
        String sql = PRESCRIPTION_SELECT + " AND p.prescription_id = ?";
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, clinicId);
            ps.setInt(2, prescriptionId);
            return prescriptions(ps).stream().findFirst();
        }
    }

    @Override
    public int prescribe(int clinicId, int patientUserId, int clinicianId, PrescriptionDraft d, LocalDateTime now,
                         AuditEntry audit) throws SQLException {
        String sql = """
            INSERT INTO "Prescription" (user_id, clinic_id, medicine_id, test_id, dosage, doses_per_day,
                days_between_doses, start_date, end_date, instructions, actively_taking, prescribing_clinician_id,
                created_at, updated_at)
            SELECT CAST(? AS INTEGER), CAST(? AS INTEGER), CAST(? AS INTEGER), CAST(? AS INTEGER),
                   CAST(? AS VARCHAR(50)), CAST(? AS INTEGER), CAST(? AS INTEGER), CAST(? AS DATE), CAST(? AS DATE),
                   CAST(? AS TEXT), TRUE, CAST(? AS INTEGER), CAST(? AS TIMESTAMP), CAST(? AS TIMESTAMP)
            WHERE
            """ + PATIENT_VISIBLE;
        try {
            return inTransaction(c -> {
                int id;
                try (PreparedStatement ps = c.prepareStatement(sql, new String[]{"prescription_id"})) {
                    ps.setInt(1, patientUserId);
                    ps.setInt(2, clinicId);
                    ps.setInt(3, d.medicineId());
                    setInt(ps, 4, d.testId());
                    ps.setString(5, d.dosage());
                    setInt(ps, 6, d.dosesPerDay());
                    setInt(ps, 7, d.daysBetweenDoses());
                    setDate(ps, 8, d.startDate());
                    setDate(ps, 9, d.endDate());
                    ps.setString(10, d.instructions());
                    ps.setInt(11, clinicianId);
                    ps.setTimestamp(12, Timestamp.valueOf(now));
                    ps.setTimestamp(13, Timestamp.valueOf(now));
                    ps.setInt(14, patientUserId);
                    ps.setInt(15, clinicId);
                    if (ps.executeUpdate() != 1) throw new NothingChanged();
                    id = generatedId(ps);
                }
                AuditLogDAO.insert(c, withEntityId(audit, id));
                return id;
            });
        } catch (NothingChanged e) {
            return 0;
        }
    }

    @Override
    public boolean stopPrescription(int clinicId, int prescriptionId, LocalDate today, LocalDateTime now,
                                    AuditEntry audit) throws SQLException {
        String sql = """
            UPDATE "Prescription"
            SET actively_taking = FALSE,
                end_date = CASE WHEN end_date IS NULL OR end_date > ? THEN ? ELSE end_date END,
                updated_at = ?
            WHERE prescription_id = ? AND clinic_id = ? AND (actively_taking IS NULL OR actively_taking = TRUE)
            """;
        return guarded(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setDate(1, Date.valueOf(today));
                ps.setDate(2, Date.valueOf(today));
                ps.setTimestamp(3, Timestamp.valueOf(now));
                ps.setInt(4, prescriptionId);
                ps.setInt(5, clinicId);
                if (ps.executeUpdate() != 1) throw new NothingChanged();
            }
            AuditLogDAO.insert(c, audit);
            return true;
        });
    }

    private static List<StaffPrescription> prescriptions(PreparedStatement ps) throws SQLException {
        List<StaffPrescription> out = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Date start = rs.getDate("start_date");
                Date end = rs.getDate("end_date");
                Object taking = rs.getObject("actively_taking");
                out.add(new StaffPrescription(rs.getInt("prescription_id"), rs.getInt("user_id"),
                        rs.getInt("clinic_id"), rs.getInt("medicine_id"), rs.getString("medicine_name"),
                        getInt(rs, "test_id"), rs.getString("dosage"), getInt(rs, "doses_per_day"),
                        getInt(rs, "days_between_doses"),
                        start == null ? null : start.toLocalDate(), end == null ? null : end.toLocalDate(),
                        rs.getString("instructions"), taking == null || rs.getBoolean("actively_taking"),
                        getInt(rs, "prescribing_clinician_id")));
            }
        }
        return out;
    }

    // ================================================================ clinic admin

    @Override
    public List<StaffMember> staff(int clinicId) throws SQLException {
        return members(clinicId, StaffAccountDAO.ACTIVE);
    }

    @Override
    public List<StaffMember> pendingStaff(int clinicId) throws SQLException {
        return members(clinicId, StaffAccountDAO.PENDING);
    }

    @Override
    public boolean approveStaff(int clinicId, int staffUserId, int byUserId, LocalDateTime now, AuditEntry audit)
            throws SQLException {
        return guarded(c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE "StaffClinic" SET status = 'ACTIVE', approved_by = ?, approved_at = ?
                    WHERE clinic_id = ? AND user_id = ? AND status = 'PENDING'
                    """)) {
                ps.setInt(1, byUserId);
                ps.setTimestamp(2, Timestamp.valueOf(now));
                ps.setInt(3, clinicId);
                ps.setInt(4, staffUserId);
                if (ps.executeUpdate() != 1) throw new NothingChanged();
            }
            AuditLogDAO.insert(c, audit);
            return true;
        });
    }

    @Override
    public boolean rejectStaff(int clinicId, int staffUserId, AuditEntry audit) throws SQLException {
        return guarded(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM \"StaffClinic\" WHERE clinic_id = ? AND user_id = ? AND status = 'PENDING'")) {
                ps.setInt(1, clinicId);
                ps.setInt(2, staffUserId);
                if (ps.executeUpdate() != 1) throw new NothingChanged();
            }
            AuditLogDAO.insert(c, audit);
            return true;
        });
    }

    private List<StaffMember> members(int clinicId, String status) throws SQLException {
        String sql = """
            SELECT u.user_id, u.email, u.first_name, u.last_name, sc.role
            FROM "StaffClinic" sc JOIN "User" u ON u.user_id = sc.user_id
            WHERE sc.clinic_id = ? AND sc.status = ?
            ORDER BY u.email
            """;
        List<StaffMember> out = new ArrayList<>();
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, clinicId);
            ps.setString(2, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    User u = new User(rs.getInt("user_id"), rs.getString("email"), rs.getString("first_name"),
                            rs.getString("last_name"));
                    out.add(new StaffMember(u.userId(), u.email(), u.getDisplayName(),
                            StaffRole.fromDb(rs.getString("role"))));
                }
            }
        }
        return out;
    }

    @Override
    public boolean removeStaff(int clinicId, int staffUserId, AuditEntry audit) throws SQLException {
        return guarded(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM \"StaffClinic\" WHERE clinic_id = ? AND user_id = ? AND status = 'ACTIVE'")) {
                ps.setInt(1, clinicId);
                ps.setInt(2, staffUserId);
                if (ps.executeUpdate() != 1) throw new NothingChanged();
            }
            AuditLogDAO.insert(c, audit);
            return true;
        });
    }

    @Override
    public boolean setRole(int clinicId, int staffUserId, StaffRole role, AuditEntry audit) throws SQLException {
        if (role == null || !role.isStaff()) return false;
        // Only this clinic's membership changes; the person's role at their other clinics stays.
        String sql = """
            UPDATE "StaffClinic" SET role = ?
            WHERE user_id = ? AND clinic_id = ? AND status = 'ACTIVE'
              AND EXISTS (SELECT 1 FROM "User" u WHERE u.user_id = ? AND u.role IN ('CLINICIAN', 'CLINIC_ADMIN'))
            """;
        return guarded(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setString(1, role.name());
                ps.setInt(2, staffUserId);
                ps.setInt(3, clinicId);
                ps.setInt(4, staffUserId);
                if (ps.executeUpdate() != 1) throw new NothingChanged();
            }
            AuditLogDAO.insert(c, audit);
            return true;
        });
    }

    @Override
    public void setStaffCodeHash(int clinicId, String codeHash, int byUserId, LocalDateTime now, AuditEntry audit)
            throws SQLException {
        inTransaction(c -> {
            int updated;
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE \"ClinicStaffCode\" SET code_hash = ?, rotated_at = ?, rotated_by = ?, code_is_demo = FALSE"
                            + " WHERE clinic_id = ?")) {
                ps.setString(1, codeHash);
                ps.setTimestamp(2, Timestamp.valueOf(now));
                ps.setInt(3, byUserId);
                ps.setInt(4, clinicId);
                updated = ps.executeUpdate();
            }
            if (updated == 0) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO \"ClinicStaffCode\" (clinic_id, code_hash, rotated_at, rotated_by, code_is_demo)"
                                + " VALUES (?, ?, ?, ?, FALSE)")) {
                    ps.setInt(1, clinicId);
                    ps.setString(2, codeHash);
                    ps.setTimestamp(3, Timestamp.valueOf(now));
                    ps.setInt(4, byUserId);
                    ps.executeUpdate();
                }
            }
            AuditLogDAO.insert(c, audit);
            return null;
        });
    }

    @Override
    public Optional<Boolean> staffCodeIsDemo(int clinicId) throws SQLException {
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT code_is_demo FROM \"ClinicStaffCode\" WHERE clinic_id = ?")) {
            ps.setInt(1, clinicId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(rs.getBoolean(1));
            }
        }
    }

    private static final String TIMESLOT_SELECT = """
        SELECT t.timeslot_id, t.clinic_id, t.service_id, s.name AS service_name, t.date, t.start_time, t.end_time,
               t.capacity, t.booked_count
        FROM "Timeslot" t LEFT JOIN "Service" s ON s.service_id = t.service_id
        WHERE t.clinic_id = ?
        """;

    @Override
    public List<StaffTimeslot> timeslots(int clinicId, LocalDate date) throws SQLException {
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(TIMESLOT_SELECT + " AND t.date = ? ORDER BY t.start_time, t.timeslot_id")) {
            ps.setInt(1, clinicId);
            ps.setDate(2, Date.valueOf(date));
            return timeslots(ps);
        }
    }

    @Override
    public Optional<StaffTimeslot> timeslot(int clinicId, int timeslotId) throws SQLException {
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(TIMESLOT_SELECT + " AND t.timeslot_id = ?")) {
            ps.setInt(1, clinicId);
            ps.setInt(2, timeslotId);
            return timeslots(ps).stream().findFirst();
        }
    }

    @Override
    public boolean clinicOffersService(int clinicId, int serviceId) throws SQLException {
        try (Connection c = DB.connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT 1 FROM \"ClinicService\" WHERE clinic_id = ? AND service_id = ?")) {
            ps.setInt(1, clinicId);
            ps.setInt(2, serviceId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public int createTimeslot(int clinicId, int serviceId, LocalDate date, LocalTime start, LocalTime end,
                              int capacity, AuditEntry audit) throws SQLException {
        return inTransaction(c -> {
            int id;
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO "Timeslot" (date, start_time, end_time, service_id, clinic_id, capacity, booked_count)
                    VALUES (?, ?, ?, ?, ?, ?, 0)
                    """, new String[]{"timeslot_id"})) {
                ps.setDate(1, Date.valueOf(date));
                ps.setTime(2, Time.valueOf(start));
                ps.setTime(3, Time.valueOf(end));
                ps.setInt(4, serviceId);
                ps.setInt(5, clinicId);
                ps.setInt(6, capacity);
                ps.executeUpdate();
                id = generatedId(ps);
            }
            AuditLogDAO.insert(c, withEntityId(audit, id));
            return id;
        });
    }

    @Override
    public boolean updateTimeslot(int clinicId, int timeslotId, LocalDate date, LocalTime start, LocalTime end,
                                  int capacity, AuditEntry audit) throws SQLException {
        String sql = """
            UPDATE "Timeslot" SET date = ?, start_time = ?, end_time = ?, capacity = ?
            WHERE timeslot_id = ? AND clinic_id = ? AND COALESCE(booked_count, 0) <= ?
            """;
        return guarded(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setDate(1, Date.valueOf(date));
                ps.setTime(2, Time.valueOf(start));
                ps.setTime(3, Time.valueOf(end));
                ps.setInt(4, capacity);
                ps.setInt(5, timeslotId);
                ps.setInt(6, clinicId);
                ps.setInt(7, capacity);
                if (ps.executeUpdate() != 1) throw new NothingChanged();
            }
            AuditLogDAO.insert(c, audit);
            return true;
        });
    }

    private static List<StaffTimeslot> timeslots(PreparedStatement ps) throws SQLException {
        List<StaffTimeslot> out = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Date date = rs.getDate("date");
                Time start = rs.getTime("start_time");
                Time end = rs.getTime("end_time");
                out.add(new StaffTimeslot(rs.getInt("timeslot_id"), rs.getInt("clinic_id"), rs.getInt("service_id"),
                        rs.getString("service_name"), date == null ? null : date.toLocalDate(),
                        start == null ? null : start.toLocalTime(), end == null ? null : end.toLocalTime(),
                        rs.getInt("capacity"), rs.getInt("booked_count")));
            }
        }
        return out;
    }

    // ================================================================ helpers

    private static AuditEntry withEntityId(AuditEntry a, int id) {
        return new AuditEntry(a.staffUserId(), a.clinicId(), a.action(), a.entity(), id, a.patientUserId());
    }

    private static int generatedId(PreparedStatement ps) throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            if (!keys.next()) throw new SQLException("No id generated");
            return keys.getInt(1);
        }
    }

    private static LocalDateTime toLocal(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }

    private static Integer getInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private static void setInt(PreparedStatement ps, int i, Integer v) throws SQLException {
        if (v == null) ps.setNull(i, Types.INTEGER);
        else ps.setInt(i, v);
    }

    private static void setDate(PreparedStatement ps, int i, LocalDate d) throws SQLException {
        if (d == null) ps.setNull(i, Types.DATE);
        else ps.setDate(i, Date.valueOf(d));
    }
}
