package model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StaffAccessTest {

    @Test
    void rolesArePerClinic() {
        StaffAccess a = new StaffAccess(1, StaffRole.CLINICIAN,
                Map.of(5, StaffRole.CLINICIAN, 2, StaffRole.CLINIC_ADMIN), false);
        assertEquals(List.of(2, 5), a.clinicIds(), "lowest id first");
        assertTrue(a.isAdminAt(2));
        assertFalse(a.isAdminAt(5));
        assertEquals(StaffRole.CLINICIAN, a.roleAt(5));
        assertEquals(StaffRole.PATIENT, a.roleAt(9), "no membership: no role");
        assertFalse(a.isAdminAt(9));
        assertTrue(a.isAdminAnywhere());
    }

    @Test
    void theAccountRoleGrantsNothingAtAClinic() {
        StaffAccess a = new StaffAccess(1, StaffRole.CLINIC_ADMIN, Map.of(1, StaffRole.CLINICIAN), false);
        assertFalse(a.isAdminAt(1));
        assertFalse(a.isAdminAnywhere());
    }

    @Test
    void aPatientRoleOnAMembership_orAPatientAccount_givesNoClinic() {
        Map<Integer, StaffRole> roles = new HashMap<>();
        roles.put(1, StaffRole.PATIENT);
        roles.put(2, null);
        roles.put(3, StaffRole.CLINICIAN);
        StaffAccess a = new StaffAccess(1, StaffRole.CLINICIAN, roles, false);
        assertEquals(List.of(3), a.clinicIds());
        assertFalse(a.worksAt(1));

        StaffAccess patient = new StaffAccess(2, StaffRole.PATIENT, Map.of(1, StaffRole.CLINIC_ADMIN), false);
        assertTrue(patient.clinicIds().isEmpty());
        assertFalse(patient.worksAt(1));
        assertFalse(patient.isAdminAt(1));
        assertFalse(patient.isAdminAnywhere());
    }

    @Test
    void theListConstructorGivesEveryClinicTheSameRole() {
        StaffAccess a = new StaffAccess(1, StaffRole.CLINIC_ADMIN, List.of(3, 1), false);
        assertEquals(List.of(1, 3), a.clinicIds());
        assertTrue(a.isAdminAt(1) && a.isAdminAt(3));
        assertThrows(UnsupportedOperationException.class, () -> a.clinicRoles().put(4, StaffRole.CLINIC_ADMIN));
    }
}
