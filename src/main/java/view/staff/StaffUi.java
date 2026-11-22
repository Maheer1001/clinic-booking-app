package view.staff;

import controller.StaffController;
import controller.StaffForms;
import controller.StaffForms.Pill;
import dao.ClinicDAO;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import model.Clinic;
import service.StaffResult;
import view.ui.UI;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Small building blocks shared by the staff screens, on top of {@link UI}. */
final class StaffUi {

    private static final URL STAFF_CSS = StaffUi.class.getResource("/css/staff.css");
    /** Clinic names never change while the app runs. */
    private static final Map<Integer, String> CLINIC_NAMES = new ConcurrentHashMap<>();

    private StaffUi() {}

    /** A scrolling page with the staff stylesheet. */
    static ScrollPane scroll(Node content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        withStyles(sp);
        return sp;
    }

    static <T extends Parent> T withStyles(T root) {
        if (STAFF_CSS != null && !root.getStylesheets().contains(STAFF_CSS.toExternalForm())) {
            root.getStylesheets().add(STAFF_CSS.toExternalForm());
        }
        return root;
    }

    static Label pill(Pill p) {
        Label l = new Label(p.text());
        l.getStyleClass().addAll("staff-pill", "staff-pill-" + p.kind());
        l.setMinWidth(Region.USE_PREF_SIZE);
        return l;
    }

    /** A message line for a section: green "Saved." style on success, red on failure, hidden when empty. */
    static Label message() {
        Label l = UI.inlineSaveConfirmation();
        l.managedProperty().bind(l.textProperty().isNotEmpty());
        l.visibleProperty().bind(l.textProperty().isNotEmpty());
        return l;
    }

    static void showOk(Label label, String text) {
        label.getStyleClass().remove(UI.ERROR);
        if (!label.getStyleClass().contains(UI.INLINE_SAVE_CONFIRM)) label.getStyleClass().add(UI.INLINE_SAVE_CONFIRM);
        label.setText(text);
    }

    static void showError(Label label, String text) {
        label.getStyleClass().remove(UI.INLINE_SAVE_CONFIRM);
        if (!label.getStyleClass().contains(UI.ERROR)) label.getStyleClass().add(UI.ERROR);
        label.setText(text == null ? StaffResult.Status.DB_ERROR.defaultMessage() : text);
    }

    /** Shows a write's result: "Saved. ..." when it worked, otherwise the service's message. */
    static void showSaved(Label label, StaffResult<?> r) {
        if (r.ok()) showOk(label, StaffForms.savedText(r.message()));
        else showError(label, r.message());
    }

