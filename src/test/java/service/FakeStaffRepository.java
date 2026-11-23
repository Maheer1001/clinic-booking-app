package service;

import dao.StaffRepository;
import model.AppointmentStatus;
import model.AuditEntry;
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

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory {@link StaffRepository} that behaves like the SQL: everything is scoped by clinic. */
public class FakeStaffRepository implements StaffRepository {

    final Map<Integer, StaffAccess> access = new HashMap<>();
    /** appId -> clinicId */
    final Map<Integer, Integer> appClinic = new HashMap<>();
    final Map<Integer, StaffAppointment> appointments = new HashMap<>();
    final Map<Integer, StaffTest> tests = new HashMap<>();
    final Map<Integer, StaffPrescription> prescriptions = new HashMap<>();
    final Map<Integer, List<StaffMember>> staff = new HashMap<>();
    final Map<Integer, StaffTimeslot> timeslots = new HashMap<>();
    final Map<Integer, String> codeHashes = new HashMap<>();
    public final List<AuditEntry> audits = new ArrayList<>();
    final List<Medicine> medicines = List.of(new Medicine(1, "Doxycycline"));
    boolean failAudit;
    private int nextId = 1000;

    /** A staff account with the same role at each of {@code clinics}. */
    public void addStaff(int userId, StaffRole role, Integer... clinics) {
        access.put(userId, new StaffAccess(userId, role, List.of(clinics), false));
        for (int c : clinics) {
            staff.computeIfAbsent(c, k -> new ArrayList<>())
                    .add(new StaffMember(userId, "s" + userId + "@x.test", "Staff " + userId, role));
        }
    }

    /** One more ACTIVE membership with its own role (roles are per clinic, like the SQL). */
    public void addMembership(int userId, int clinicId, StaffRole role) {
        StaffAccess a = access.get(userId);
        Map<Integer, StaffRole> roles = new HashMap<>(a == null ? Map.of() : a.clinicRoles());
        roles.put(clinicId, role);
        access.put(userId, new StaffAccess(userId, a == null ? role : a.role(), roles, false));
        staff.computeIfAbsent(clinicId, k -> new ArrayList<>())
                .add(new StaffMember(userId, "s" + userId + "@x.test", "Staff " + userId, role));
    }

    private void putClinicRole(int userId, int clinicId, StaffRole role) {
        StaffAccess a = access.get(userId);
        Map<Integer, StaffRole> roles = new HashMap<>(a.clinicRoles());
        if (role == null) roles.remove(clinicId);
        else roles.put(clinicId, role);
        access.put(userId, new StaffAccess(userId, a.role(), roles, false));
    }

    final Map<Integer, List<StaffMember>> pending = new HashMap<>();

    /** A staff sign-up waiting for approval: like the SQL, access lists no clinic for it. */
    public void addPending(int userId, int clinicId) {
        access.putIfAbsent(userId, new StaffAccess(userId, StaffRole.CLINICIAN, List.of(), false));
        pending.computeIfAbsent(clinicId, k -> new ArrayList<>())
                .add(new StaffMember(userId, "p" + userId + "@x.test", "Pending " + userId, StaffRole.CLINICIAN));
    }

    @Override
    public List<StaffMember> pendingStaff(int clinicId) {
        return List.copyOf(pending.getOrDefault(clinicId, List.of()));
    }

    @Override
    public boolean approveStaff(int clinicId, int staffUserId, int byUserId, LocalDateTime now, AuditEntry audit)
            throws SQLException {
        List<StaffMember> list = pending.getOrDefault(clinicId, new ArrayList<>());
        Optional<StaffMember> m = list.stream().filter(x -> x.userId() == staffUserId).findFirst();
        if (m.isEmpty()) return false;
        list.remove(m.get());
        staff.computeIfAbsent(clinicId, k -> new ArrayList<>()).add(m.get());
        putClinicRole(staffUserId, clinicId, m.get().role());
        write(audit);
        return true;
    }

