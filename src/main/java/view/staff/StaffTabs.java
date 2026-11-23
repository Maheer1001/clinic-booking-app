package view.staff;

import controller.StaffController;
import javafx.scene.Node;
import model.StaffAccess;
import view.Navigator;

/**
 * The home page of each staff tab (Today / Patients / Clinic). Pages inside a tab are opened with
 * {@link Navigator#goToPage} and left with Back. All three follow the clinic switcher
 * ({@code Data.getCurrentClinicId()}); an account with no approved clinic gets a waiting page.
 */
public final class StaffTabs {

    private StaffTabs() {}

    /** The clinic's appointments for a day (default today). */
    public static Node today(Navigator nav, StaffAccess access) {
        return today(nav, new StaffController(access));
    }

    public static Node today(Navigator nav, StaffController staff) {
        return new StaffDayView(nav, staff).page();
    }

    /** Search the clinic's patients and open a patient's record. */
    public static Node patients(Navigator nav, StaffAccess access) {
        return patients(nav, new StaffController(access));
    }

    public static Node patients(Navigator nav, StaffController staff) {
        return new StaffPatientView(nav, staff).searchPage();
    }

    /** Clinic admin: approvals, staff, staff code and timeslots. */
    public static Node clinic(Navigator nav, StaffAccess access) {
        return clinic(nav, new StaffController(access));
    }

    public static Node clinic(Navigator nav, StaffController staff) {
        return new StaffClinicView(nav, staff).page();
    }
}