    static Node loading() {
        ProgressIndicator p = new ProgressIndicator();
        p.setMaxSize(36, 36);
        VBox box = new VBox(p);
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    /** A red line for a failed load, with a Try again button. */
    static Node loadError(String message, Runnable retry) {
        Label l = UI.error(message == null ? StaffResult.Status.DB_ERROR.defaultMessage() : message);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        if (retry == null) return l;
        Button again = UI.secondaryButton("Try again");
        again.setOnAction(e -> retry.run());
        return new VBox(10, l, again);
    }

    /** A list card: title and meta on the left, a pill on the right. */
    static Button listCard(String title, String meta, Pill pill, Runnable open) {
        Label t = UI.wrappingLabel(title);
        t.getStyleClass().add("entry-title");
        VBox text = new VBox(2, t);
        if (meta != null && !meta.isBlank()) {
            Label m = UI.wrappingLabel(meta);
            m.getStyleClass().add("entry-meta");
            text.getChildren().add(m);
        }
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(10, text);
        if (pill != null) row.getChildren().add(pill(pill));
        row.setAlignment(Pos.CENTER_LEFT);
        Button b = UI.listButton();
        b.setGraphic(row);
        b.setMaxWidth(Double.MAX_VALUE);
        row.prefWidthProperty().bind(b.widthProperty().subtract(34));
        b.setAccessibleText(title + (meta == null ? "" : ", " + meta) + (pill == null ? "" : ", " + pill.text()));
        if (open != null) b.setOnAction(e -> open.run());
        return b;
    }

    /** A titled section card. */
    static VBox section(String heading, Node... children) {
        VBox card = UI.card(10);
        card.getChildren().add(UI.titleSmWrapping(heading));
        card.getChildren().addAll(children);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    /** Two equal-width buttons side by side. */
    static HBox pair(Button a, Button b) {
        for (Button x : List.of(a, b)) {
            x.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(x, Priority.ALWAYS);
            x.setPrefWidth(1);
        }
        return new HBox(10, a, b);
    }

    /** Confirmation dialog in the app's style. True on the confirm button. */
    static boolean confirm(Node owner, String title, String message, String confirmText) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        ButtonType yes = new ButtonType(confirmText, ButtonBar.ButtonData.OK_DONE);
        ButtonType no = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(no, yes);
        UI.styleDialog(alert, owner == null ? null : owner.getScene());
        if (alert.getDialogPane().lookupButton(yes) instanceof Button b) b.getStyleClass().add(UI.ALERT_AGREE);
        if (alert.getDialogPane().lookupButton(no) instanceof Button b) b.getStyleClass().add(UI.ALERT_IGNORE);
        return alert.showAndWait().filter(t -> t == yes).isPresent();
    }

    // ------------------------------------------------------------ clinic switcher

    static String clinicName(int clinicId) {
        return CLINIC_NAMES.getOrDefault(clinicId, "Clinic " + clinicId);
    }

    /**
     * The clinic heading: just the name for one clinic, a drop-down to switch for several. Names are
     * read off the JavaFX thread; until they arrive the ids are shown.
     */
    static Node clinicSwitcher(StaffController staff) {
        List<Integer> ids = staff.clinicIds();
        Label single = new Label(clinicName(staff.clinicId()));
        single.getStyleClass().add("page-intro");
        single.setWrapText(true);
        ComboBox<Integer> box = new ComboBox<>();
        Runnable refresh = () -> {
            single.setText(clinicName(staff.clinicId()));
            box.setButtonCell(clinicCell());
        };
        if (ids.size() > 1) {
            box.getItems().setAll(ids);
            box.setValue(staff.clinicId());
            box.setCellFactory(l -> clinicCell());
            box.setButtonCell(clinicCell());
            box.setMaxWidth(Double.MAX_VALUE);
            box.setAccessibleText("Clinic");
            box.valueProperty().addListener((o, was, now) -> {
                if (now != null) staff.switchClinic(now);
            });
            onClinicChange(box, () -> {
                int now = staff.clinicId();
                if (now != 0 && !Integer.valueOf(now).equals(box.getValue())) box.setValue(now);
            });
        }
        if (ids.stream().anyMatch(id -> !CLINIC_NAMES.containsKey(id))) {
            staff.load(() -> {
                Map<Integer, String> names = new HashMap<>();
                ClinicDAO dao = new ClinicDAO();
                for (int id : ids) {
                    try {
                        Clinic c = dao.findById(id);
                        if (c != null && c.getName() != null) names.put(id, c.getName());
                    } catch (RuntimeException ignored) {
                        // keep "Clinic <id>"
                    }
                }
                return names;
            }, r -> {
                if (r.ok()) CLINIC_NAMES.putAll(r.value());
                refresh.run();
            });
        }
        if (ids.size() <= 1) return single;
        Label label = new Label("CLINIC");
        label.getStyleClass().add("detail-label");
        return new VBox(4, label, box);
    }

    /**
     * Runs {@code action} when the clinic switcher changes clinic. The listener is weak and kept alive
     * by {@code owner}, so pages from a logged-out session are not held on to.
     */
    static void onClinicChange(Node owner, Runnable action) {
        javafx.beans.value.ChangeListener<Number> l = (o, was, now) -> {
            if (now.intValue() != 0) action.run();
        };
        owner.getProperties().put("staff.clinicListener." + System.identityHashCode(action), l);
        StaffController.clinicChanges().addListener(new javafx.beans.value.WeakChangeListener<>(l));
    }

    private static ListCell<Integer> clinicCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Integer id, boolean empty) {
                super.updateItem(id, empty);
                setText(empty || id == null ? null : clinicName(id));
            }
        };
    }

    /** The page shown when the account has no clinic (yet). */
    static Node noClinic() {
        return UI.page(UI.emptyState(UI.ICON_BELL, "Waiting for approval",
                "Your clinic admin needs to approve your account before you can see the clinic."));
    }
}
