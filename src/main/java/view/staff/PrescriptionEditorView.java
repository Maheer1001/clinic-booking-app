package view.staff;

import controller.StaffController;
import controller.StaffForms;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import model.Medicine;
import model.PrescriptionDraft;
import model.StaffPrescription;
import view.Navigator;
import view.ui.Dates;
import view.ui.UI;

import java.time.LocalDate;
import java.util.List;

/**
 * Prescribe from the clinic's medicine list (dose, doses per day, days between doses, start/end and
 * instructions), or view an existing prescription and stop it early.
 */
public class PrescriptionEditorView {

    private final Navigator nav;
    private final StaffController staff;
    private final int patientId;
    private final Integer testId;
    private StaffPrescription existing;
    private final Runnable onSaved;
    private final VBox content = UI.pageTop();

    /**
     * @param testId   the test this treats (optional)
     * @param existing a prescription to show (with Stop early), or null to prescribe
     */
    public PrescriptionEditorView(Navigator nav, StaffController staff, int patientId, Integer testId,
                                  StaffPrescription existing, Runnable onSaved) {
        this.nav = nav;
        this.staff = staff;
        this.patientId = patientId;
        this.testId = testId;
        this.existing = existing;
        this.onSaved = onSaved == null ? () -> {} : onSaved;
    }

    public Node page() {
        if (existing != null) showExisting(null);
        else loadForm();
        return StaffUi.scroll(content);
    }

    // ------------------------------------------------------------ new prescription

    private void loadForm() {
        content.getChildren().setAll(StaffUi.loading());
        staff.<List<Medicine>>call((s, u, c) -> s.medicines(u, c), r -> {
            if (!r.ok()) {
                content.getChildren().setAll(StaffUi.loadError(r.message(), this::loadForm));
                return;
            }
            showForm(r.value());
        });
    }

    private void showForm(List<Medicine> medicines) {
        ComboBox<Medicine> medicine = new ComboBox<>();
        medicine.getItems().setAll(medicines);
        medicine.setCellFactory(l -> medicineCell());
        medicine.setButtonCell(medicineCell());
        medicine.setPromptText("Choose a medicine");
        medicine.setAccessibleText("Medicine");
        medicine.setMaxWidth(Double.MAX_VALUE);

        TextField dose = UI.textField("");
        dose.setPromptText("e.g. 100mg");
        dose.setAccessibleText("Dose");
        Spinner<Integer> perDay = new Spinner<>(1, 24, 1);
        Spinner<Integer> between = new Spinner<>(0, 365, 1);
        for (Spinner<Integer> sp : List.of(perDay, between)) {
            sp.setEditable(true);
            UI.tuneIntSpinner(sp);
            sp.setMaxWidth(Double.MAX_VALUE);
        }
        perDay.setAccessibleText("Doses per day");
        between.setAccessibleText("Days between doses");
        DatePicker start = new DatePicker(LocalDate.now());
        DatePicker end = new DatePicker();
        for (DatePicker dp : List.of(start, end)) {
            UI.datePicker(dp);
            dp.setMaxWidth(Double.MAX_VALUE);
        }
        start.setAccessibleText("Start date");
        end.setAccessibleText("End date");
        end.setPromptText("Optional");
        TextArea instructions = new TextArea();
        instructions.setWrapText(true);
        instructions.setPrefRowCount(3);
        instructions.setPromptText("e.g. Take with food. Avoid alcohol.");
        instructions.setAccessibleText("Instructions");

        Label msg = StaffUi.message();
        Button prescribe = UI.primaryButton("Prescribe");
        prescribe.setMaxWidth(Double.MAX_VALUE);
        prescribe.setOnAction(e -> {
            if (medicine.getValue() == null) {
                StaffUi.showError(msg, "Choose a medicine from the list.");
                return;
            }
            PrescriptionDraft d = new PrescriptionDraft(medicine.getValue().medicineId(), testId, dose.getText(),
                    perDay.getValue(), between.getValue(), start.getValue(), end.getValue(), instructions.getText());
            prescribe.setDisable(true);
            staff.<StaffPrescription>call((s, u, c) -> s.prescribe(u, c, patientId, d), r -> {
                prescribe.setDisable(false);
                if (!r.ok()) {
                    StaffUi.showError(msg, r.message());
                    return;
                }
                existing = r.value();
                onSaved.run();
                // Show what was saved (with Stop early) so it can't be prescribed twice by a double click.
                showExisting(StaffForms.savedText(r.message()));
            });
        });

        VBox form = StaffUi.section("New prescription",
                label("MEDICINE"), medicine,
                label("DOSE"), dose,
                twoUp(labelled("DOSES PER DAY", perDay), labelled("DAYS BETWEEN DOSES", between)),
                UI.muted("0 or 1 day between doses means every day."),
                twoUp(labelled("START", start), labelled("END", end)),
                label("INSTRUCTIONS"), instructions);
        content.getChildren().setAll(form, prescribe, msg);
    }

