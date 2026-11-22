package controller;

import controller.LoginRouting.Route;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import model.StaffAccess;
import model.StaffRole;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import service.AccountSecurityService.ChangePasswordResult;
import view.Navigator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LoginRoutingTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // already started by another test class
        }
    }

    private static StaffAccess access(StaffRole role, List<Integer> clinics, boolean mustReset) {
        return new StaffAccess(42, role, clinics, mustReset);
    }

    @Test
    void patientsGetThePatientApp() {
        assertEquals(Route.PATIENT, LoginRouting.route(access(StaffRole.PATIENT, List.of(), false)));
        assertEquals(Route.PATIENT, LoginRouting.route(null));
        // a stray must_reset flag on a patient does not send them anywhere else
        assertEquals(Route.PATIENT, LoginRouting.route(access(StaffRole.PATIENT, List.of(), true)));
    }

    @Test
    void staffWithAClinicGetTheStaffApp() {
        assertEquals(Route.STAFF, LoginRouting.route(access(StaffRole.CLINICIAN, List.of(1), false)));
        assertEquals(Route.STAFF, LoginRouting.route(access(StaffRole.CLINIC_ADMIN, List.of(1, 2), false)));
    }

    @Test
    void staffWithNoClinicWaitForApproval() {
        assertEquals(Route.STAFF_PENDING, LoginRouting.route(access(StaffRole.CLINICIAN, List.of(), false)));
        assertEquals(Route.STAFF_PENDING, LoginRouting.route(access(StaffRole.CLINIC_ADMIN, List.of(), true)),
                "no clinic wins over the password step");
    }

    @Test
    void staffWhoMustResetChooseAPasswordFirst() {
        assertEquals(Route.STAFF_MUST_RESET, LoginRouting.route(access(StaffRole.CLINIC_ADMIN, List.of(3), true)));
        assertEquals(Route.STAFF_MUST_RESET, LoginRouting.route(access(StaffRole.CLINICIAN, List.of(3), true)));
    }

    private static List<String> ids(List<Navigator.Tab> tabs) {
        return tabs.stream().map(Navigator.Tab::id).toList();
    }

    @Test
    void cliniciansGetTodayAndPatients() {
        List<Navigator.Tab> tabs = LoginRouting.staffTabs(access(StaffRole.CLINICIAN, List.of(1), false));
        assertEquals(List.of("today", "patients"), ids(tabs));
        assertEquals(List.of("Today", "Patients"), tabs.stream().map(t -> t.button().getText()).toList());
    }

    @Test
    void clinicAdminsAlsoGetTheClinicTab() {
        List<Navigator.Tab> tabs = LoginRouting.staffTabs(access(StaffRole.CLINIC_ADMIN, List.of(1), false));
        assertEquals(List.of("today", "patients", "clinic"), ids(tabs));
    }

    @Test
    void staffNavigatorStartsOnTodayWithPlaceholderPages() {
        StaffAccess admin = access(StaffRole.CLINIC_ADMIN, List.of(1), false);
        Navigator nav = new Navigator(LoginRouting.staffTabs(admin), javafx.scene.layout.VBox::new);
        nav.start();
        assertEquals("today", nav.getCurrentTab());
        assertTrue(hasLabel(nav.getRoot().getCenter(), "Coming soon"));
        nav.handleTabClick("clinic");
        assertEquals("clinic", nav.getCurrentTab());
        assertThrows(IllegalArgumentException.class, () -> nav.handleTabClick("booking"));
    }

    // ---------------------------------------------------------------- StaffGate pages

    private static boolean hasLabel(Node node, String text) {
        if (node instanceof Label l && text.equals(l.getText())) return true;
        if (node instanceof javafx.scene.control.ScrollPane sp) return hasLabel(sp.getContent(), text);
        if (node instanceof Parent p) {
            for (Node child : p.getChildrenUnmodifiable()) if (hasLabel(child, text)) return true;
        }
        return false;
    }

    private static <T extends Node> List<T> find(Node node, Class<T> type) {
        List<T> out = new ArrayList<>();
        if (type.isInstance(node)) out.add(type.cast(node));
        if (node instanceof javafx.scene.control.ScrollPane sp) out.addAll(find(sp.getContent(), type));
        else if (node instanceof Parent p) for (Node c : p.getChildrenUnmodifiable()) out.addAll(find(c, type));
        return out;
    }

    private static Button button(Node page, String text) {
        return find(page, Button.class).stream().filter(b -> text.equals(b.getText())).findFirst().orElseThrow();
    }

    @Test
    void pendingPageExplainsAndOnlyOffersLogOut() {
        AtomicInteger logOuts = new AtomicInteger();
        Parent page = StaffGate.pendingPage(logOuts::incrementAndGet);
        assertTrue(hasLabel(page, StaffGate.PENDING_MESSAGE));
        assertEquals(1, find(page, Button.class).size());
        button(page, "Log out").fire();
        assertEquals(1, logOuts.get());
    }

    @Test
    void newPasswordPageChecksThenSavesAndContinues() {
        List<String> saved = new ArrayList<>();
        ChangePasswordResult[] reply = {ChangePasswordResult.SAME_PASSWORD};
        AtomicInteger done = new AtomicInteger();
        Parent page = StaffGate.passwordResetPage((pw, onResult) -> { saved.add(pw); onResult.accept(reply[0]); },
                done::incrementAndGet, () -> fail("not logged out"));
        List<PasswordField> fields = find(page, PasswordField.class);
        Button save = button(page, "Save new password");
        assertTrue(save.isDisabled(), "nothing typed yet");

        fields.get(0).setText("Str0ng!Passw0rd");
        fields.get(1).setText("different");
        save.fire();
        assertTrue(hasLabel(page, "Passwords don't match."));
        assertTrue(saved.isEmpty());

        fields.get(1).setText("Str0ng!Passw0rd");
        save.fire();
        assertEquals(List.of("Str0ng!Passw0rd"), saved);
        assertTrue(hasLabel(page, ChangePasswordResult.SAME_PASSWORD.message()), "the service's message is shown");
        assertEquals(0, done.get());

        reply[0] = ChangePasswordResult.SUCCESS;
        save.fire();
        assertEquals(1, done.get());
    }

    @Test
    void newPasswordPageRejectsAWeakPasswordWithoutSaving() {
        List<String> saved = new ArrayList<>();
        Parent page = StaffGate.passwordResetPage((pw, onResult) -> saved.add(pw), () -> {}, () -> {});
        List<PasswordField> fields = find(page, PasswordField.class);
        fields.get(0).setText("short");
        fields.get(1).setText("short");
        button(page, "Save new password").fire();
        assertTrue(saved.isEmpty());
        assertTrue(hasLabel(page, ChangePasswordResult.WEAK_PASSWORD.message()));
    }
}
