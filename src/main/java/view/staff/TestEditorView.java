package view.staff;

import controller.StaffController;
import controller.StaffForms;
import controller.StaffForms.ResultRow;
import controller.StaffTestEditorController;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import model.StaffTest;
import model.TestDraft;
import model.TestResultStatus;
import view.Navigator;
import view.ui.Dates;
import view.ui.UI;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Create or edit a test: type, date, result rows, clinician notes and treatment instructions. New
 * tests are saved as drafts; "Release to patient" saves and releases (with a confirm step for a
 * positive result). The patient then gets a discreet email and an in-app alert.
 */
public class TestEditorView {

    private final Navigator nav;
    private final StaffController staff;
    private final int patientId;
    private final Integer appId;
    private final Integer testId;
    private final Runnable onSaved;
    private final VBox content = UI.pageTop();

    private StaffTestEditorController editor;
    private final TextField type = UI.textField("");
    private final DatePicker date = new DatePicker(LocalDate.now());
    private final VBox rows = new VBox(8);
    private final List<RowFields> rowFields = new ArrayList<>();
    private final TextArea notes = new TextArea();
    private final TextArea treatment = new TextArea();
    private final Label statusLine = new Label();
    private final Label msg = StaffUi.message();
    private final Button save = UI.secondaryButton("Save draft");
    private final Button release = UI.primaryButton("Release to patient");
    private final HBox header = new HBox(10);

    private record RowFields(VBox row, TextField condition, ComboBox<TestResultStatus> result) {}

    /**
     * @param appId  the appointment it was taken at (new tests only; may be null)
     * @param testId the test to edit, or null for a new one
     * @param onSaved run after a save or release (e.g. to refresh a patient record)
     */
    public TestEditorView(Navigator nav, StaffController staff, int patientId, Integer appId, Integer testId,
                          Runnable onSaved) {
        this.nav = nav;
        this.staff = staff;
        this.patientId = patientId;
        this.appId = appId;
        this.testId = testId;
        this.onSaved = onSaved == null ? () -> {} : onSaved;
    }

    public Node page() {
        if (testId == null) {
            editor = new StaffTestEditorController(staff, patientId, null);
            build(null);
        } else {
            content.getChildren().setAll(StaffUi.loading());
            staff.<StaffTest>call((s, u, c) -> s.test(u, c, testId), r -> {
                if (!r.ok()) {
                    content.getChildren().setAll(StaffUi.loadError(r.message(), null));
                    return;
                }
                editor = new StaffTestEditorController(staff, patientId, r.value());
                build(r.value());
            });
        }
        return StaffUi.scroll(content);
    }

    private void build(StaffTest t) {
        type.setPromptText("e.g. STI screen");
        type.setAccessibleText("Test type");
        StaffUi.datePicker(date);
        date.setAccessibleText("Test date");
        date.setMaxWidth(Double.MAX_VALUE);
        for (TextArea area : List.of(notes, treatment)) {
            area.setWrapText(true);
            area.setPrefRowCount(3);
        }
        notes.setPromptText("Only shown to the patient once released.");
        notes.setAccessibleText("Clinician notes");
        treatment.setPromptText("What the patient should do next, if anything.");
        treatment.setAccessibleText("Treatment instructions");

        if (t != null) {
            type.setText(t.testType());
            date.setValue(t.testDate());
            notes.setText(t.clinicianNotes() == null ? "" : t.clinicianNotes());
            treatment.setText(t.treatmentInstructions() == null ? "" : t.treatmentInstructions());
            for (StaffTest.Result r : t.results()) addRow(r.condition(), r.result());
        }
        if (rowFields.isEmpty()) addRow("", TestResultStatus.PENDING);

        Button add = UI.positiveButton("+ Add result");
        add.setOnAction(e -> addRow("", TestResultStatus.PENDING));

        statusLine.setWrapText(true);
        statusLine.setMaxWidth(Double.MAX_VALUE);
        statusLine.getStyleClass().add("page-intro");
        header.setAlignment(Pos.CENTER_LEFT);

        save.setMaxWidth(Double.MAX_VALUE);
        release.setMaxWidth(Double.MAX_VALUE);
        save.setOnAction(e -> doSave(false));
        release.setOnAction(e -> doSave(true));
        Label releaseNote = UI.muted("When you release it, the patient gets a discreet email (it never says the "
                + "result) and an in-app alert that a new result is ready.");
        releaseNote.setWrapText(true);

        VBox details = StaffUi.section("Test", label("TEST TYPE"), type, label("TEST DATE"), date);
        VBox results = StaffUi.section("Results", rows, add);
        VBox text = StaffUi.section("For the patient", label("CLINICIAN NOTES"), notes,
                label("TREATMENT INSTRUCTIONS"), treatment);
        VBox actions = new VBox(10, release, save, msg, releaseNote);
        content.getChildren().setAll(header, statusLine, details, results, text, actions);
        refreshHeader();
        // Any edit after a save means the message no longer describes what is on screen.
        type.textProperty().addListener((o, a, b) -> msg.setText(""));
    }

