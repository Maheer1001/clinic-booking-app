package model;

import java.util.List;

/**
 * A user's role and the clinics they work at, read at login (and re-read by every staff action).
 * Patients have role PATIENT and no clinics.
 */
public record StaffAccess(int userId, StaffRole role, List<Integer> clinicIds, boolean mustResetPassword) {

    public StaffAccess {
        role = role == null ? StaffRole.PATIENT : role;
        clinicIds = clinicIds == null ? List.of() : List.copyOf(clinicIds);
    }

    public boolean isStaff() {
        return role.isStaff();
    }

    public boolean isAdmin() {
        return role == StaffRole.CLINIC_ADMIN;
    }

    public boolean worksAt(int clinicId) {
        return clinicIds.contains(clinicId);
    }
}