    private static ListCell<Medicine> medicineCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Medicine m, boolean empty) {
                super.updateItem(m, empty);
                setText(empty || m == null ? null : m.name());
            }
        };
    }

    // ------------------------------------------------------------ existing prescription

    private void showExisting(String savedMessage) {
        StaffPrescription p = existing;
        Label name = UI.detailTitle(p.medicineName());
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);
        HBox top = new HBox(10, name, StaffUi.pill(StaffForms.prescriptionPill(p.active())));
        top.setAlignment(Pos.CENTER_LEFT);
        VBox card = UI.card(12, top,
                twoUp(UI.detail("DOSE", p.dosage()), UI.detail("HOW OFTEN", howOften(p))),
                twoUp(UI.detail("START", Dates.medium(p.startDate())),
                        UI.detail(p.active() ? "ENDS" : "ENDED", p.endDate() == null ? "Ongoing" : Dates.medium(p.endDate()))),
                UI.detail("INSTRUCTIONS", p.instructions()));
        Label msg = StaffUi.message();
        if (savedMessage != null) StaffUi.showOk(msg, savedMessage);
        content.getChildren().setAll(card);
        if (p.active()) {
            Button stop = UI.destructiveButton("Stop early");
            stop.setMaxWidth(Double.MAX_VALUE);
            stop.setOnAction(e -> {
                if (!StaffUi.confirm(content, "Stop this prescription?",
                        "It ends today and the patient's dose reminders stop the next time they open the app.",
                        "Stop early")) {
                    return;
                }
                int id = p.prescriptionId();
                stop.setDisable(true);
                staff.<StaffPrescription>call((s, u, c) -> s.stopPrescription(u, c, id), r -> {
                    stop.setDisable(false);
                    if (!r.ok()) {
                        StaffUi.showError(msg, r.message());
                        return;
                    }
                    existing = r.value();
                    onSaved.run();
                    showExisting(StaffForms.savedText(r.message()));
                });
            });
            content.getChildren().add(stop);
        }
        content.getChildren().add(msg);
    }

    static String howOften(StaffPrescription p) {
        Integer perDay = p.dosesPerDay();
        Integer gap = p.daysBetweenDoses();
        int n = perDay == null ? 1 : perDay;
        if (gap != null && gap > 1) return (n == 1 ? "1 dose" : n + " doses") + " every " + gap + " days";
        if (perDay == null) return "As directed";
        return n == 1 ? "Once a day" : n + " times a day";
    }

    private static Label label(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("detail-label");
        return l;
    }

    private static VBox labelled(String text, Node field) {
        return new VBox(4, label(text), field);
    }

    private static HBox twoUp(Node a, Node b) {
        HBox row = new HBox(12, a, b);
        for (Node n : row.getChildren()) {
            HBox.setHgrow(n, Priority.ALWAYS);
            if (n instanceof VBox v) {
                v.setMaxWidth(Double.MAX_VALUE);
                v.setPrefWidth(1);
            }
        }
        return row;
    }
}
