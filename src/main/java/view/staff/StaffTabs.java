package view.staff;

import javafx.scene.Node;
import model.StaffAccess;
import view.Navigator;
import view.ui.UI;

/** Home pages of the staff tabs (placeholders until the staff screens land). */
public final class StaffTabs {

    private StaffTabs() {}

    public static Node today(Navigator nav, StaffAccess access) {
        return placeholder("Today");
    }

    public static Node patients(Navigator nav, StaffAccess access) {
        return placeholder("Patients");
    }

    public static Node clinic(Navigator nav, StaffAccess access) {
        return placeholder("Clinic");
    }

    private static Node placeholder(String heading) {
        return UI.pageTop(UI.heading(heading), UI.body("Coming soon"));
    }
}