    private static Label label(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("detail-label");
        return l;
    }

    private void addRow(String condition, TestResultStatus result) {
        TextField c = UI.textField(condition == null ? "" : condition);
        c.setPromptText("Condition, e.g. Chlamydia");
        c.setAccessibleText("Condition");
        c.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(c, Priority.ALWAYS);
        ComboBox<TestResultStatus> box = new ComboBox<>(FXCollections.observableArrayList(
                TestResultStatus.PENDING, TestResultStatus.NEGATIVE, TestResultStatus.POSITIVE));
        box.setValue(result == null ? TestResultStatus.PENDING : result);
        box.setCellFactory(l -> statusCell());
        box.setButtonCell(statusCell());
        box.setAccessibleText("Result");
        box.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(box, Priority.ALWAYS);
        Button remove = UI.dangerButton("Remove");
        remove.setAccessibleText("Remove this result");
        remove.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        // Condition on its own line (full width), then the result and Remove: fits 420px at large text.
        HBox controls = new HBox(8, box, remove);
        controls.setAlignment(Pos.CENTER_LEFT);
        VBox row = UI.innerCard(8, c, controls);
        row.getStyleClass().add("staff-result-row");
        RowFields f = new RowFields(row, c, box);
        remove.setOnAction(e -> {
            rowFields.remove(f);
            rows.getChildren().remove(row);
            if (rowFields.isEmpty()) addRow("", TestResultStatus.PENDING);
        });
        rowFields.add(f);
        rows.getChildren().add(row);
    }

    private static ListCell<TestResultStatus> statusCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(TestResultStatus s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : s.label());
            }
        };
    }

    private TestDraft draft() {
        List<ResultRow> lines = rowFields.stream()
                .map(f -> new ResultRow(f.condition().getText(), f.result().getValue())).toList();
        Integer app = editor.isNew() ? appId : editor.test().appId();
        return StaffForms.testDraft(app, type.getText(), date.getValue(), lines, notes.getText(), treatment.getText());
    }

    private void doSave(boolean andRelease) {
        TestDraft d = draft();
        save.setDisable(true);
        release.setDisable(true);
        StaffTestEditorController.Confirmer confirmer = message -> StaffUi.confirm(content,
                andRelease ? "Release a positive result?" : "Save a positive result?",
                message + "\n\nThe patient gets a discreet email and an in-app alert. The email never says the result.",
                andRelease ? "Release to patient" : "Save and tell patient");
        java.util.function.Consumer<StaffTestEditorController.Outcome> done = o -> {
            save.setDisable(false);
            release.setDisable(false);
            refreshHeader();
            if (o.ok()) {
                onSaved.run();
                StaffUi.showOk(msg, andRelease ? o.message() : StaffForms.savedText(o.message()));
            } else {
                StaffUi.showError(msg, o.message());
            }
        };
        if (andRelease) editor.saveAndRelease(d, confirmer, done);
        else editor.save(d, confirmer, done);
    }

    private void refreshHeader() {
        StaffTest t = editor.test();
        Label title = UI.detailTitle(t == null ? "New test result" : t.testType());
        title.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(title, Priority.ALWAYS);
        header.getChildren().setAll(title, StaffUi.pill(StaffForms.testPill(editor.isReleased())));
        if (editor.isReleased()) {
            statusLine.setText("Released to the patient" + (t.releasedAt() == null ? "."
                    : " on " + Dates.medium(t.releasedAt().toLocalDate()) + ".")
                    + " A result changed to positive or negative alerts them again.");
            save.setText("Save changes");
            release.setVisible(false);
            release.setManaged(false);
        } else {
            statusLine.setText(t == null
                    ? "Saved as a draft first: the patient sees nothing until you release it."
                    : "Draft: the patient can't see this yet.");
        }
    }
}
