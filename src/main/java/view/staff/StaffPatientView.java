package view.staff;

import controller.StaffController;
import controller.StaffForms;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import model.StaffAppointment;
import model.StaffPatient;
import model.StaffPrescription;
import model.StaffTest;
import service.StaffService;
import service.StaffService.PatientRecord;
import view.Navigator;
import view.ui.Dates;
import view.ui.UI;

import java.util.List;

/**
 * Patients tab: search the clinic's patients (only people with an appointment here), then a patient's
 * record at this clinic: appointments, tests (draft or released) and prescriptions.
 */
public class StaffPatientView {

    private final Navigator nav;
    private final StaffController staff;
    private final TextField search = UI.textField("");
    private final Label hint = UI.intro("");
    private final VBox results = new VBox(10);
    private int searchToken;

    public StaffPatientView(Navigator nav, StaffController staff) {
        this.nav = nav;
        this.staff = staff;
    }

    // ------------------------------------------------------------ search

    public Node searchPage() {
        if (staff.clinicId() == 0) return StaffUi.scroll(StaffUi.noClinic());
        UI.searchField(search);
        search.setPromptText("Name or email");
        search.setAccessibleText("Search patients by name or email");
        search.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(search, Priority.ALWAYS);
        Button go = UI.primaryButton("Search");
        go.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        go.setMaxWidth(110);
        go.setOnAction(e -> runSearch());
        search.setOnAction(e -> runSearch());
        HBox bar = new HBox(8, search, go);
        bar.setAlignment(Pos.CENTER_LEFT);
        hint.setText("Only patients with an appointment at this clinic are shown. Type at least "
                + StaffService.MIN_SEARCH + " letters.");
        VBox content = UI.pageTop(StaffUi.clinicSwitcher(staff), UI.pageHeading("Find a patient"), hint, bar, results);
        StaffUi.onClinicChange(content, () -> {
            results.getChildren().clear();
            if (search.getText().strip().length() >= StaffService.MIN_SEARCH) runSearch();
        });
        return StaffUi.scroll(content);
    }

    private void runSearch() {
        String q = search.getText();
        int token = ++searchToken;
        results.getChildren().setAll(StaffUi.loading());
        staff.<List<StaffPatient>>call((s, u, c) -> s.searchPatients(u, c, q), r -> {
            if (token != searchToken) return;
            if (!r.ok()) {
                results.getChildren().setAll(StaffUi.loadError(r.message(), null));
                return;
            }
            results.getChildren().clear();
            if (r.value().isEmpty()) {
                results.getChildren().add(UI.emptyState(UI.ICON_SEARCH, "No patients found",
                        "No one with an appointment at this clinic matches that name or email."));
                return;
            }
            for (StaffPatient p : r.value()) {
                String meta = p.lastAppointmentDate() == null ? null
                        : "Last appointment " + Dates.medium(p.lastAppointmentDate());
                results.getChildren().add(StaffUi.listCard(p.displayName(), meta, null,
                        () -> nav.goToPage(recordPage(p.userId()), "Patient record", false)));
            }
        });
    }

    // ------------------------------------------------------------ record

    /** The patient's record at the current clinic. Reloads itself after an edit made from it. */
    public Node recordPage(int patientId) {
        VBox content = UI.pageTop();
        loadRecord(content, patientId);
        return StaffUi.scroll(content);
    }

    private void loadRecord(VBox content, int patientId) {
        if (content.getChildren().isEmpty()) content.getChildren().setAll(StaffUi.loading());
        staff.<PatientRecord>call((s, u, c) -> s.patientRecord(u, c, patientId), r -> {
            if (!r.ok()) {
                content.getChildren().setAll(StaffUi.loadError(r.message(), () -> loadRecord(content, patientId)));
                return;
            }
            showRecord(content, r.value());
        });
    }

    private void showRecord(VBox content, PatientRecord rec) {
        StaffPatient p = rec.patient();
        int patientId = p.userId();
        Runnable reload = () -> loadRecord(content, patientId);

        VBox header = UI.card(10, UI.detailTitle(p.displayName()), UI.detail("ACCOUNT EMAIL", p.email()));
        if (p.lastAppointmentDate() != null) {
            header.getChildren().add(UI.detail("LAST APPOINTMENT HERE", Dates.medium(p.lastAppointmentDate())));
        }
        Button addTest = UI.secondaryButton("Add test result");
        addTest.setOnAction(e -> nav.goToPage(
                new TestEditorView(nav, staff, patientId, null, null, reload).page(), "Test result", false));
        Button prescribe = UI.secondaryButton("Prescribe");
        prescribe.setOnAction(e -> nav.goToPage(
                new PrescriptionEditorView(nav, staff, patientId, null, null, reload).page(), "Prescription", false));
        header.getChildren().add(StaffUi.pair(addTest, prescribe));

        VBox appts = StaffUi.section("Appointments here");
        if (rec.appointments().isEmpty()) appts.getChildren().add(UI.muted("No appointments at this clinic."));
        for (StaffAppointment a : rec.appointments()) {
            // Date as the title, time and service underneath: "date, time" in one title was cut in two
            // beside the pill at 420px.
            String time = Dates.timeRange(a.booking().startTime(), a.booking().endTime());
            String service = a.booking().serviceName();
            appts.getChildren().add(StaffUi.listCard(Dates.full(a.booking().date()),
                    service == null || service.isBlank() ? time : time + " · " + service,
                    StaffForms.appointmentPill(a.status()),
                    () -> nav.goToPage(new StaffAppointmentView(nav, staff, a.appId(), reload).page(),
                            "Appointment", false)));
        }

        VBox tests = StaffUi.section("Tests");
        if (rec.tests().isEmpty()) tests.getChildren().add(UI.muted("No tests recorded at this clinic."));
        for (StaffTest t : rec.tests()) {
            String meta = Dates.medium(t.testDate()) + " · " + t.results().size()
                    + (t.results().size() == 1 ? " result" : " results");
            tests.getChildren().add(StaffUi.listCard(t.testType(), meta, StaffForms.testPill(t.released()),
                    () -> nav.goToPage(new TestEditorView(nav, staff, patientId, null, t.testId(), reload).page(),
                            "Test result", false)));
        }

        VBox rx = StaffUi.section("Prescriptions");
        if (rec.prescriptions().isEmpty()) rx.getChildren().add(UI.muted("No prescriptions from this clinic."));
        for (StaffPrescription pr : rec.prescriptions()) {
            String meta = pr.dosage() + " · from " + Dates.medium(pr.startDate());
            rx.getChildren().add(StaffUi.listCard(pr.medicineName(), meta, StaffForms.prescriptionPill(pr.active()),
                    () -> nav.goToPage(new PrescriptionEditorView(nav, staff, patientId, pr.testId(), pr, reload).page(),
                            "Prescription", false)));
        }

        content.getChildren().setAll(header, appts, tests, rx);
    }
}