    @Override
    public boolean rejectStaff(int clinicId, int staffUserId, AuditEntry audit) throws SQLException {
        boolean removed = pending.getOrDefault(clinicId, new ArrayList<>()).removeIf(m -> m.userId() == staffUserId);
        if (removed) write(audit);
        return removed;
    }

    public void addAppointment(int appId, int clinicId, int patientId, LocalDate date, String status) {
        appClinic.put(appId, clinicId);
        appointments.put(appId, new StaffAppointment(new ClinicBookingDetails(appId, patientId, date,
                LocalTime.of(10, 0), LocalTime.of(10, 30), "STI test", status, null, null),
                "Patient " + patientId, null, null));
    }

    @Override
    public Optional<StaffAccess> access(int userId) {
        return Optional.ofNullable(access.get(userId));
    }

    @Override
    public void audit(List<AuditEntry> entries) throws SQLException {
        if (failAudit) throw new SQLException("audit down", "08000");
        audits.addAll(entries);
    }

    private void write(AuditEntry e) throws SQLException {
        audit(List.of(e));
    }

    @Override
    public List<StaffAppointment> dayList(int clinicId, LocalDate date) {
        return appointments.values().stream()
                .filter(a -> appClinic.get(a.appId()) == clinicId && date.equals(a.booking().date())).toList();
    }

    @Override
    public Optional<StaffAppointment> appointment(int clinicId, int appId) {
        return Optional.ofNullable(appointments.get(appId)).filter(a -> appClinic.get(appId) == clinicId);
    }

    @Override
    public boolean setOutcome(int clinicId, int appId, AppointmentStatus status, LocalDateTime now, AuditEntry audit)
            throws SQLException {
        Optional<StaffAppointment> a = appointment(clinicId, appId);
        if (a.isEmpty() || a.get().status() == AppointmentStatus.CANCELLED) return false;
        ClinicBookingDetails b = a.get().booking();
        appointments.put(appId, new StaffAppointment(new ClinicBookingDetails(b.appId(), b.userId(), b.date(),
                b.startTime(), b.endTime(), b.serviceName(), status.name(), b.notes(), b.details()),
                a.get().patientName(), a.get().staffNotes(), status == AppointmentStatus.COMPLETED ? now : null));
        write(audit);
        return true;
    }

    @Override
    public boolean setStaffNotes(int clinicId, int appId, String notes, LocalDateTime now, AuditEntry audit)
            throws SQLException {
        Optional<StaffAppointment> a = appointment(clinicId, appId);
        if (a.isEmpty()) return false;
        appointments.put(appId, new StaffAppointment(a.get().booking(), a.get().patientName(), notes,
                a.get().attendedAt()));
        write(audit);
        return true;
    }

    @Override
    public boolean patientVisible(int clinicId, int patientUserId) {
        return appointments.values().stream()
                .anyMatch(a -> a.patientUserId() == patientUserId && appClinic.get(a.appId()) == clinicId);
    }

    @Override
    public List<StaffPatient> searchPatients(int clinicId, String query, int limit) {
        return appointments.values().stream()
                .filter(a -> appClinic.get(a.appId()) == clinicId)
                .map(StaffAppointment::patientUserId).distinct()
                .map(id -> new StaffPatient(id, "Patient " + id, "p" + id + "@x.test", null)).toList();
    }

    @Override
    public Optional<StaffPatient> patient(int clinicId, int patientUserId) {
        if (!patientVisible(clinicId, patientUserId)) return Optional.empty();
        return Optional.of(new StaffPatient(patientUserId, "Patient " + patientUserId, null, null));
    }

    @Override
    public List<StaffAppointment> patientAppointments(int clinicId, int patientUserId) {
        return appointments.values().stream()
                .filter(a -> a.patientUserId() == patientUserId && appClinic.get(a.appId()) == clinicId).toList();
    }

    @Override
    public List<StaffTest> patientTests(int clinicId, int patientUserId) {
        return tests.values().stream().filter(t -> t.clinicId() == clinicId && t.patientUserId() == patientUserId).toList();
    }

