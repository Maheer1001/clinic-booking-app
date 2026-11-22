package view.staff;

import controller.StaffController;
import controller.StaffForms;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import model.AppointmentStatus;
import model.BookingDetails;
import model.ClinicBookingDetails;
import model.StaffAppointment;
import service.StaffService;
import view.Navigator;
import view.ui.Dates;
import view.ui.UI;

import java.time.LocalDate;

/**
 * One appointment for staff: the booking and the personal details given with it (this page only,
 * never a list), attendance, staff-only notes, and shortcuts to add a test or prescribe for the patient.
 */
public class StaffAppointmentView {

    private final Navigator nav;
    private final StaffController staff;
    private final int appId;
    private final Runnable onChanged;
    private final VBox content = UI.pageTop();
    private Label attendanceMsg;

    /** @param onChanged run after attendance or notes change (e.g. to refresh the day list) */
    public StaffAppointmentView(Navigator nav, StaffController staff, int appId, Runnable onChanged) {
        this.nav = nav;
        this.staff = staff;
        this.appId = appId;
        this.onChanged = onChanged == null ? () -> {} : onChanged;
    }

    public Node page() {
        load();
        return StaffUi.scroll(content);
    }

    private void load() {
        content.getChildren().setAll(StaffUi.loading());
        staff.<StaffAppointment>call((s, u, c) -> s.appointment(u, c, appId), r -> {
            if (!r.ok()) {
                content.getChildren().setAll(StaffUi.loadError(r.message(), this::load));
                return;
            }
            show(r.value());
        });
    }

    private void show(StaffAppointment a) {
        content.getChildren().setAll(summaryCard(a), detailsCard(a.booking()), attendanceCard(a), notesCard(a),
                actionsCard(a));
    }

    private Node summaryCard(StaffAppointment a) {
        ClinicBookingDetails b = a.booking();
        Label name = UI.detailTitle(a.patientName());
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);
        HBox top = new HBox(10, name, StaffUi.pill(StaffForms.appointmentPill(a.status())));
        top.setAlignment(Pos.CENTER_LEFT);
        VBox card = UI.card(12, top,
                UI.detail("SERVICE", b.serviceName()),
                UI.detail("WHEN", Dates.dateTime(b.date(), b.startTime(), b.endTime())));
        if (a.attendedAt() != null && a.status() == AppointmentStatus.COMPLETED) {
            card.getChildren().add(UI.muted("Marked as attended."));
        }
        return card;
    }

    /** Personal details from the booking: detail page only. */
    private Node detailsCard(ClinicBookingDetails b) {
        BookingDetails d = b.details();
        VBox card = StaffUi.section("Booking details");
        if (d == null) {
            card.getChildren().add(UI.muted("No personal details were given with this booking."));
        } else {
            card.getChildren().addAll(
                    UI.detail("NAME", d.fullName()),
                    UI.detail("DATE OF BIRTH", d.dob() == null ? null : Dates.medium(d.dob())),
                    UI.detail("PHONE", d.phone()),
                    UI.detail("CONTACT EMAIL", d.email()),
                    UI.detail("GENDER", d.gender()),
                    UI.detail("SEXUALITY", d.sexuality()),
                    UI.detail("ACCESSIBILITY NEEDS", d.accessibilityNeeds()));
        }
        card.getChildren().add(UI.detail("PATIENT'S NOTES", b.notes()));
        return card;
    }

    private Node attendanceCard(StaffAppointment a) {
        AppointmentStatus status = a.status();
        LocalDate date = a.booking().date();
        Label msg = StaffUi.message();
        VBox card = StaffUi.section("Attendance");
        if (status == AppointmentStatus.CANCELLED) {
            card.getChildren().add(UI.muted("This appointment was cancelled, so attendance can't be recorded."));
            return card;
        }
        if (!StaffForms.canRecordOutcome(status, date, LocalDate.now())) {
            card.getChildren().add(UI.muted("You can record attendance from the day of the appointment."));
            return card;
        }
        Button attended = UI.primaryButton("Mark attended");
        Button noShow = UI.secondaryButton("No-show");
        attended.setDisable(status == AppointmentStatus.COMPLETED);
        noShow.setDisable(status == AppointmentStatus.NO_SHOW);
        attended.setOnAction(e -> outcome(msg, (s, u, c) -> s.markCompleted(u, c, appId)));
        noShow.setOnAction(e -> outcome(msg, (s, u, c) -> s.markNoShow(u, c, appId)));
        card.getChildren().add(StaffUi.pair(attended, noShow));
        if (StaffForms.canClearOutcome(status)) {
            Button clear = UI.secondaryButton("Clear attendance");
            clear.setMaxWidth(Double.MAX_VALUE);
            clear.setOnAction(e -> outcome(msg, (s, u, c) -> s.clearOutcome(u, c, appId)));
            card.getChildren().add(clear);
        }
        card.getChildren().add(msg);
        attendanceMsg = msg;
        return card;
    }

    private void outcome(Label msg, StaffController.Call<StaffAppointment> op) {
        staff.call(op, r -> {
            if (!r.ok()) {
                StaffUi.showError(msg, r.message());
                return;
            }
            onChanged.run();
            show(r.value());
            // The rebuilt card has a fresh message line: say what happened there.
            if (attendanceMsg != null) StaffUi.showOk(attendanceMsg, StaffForms.savedText(r.message()));
        });
    }

    private Node notesCard(StaffAppointment a) {
        TextArea notes = new TextArea(a.staffNotes() == null ? "" : a.staffNotes());
        notes.setWrapText(true);
        notes.setPrefRowCount(4);
        notes.setPromptText("Only staff at this clinic can see these notes.");
        notes.setAccessibleText("Staff notes");
        Label count = UI.muted("");
        Runnable updateCount = () -> count.setText(notes.getLength() + " / " + StaffService.MAX_NOTES);
        notes.textProperty().addListener((o, was, now) -> updateCount.run());
        updateCount.run();
        Label msg = StaffUi.message();
        Button save = UI.primaryButton("Save notes");
        save.setMaxWidth(Double.MAX_VALUE);
        save.setOnAction(e -> {
            String text = notes.getText();
            save.setDisable(true);
            staff.<StaffAppointment>call((s, u, c) -> s.saveStaffNotes(u, c, appId, text), r -> {
                save.setDisable(false);
                if (r.ok()) StaffUi.showOk(msg, UI.SAVED);
                else StaffUi.showError(msg, r.message());
            });
        });
        notes.textProperty().addListener((o, was, now) -> msg.setText(""));
        return StaffUi.section("Staff notes", UI.muted("Never shown to the patient."), notes, count, save, msg);
    }

    private Node actionsCard(StaffAppointment a) {
        int patientId = a.patientUserId();
        Button test = UI.secondaryButton("Add test result");
        test.setOnAction(e -> {
            TestEditorView editor = new TestEditorView(nav, staff, patientId, appId, null, null);
            nav.goToPage(editor.page(), "Test result", false);
        });
        Button prescribe = UI.secondaryButton("Prescribe");
        prescribe.setOnAction(e -> {
            PrescriptionEditorView editor = new PrescriptionEditorView(nav, staff, patientId, null, null, null);
            nav.goToPage(editor.page(), "Prescription", false);
        });
        Button record = UI.secondaryButton("Open patient record");
        record.setMaxWidth(Double.MAX_VALUE);
        record.setOnAction(e -> {
            StaffPatientView view = new StaffPatientView(nav, staff);
            nav.goToPage(view.recordPage(patientId), "Patient record", false);
        });
        return StaffUi.section("For this patient", StaffUi.pair(test, prescribe), record);
    }
}
