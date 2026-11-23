package model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Who a user is to the staff side, read at login (and re-read by every staff action).
 *
 * <p>{@code role} is the account-level "User".role and only says patient or staff (it routes the
 * login). What a staff member may do is decided per clinic by {@code clinicRoles}: the role on each
 * ACTIVE "StaffClinic" membership, so someone can be CLINIC_ADMIN at one clinic and CLINICIAN at
 * another. Always ask {@link #roleAt} / {@link #isAdminAt} for the clinic being acted on.
 * Patients have role PATIENT and no clinics.
 */
public record StaffAccess(int userId, StaffRole role, Map<Integer, StaffRole> clinicRoles, boolean mustResetPassword) {

    public StaffAccess {
        role = role == null ? StaffRole.PATIENT : role;
        Map<Integer, StaffRole> sorted = new TreeMap<>();
        if (clinicRoles != null) {
            clinicRoles.forEach((clinic, r) -> {
                // PATIENT (or an unknown value) on a membership grants nothing: the clinic is left out
                if (clinic != null && r != null && r.isStaff()) sorted.put(clinic, r);
            });
        }
        clinicRoles = Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    /**
     * Every listed clinic gets the same role (tests and callers that only know one role). Real
     * logins come from {@code UserDAO.getStaffAccess}, which reads each membership's own role.
     */
    public StaffAccess(int userId, StaffRole role, List<Integer> clinicIds, boolean mustResetPassword) {
        this(userId, role, sameRole(clinicIds, role), mustResetPassword);
    }

    private static Map<Integer, StaffRole> sameRole(List<Integer> clinicIds, StaffRole role) {
        Map<Integer, StaffRole> out = new LinkedHashMap<>();
        if (clinicIds != null && role != null) for (Integer id : clinicIds) out.put(id, role);
        return out;
    }

    /** A staff account (not a patient). Says nothing about any clinic. */
    public boolean isStaff() {
        return role.isStaff();
    }

    /** The clinics with an ACTIVE membership, lowest id first. Empty for patients. */
    public List<Integer> clinicIds() {
        return isStaff() ? List.copyOf(clinicRoles.keySet()) : List.of();
    }

    public boolean worksAt(int clinicId) {
        return isStaff() && clinicRoles.containsKey(clinicId);
    }

    /** The role at this clinic; PATIENT (no access) if they do not work there. */
    public StaffRole roleAt(int clinicId) {
        return worksAt(clinicId) ? clinicRoles.get(clinicId) : StaffRole.PATIENT;
    }

    /** CLINIC_ADMIN at this clinic (being an admin somewhere else does not count). */
    public boolean isAdminAt(int clinicId) {
        return roleAt(clinicId) == StaffRole.CLINIC_ADMIN;
    }

    /** CLINIC_ADMIN at one or more clinics: decides whether the Clinic tab exists at all. */
    public boolean isAdminAnywhere() {
        return isStaff() && clinicRoles.containsValue(StaffRole.CLINIC_ADMIN);
    }
}