    @Override
    public List<StaffPrescription> patientPrescriptions(int clinicId, int patientUserId) {
        return prescriptions.values().stream()
                .filter(p -> p.clinicId() == clinicId && p.patientUserId() == patientUserId).toList();
    }

    @Override
    public Optional<StaffTest> test(int clinicId, int testId) {
        return Optional.ofNullable(tests.get(testId)).filter(t -> t.clinicId() == clinicId);
    }

    private static List<StaffTest.Result> results(TestDraft d, LocalDateTime now) {
        List<StaffTest.Result> out = new ArrayList<>();
        int i = 1;
        for (TestResult r : d.results()) out.add(new StaffTest.Result(i++, r.condition(), r.result(), now));
        return out;
    }

    @Override
    public int createTest(int clinicId, int patientUserId, int clinicianId, TestDraft d, LocalDateTime now,
                          AuditEntry audit) throws SQLException {
        if (!patientVisible(clinicId, patientUserId)) return 0;
        int id = nextId++;
        tests.put(id, new StaffTest(id, patientUserId, clinicId, d.appId(), d.testType(), d.testDate(),
                d.clinicianNotes(), d.treatmentInstructions(), clinicianId, null, results(d, now)));
        write(new AuditEntry(audit.staffUserId(), audit.clinicId(), audit.action(), audit.entity(), id,
                audit.patientUserId()));
        return id;
    }

    @Override
    public boolean updateTest(int clinicId, int testId, TestDraft d, LocalDateTime now, AuditEntry audit)
            throws SQLException {
        Optional<StaffTest> t = test(clinicId, testId);
        if (t.isEmpty()) return false;
        StaffTest o = t.get();
        tests.put(testId, new StaffTest(testId, o.patientUserId(), clinicId, d.appId(), d.testType(), d.testDate(),
                d.clinicianNotes(), d.treatmentInstructions(), o.clinicianId(), o.releasedAt(), results(d, now)));
        write(audit);
        return true;
    }

    @Override
    public boolean releaseTest(int clinicId, int testId, LocalDateTime now, AuditEntry audit) throws SQLException {
        Optional<StaffTest> t = test(clinicId, testId);
        if (t.isEmpty() || t.get().released()) return false;
        StaffTest o = t.get();
        tests.put(testId, new StaffTest(testId, o.patientUserId(), clinicId, o.appId(), o.testType(), o.testDate(),
                o.clinicianNotes(), o.treatmentInstructions(), o.clinicianId(), now, o.results()));
        write(audit);
        return true;
    }

    @Override
    public List<Medicine> medicines() {
        return medicines;
    }

    @Override
    public Optional<StaffPrescription> prescription(int clinicId, int prescriptionId) {
        return Optional.ofNullable(prescriptions.get(prescriptionId)).filter(p -> p.clinicId() == clinicId);
    }

    @Override
    public int prescribe(int clinicId, int patientUserId, int clinicianId, PrescriptionDraft d, LocalDateTime now,
                         AuditEntry audit) throws SQLException {
        if (!patientVisible(clinicId, patientUserId)) return 0;
        int id = nextId++;
        prescriptions.put(id, new StaffPrescription(id, patientUserId, clinicId, d.medicineId(), "Doxycycline",
                d.testId(), d.dosage(), d.dosesPerDay(), d.daysBetweenDoses(), d.startDate(), d.endDate(),
                d.instructions(), true, clinicianId));
        write(new AuditEntry(audit.staffUserId(), audit.clinicId(), audit.action(), audit.entity(), id,
                audit.patientUserId()));
        return id;
    }

    @Override
    public boolean stopPrescription(int clinicId, int prescriptionId, LocalDate today, LocalDateTime now,
                                    AuditEntry audit) throws SQLException {
        Optional<StaffPrescription> p = prescription(clinicId, prescriptionId);
        if (p.isEmpty() || !p.get().active()) return false;
        StaffPrescription o = p.get();
        prescriptions.put(prescriptionId, new StaffPrescription(o.prescriptionId(), o.patientUserId(), o.clinicId(),
                o.medicineId(), o.medicineName(), o.testId(), o.dosage(), o.dosesPerDay(), o.daysBetweenDoses(),
                o.startDate(), today, o.instructions(), false, o.prescribingClinicianId()));
        write(audit);
        return true;
    }

