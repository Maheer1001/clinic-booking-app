package controller;

import model.StaffAccess;
import view.Navigator;
import view.staff.StaffTabs;

import java.util.ArrayList;
import java.util.List;

/** Where a successful login goes, and which tabs staff get. Pure, so it is unit tested. */
public final class LoginRouting {

    private LoginRouting() {}

    public enum Route {
        /** The patient app (Booking / Tracking / Reminders). */
        PATIENT,
        /** The staff tabs. */
        STAFF,
        /** Staff who must choose a new password first (e.g. a seeded clinic admin), then the staff tabs. */
        STAFF_MUST_RESET,
        /** Staff with no clinic (waiting for approval, or removed): a waiting page with Log out only. */
        STAFF_PENDING
    }

    /** Tab ids of the staff app. */
    public static final String TODAY = "today", PATIENTS = "patients", CLINIC = "clinic";

    public static Route route(StaffAccess access) {
        if (access == null || !access.isStaff()) return Route.PATIENT;
        // a staff account that is tied to no clinic sees nothing, not even the password step
        if (access.clinicIds().isEmpty()) return Route.STAFF_PENDING;
        if (access.mustResetPassword()) return Route.STAFF_MUST_RESET;
        return Route.STAFF;
    }

    /**
     * Today and Patients for every staff member; Clinic (admin tools) for anyone who is a clinic admin
     * at one or more of their clinics. The Clinic page itself follows the clinic switcher: at a clinic
     * where they are only a clinician it shows a note instead of the tools (and the service refuses).
     */
    public static List<Navigator.Tab> staffTabs(StaffAccess access) {
        List<Navigator.Tab> tabs = new ArrayList<>();
        tabs.add(Navigator.Tab.of(TODAY, "Today", nav -> StaffTabs.today(nav, access)));
        tabs.add(Navigator.Tab.of(PATIENTS, "Patients", nav -> StaffTabs.patients(nav, access)));
        if (access.isAdminAnywhere()) {
            tabs.add(Navigator.Tab.of(CLINIC, "Clinic", nav -> StaffTabs.clinic(nav, access)));
        }
        return List.copyOf(tabs);
    }
}