    @Override
    public List<StaffMember> staff(int clinicId) {
        return List.copyOf(staff.getOrDefault(clinicId, List.of()));
    }

    @Override
    public boolean removeStaff(int clinicId, int staffUserId, AuditEntry audit) throws SQLException {
        boolean removed = staff.getOrDefault(clinicId, new ArrayList<>()).removeIf(m -> m.userId() == staffUserId);
        if (removed) {
            putClinicRole(staffUserId, clinicId, null);
            write(audit);
        }
        return removed;
    }

    @Override
    public boolean setRole(int clinicId, int staffUserId, StaffRole role, AuditEntry audit) throws SQLException {
        List<StaffMember> list = staff.getOrDefault(clinicId, new ArrayList<>());
        for (int i = 0; i < list.size(); i++) {
            StaffMember m = list.get(i);
            if (m.userId() == staffUserId) {
                list.set(i, new StaffMember(m.userId(), m.email(), m.displayName(), role));
                // this clinic's membership only, like the SQL
                putClinicRole(staffUserId, clinicId, role);
                write(audit);
                return true;
            }
        }
        return false;
    }

    final java.util.Set<Integer> demoCodes = new java.util.HashSet<>();

    @Override
    public Optional<Boolean> staffCodeIsDemo(int clinicId) {
        if (!codeHashes.containsKey(clinicId)) return Optional.empty();
        return Optional.of(demoCodes.contains(clinicId));
    }

    @Override
    public void setStaffCodeHash(int clinicId, String codeHash, int byUserId, LocalDateTime now, AuditEntry audit)
            throws SQLException {
        codeHashes.put(clinicId, codeHash);
        demoCodes.remove(clinicId);
        write(audit);
    }

    @Override
    public List<StaffTimeslot> timeslots(int clinicId, LocalDate date) {
        return timeslots.values().stream().filter(t -> t.clinicId() == clinicId && t.date().equals(date)).toList();
    }

    @Override
    public Optional<StaffTimeslot> timeslot(int clinicId, int timeslotId) {
        return Optional.ofNullable(timeslots.get(timeslotId)).filter(t -> t.clinicId() == clinicId);
    }

    @Override
    public boolean clinicOffersService(int clinicId, int serviceId) {
        return serviceId == 1;
    }

    @Override
    public int createTimeslot(int clinicId, int serviceId, LocalDate date, LocalTime start, LocalTime end,
                              int capacity, AuditEntry audit) throws SQLException {
        int id = nextId++;
        timeslots.put(id, new StaffTimeslot(id, clinicId, serviceId, "STI test", date, start, end, capacity, 0));
        write(new AuditEntry(audit.staffUserId(), audit.clinicId(), audit.action(), audit.entity(), id, null));
        return id;
    }

    /** Booked count to apply just before the next update, to simulate a booking racing the edit. */
    Integer bookedCountRace;

    @Override
    public boolean updateTimeslot(int clinicId, int timeslotId, LocalDate date, LocalTime start, LocalTime end,
                                  int capacity, AuditEntry audit) throws SQLException {
        Optional<StaffTimeslot> t = timeslot(clinicId, timeslotId);
        if (t.isEmpty()) return false;
        StaffTimeslot o = t.get();
        int booked = bookedCountRace != null ? bookedCountRace : o.bookedCount();
        if (bookedCountRace != null) {
            timeslots.put(timeslotId, new StaffTimeslot(o.timeslotId(), o.clinicId(), o.serviceId(), o.serviceName(),
                    o.date(), o.startTime(), o.endTime(), o.capacity(), booked));
            bookedCountRace = null;
        }
        if (booked > capacity) return false; // the SQL guard
        timeslots.put(timeslotId, new StaffTimeslot(o.timeslotId(), clinicId, o.serviceId(), o.serviceName(), date,
                start, end, capacity, booked));
        write(audit);
        return true;
    }
}
