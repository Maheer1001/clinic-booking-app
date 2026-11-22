package view.ui;

import javafx.beans.binding.DoubleBinding;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import javafx.util.StringConverter;
import javafx.util.converter.IntegerStringConverter;

/**
 * Central place for in-app UI styling (Booking, Tracking, Reminders).
 * Uses CSS classes from app.css – change the .app-* rules there to update the whole app.
 * Login and signup screens are unchanged (their own FXML/CSS).
 */
public final class UI {

    private UI() {}

    //stylesheet names, can be used to apply stylesheets in accessibility settings
    public static final String STYLE = UI.class.getResource("/css/app.css").toExternalForm();
    public static final String BOLD_STYLE = UI.class.getResource("/css/bold-text.css").toExternalForm();
    public static final String TEXT_SIZE_UP_STYLE = UI.class.getResource("/css/text-size-up.css").toExternalForm();
    public static final String TEXT_SIZE_DOWN_STYLE = UI.class.getResource("/css/text-size-down.css").toExternalForm();
    public static final String CONTRAST_STYLE = UI.class.getResource("/css/contrast.css").toExternalForm();

    // Style class names – must match app.css
    public static final String PAGE = "app-page";
    public static final String PAGE_TOP = "app-page-top";
    public static final String PAGE_WIDE = "app-page-wide";
    public static final String PAGE_LEFT = "app-page-left";
    public static final String CENTER = "app-center";
    public static final String TITLE = "app-title";
    public static final String TITLE_SM = "app-title-sm";
    public static final String HEADING = "app-heading";
    public static final String CARD = "app-card";
    public static final String CARD_INNER = "app-card-inner";
    public static final String BTN = "app-btn";
    public static final String BTN_BACK = "app-btn-back";
    /** Destructive action (remove / delete). */
    public static final String BTN_DANGER = "app-btn-danger";
    /** Add / positive outlined action (same shape as danger, green). */
    public static final String BTN_POSITIVE = "app-btn-positive";
    public static final String LIST_BTN = "app-list-btn";
    public static final String MENU_BTN = "app-menu-btn";
    public static final String NAV_BTN = "app-nav-btn";
    public static final String SHORTCUT_BTN = "app-shortcut-btn";
    public static final String SPINNER = "app-spinner";
    public static final String MUTED = "app-muted";
    public static final String BODY = "app-body";
    public static final String NOTIFICATION = "app-notification";
    public static final String TRK_SEARCH = "tracking-search";
    public static final String TRK_SORT = "tracking-sort";
    public static final String TRK_DATE = "tracking-date";
    public static final String TRK_CLEAR = "tracking-clear-btn";
    public static final String ERROR = "error";
    /** Small centred red text after an in-place save (no navigation). */
    public static final String INLINE_SAVE_CONFIRM = "app-inline-save-confirm";
    /** Toggle-style tab on the Reminders hub. */
    public static final String REMINDER_TAB = "app-reminder-tab";
    /** Persistent bottom navigation (Booking / Tracking / Reminders). */
    public static final String MAIN_NAV = "app-main-nav";
    /** Same left/right layout as Tracking list rows (non-interactive header row). */
    public static final String TRACKING_LIST_ROW = "app-tracking-list-row";
    /** Reminder card header: full-width service/date row (no fixed 300px like tracking rows). */
    public static final String REMINDER_CARD_HEADER = "app-reminder-card-header";
    /** Compact {@link javafx.scene.control.TitledPane}s in {@link view.reminders.ReminderTemplateForm}. */
    public static final String REMINDER_TITLED_PANE = "app-reminder-titled-pane";
    public static final String LIST_VIEW_HOVER = "list-view-with-hover";
    public static final String ALERT_AGREE  = "alert-agree";
    public static final String ALERT_IGNORE  = "alert-ignore";
    public static final String TEXT_FIELD = "app-text-field";
    /** Full-width, primary-height sizing shared by every in-page action button. */
    public static final String BTN_BLOCK = "app-btn-block";

    /** Page container with standard padding/spacing. */
    public static VBox page(Node... children) {
        VBox v = new VBox(15, children);
        v.getStyleClass().add(PAGE);
        return v;
    }

    public static VBox pageTop(Node... children) {
        VBox v = new VBox(15, children);
        v.getStyleClass().add(PAGE_TOP);
        return v;
    }

    /** Page container with wider padding (e.g. home menu). */
    public static VBox pageWide(Node... children) {
        VBox v = new VBox(20, children);
        v.getStyleClass().add(PAGE_WIDE);
        return v;
    }

    /** Page container left-aligned (e.g. forms). */
    public static VBox pageLeft(Node... children) {
        VBox v = new VBox(15, children);
        v.getStyleClass().add(PAGE_LEFT);
        return v;
    }

    /** Main page title (e.g. "Reminders", "View Bookings"). */
    public static Label title(String text) {
        Label l = new Label(text);
        l.getStyleClass().add(TITLE);
        return l;
    }

    /** Slightly smaller title (e.g. section headers). */
    public static Label titleSm(String text) {
        Label l = new Label(text);
        l.getStyleClass().add(TITLE_SM);
        return l;
    }

    /** Like {@link #titleSm} but wraps within the parent width (card section headers). */
    public static Label titleSmWrapping(String text) {
        Label l = new Label(text);
        l.getStyleClass().add(TITLE_SM);
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    /** Bold heading for cards/lists. */
    public static Label heading(String text) {
        Label l = new Label(text);
        l.getStyleClass().add(HEADING);
        return l;
    }

    /** Card-style container (border, padding). */
    public static VBox card(double spacing, Node... children) {
        VBox v = new VBox(spacing, children);
        v.getStyleClass().add(CARD);
        return v;
    }

    public static VBox innerCard(double spacing, Node... children) {
        VBox v = new VBox(spacing, children);
        v.getStyleClass().add(CARD_INNER);
        return v;
    }

    /*
     * Button roles. Every in-page action uses one of these three, full width and the same height,
     * stacked in this order: primary first, then secondary, destructive last.
     */

    /** Primary action button (filled). */
    public static Button primaryButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().addAll(BTN, BTN_BLOCK);
        return b;
    }

    /** Secondary action (outlined, same size as {@link #primaryButton}), e.g. "Change reminder", "Send test email". */
    public static Button secondaryButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().addAll(BTN_BACK, BTN_BLOCK);
        return b;
    }

    /** Destructive action (red outline, same size as {@link #primaryButton}), e.g. "Cancel appointment". */
    public static Button destructiveButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().addAll(BTN_DANGER, BTN_BLOCK);
        return b;
    }

    /** Compact outlined button for the header icons and small inline controls; use {@link #secondaryButton} in pages. */
    public static Button backButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add(BTN_BACK);
        return b;
    }

    /** Compact destructive button for inline rows (e.g. "Remove" on a reminder row); use {@link #destructiveButton} in pages. */
    public static Button dangerButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add(BTN_DANGER);
        return b;
    }

    /** Add-row / affirmative outlined button (same layout as danger, light green). */
    public static Button positiveButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add(BTN_POSITIVE);
        return b;
    }

    /** List row button (e.g. appointment, test result). */
    public static Button listButton() {
        Button b = new Button();
        b.getStyleClass().add(LIST_BTN);
        return b;
    }

    /** Menu-style button (fixed width, e.g. Reminders menu). */
    public static Button menuButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add(MENU_BTN);
        return b;
    }

    public static ToggleButton navButton(String text) {
        ToggleButton b = new ToggleButton(text);
        b.getStyleClass().add(NAV_BTN);
        return b;
    }

    public static Button shortcutButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add(SHORTCUT_BTN);
        b.setWrapText(true);
        b.setTextAlignment(TextAlignment.CENTER);
        return b;
    }

    /** Apply narrow width to a spinner. */
    public static void styleSpinner(Spinner<?> spinner) {
        spinner.getStyleClass().add(SPINNER);
    }

    /**
     * Make an integer spinner editable and faster on press-and-hold.
     * - Users can type values directly (digits only).
     * - Holding the arrows accelerates the step rate.
     */
    public static void tuneIntSpinner(Spinner<Integer> spinner) {
        if (spinner == null) return;
        styleSpinner(spinner);

        spinner.setEditable(true);
        SpinnerValueFactory<Integer> rawVf = spinner.getValueFactory();
        if (rawVf instanceof SpinnerValueFactory.IntegerSpinnerValueFactory vf) {
            StringConverter<Integer> conv = new IntegerStringConverter();
            vf.setConverter(conv);

            TextField editor = spinner.getEditor();
            // Make the editor match the app's text field styling (purple outline).
            if (!editor.getStyleClass().contains(TEXT_FIELD)) {
                editor.getStyleClass().add(TEXT_FIELD);
            }
            editor.setTextFormatter(new TextFormatter<Integer>(conv, vf.getValue(), c -> {
                String text = c.getControlNewText();
                if (text == null || text.isBlank()) return c; // allow clearing while typing
                return text.matches("\\d+") ? c : null;
            }));

            // Commit typed value on focus loss / Enter.
            editor.focusedProperty().addListener((obs, o, n) -> {
                if (!n) commitSpinnerEditor(spinner);
            });
            editor.setOnAction(e -> commitSpinnerEditor(spinner));
        }

        // Install accelerated press-and-hold once skin nodes exist.
        spinner.skinProperty().addListener((obs, o, n) -> installSpinnerAcceleration(spinner));
        installSpinnerAcceleration(spinner);
    }

    private static void commitSpinnerEditor(Spinner<Integer> spinner) {
        SpinnerValueFactory<Integer> rawVf = spinner.getValueFactory();
        if (!(rawVf instanceof SpinnerValueFactory.IntegerSpinnerValueFactory vf)) return;

        TextField editor = spinner.getEditor();
        if (editor == null) return;

        String raw = editor.getText();
        if (raw == null || raw.isBlank()) {
            editor.setText(vf.getConverter() != null ? vf.getConverter().toString(vf.getValue()) : String.valueOf(vf.getValue()));
            return;
        }

        try {
            int v = Integer.parseInt(raw.trim());
            v = Math.max(vf.getMin(), Math.min(vf.getMax(), v));
            vf.setValue(v);
            editor.setText(vf.getConverter() != null ? vf.getConverter().toString(v) : String.valueOf(v));
        } catch (NumberFormatException ignored) {
            editor.setText(vf.getConverter() != null ? vf.getConverter().toString(vf.getValue()) : String.valueOf(vf.getValue()));
        }
    }

    private static void installSpinnerAcceleration(Spinner<Integer> spinner) {
        if (spinner.getScene() == null) {
            spinner.sceneProperty().addListener((obs, o, n) -> installSpinnerAcceleration(spinner));
            return;
        }

        Node inc = spinner.lookup(".increment-arrow-button");
        Node dec = spinner.lookup(".decrement-arrow-button");
        if (inc == null || dec == null) return;

        addHoldAccelerator(inc, spinner::increment);
        addHoldAccelerator(dec, spinner::decrement);
    }

    private static void addHoldAccelerator(Node button, Runnable step) {
        if (Boolean.TRUE.equals(button.getProperties().get("holdAccelInstalled"))) return;
        button.getProperties().put("holdAccelInstalled", true);

        javafx.animation.Timeline timeline = new javafx.animation.Timeline();
        timeline.setCycleCount(javafx.animation.Animation.INDEFINITE);

        final int[] tick = {0};

        button.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> {
            tick[0] = 0;
            timeline.stop();
            timeline.getKeyFrames().setAll(new javafx.animation.KeyFrame(javafx.util.Duration.millis(120), ev -> {
                step.run();
                tick[0]++;
                if (tick[0] == 8) timeline.setRate(2.0);
                if (tick[0] == 16) timeline.setRate(4.0);
                if (tick[0] == 32) timeline.setRate(8.0);
            }));
            timeline.setRate(1.0);
            timeline.playFromStart();
        });

        button.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_RELEASED, e -> timeline.stop());
        button.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_EXITED, e -> timeline.stop());
    }

    /**
     * A label that wraps onto more lines and makes its row taller, even inside a button graphic,
     * a list cell or a TitledPane header. Those ask for the height with no width (-1), which a
     * plain wrapping Label answers with one line, so long names ("Pregnancy Testing and Options
     * Counselling") were cut to "Pregnancy Testing and Options ...". This one answers with the
     * width it was last laid out at.
     */
    public static Label wrappingLabel(String text) {
        Label l = new Label(text) {
            @Override
            protected double computePrefHeight(double width) {
                return super.computePrefHeight(width < 0 && getWidth() > 0 ? getWidth() : width);
            }

            @Override
            protected double computeMinHeight(double width) {
                return computePrefHeight(width);
            }
        };
        l.setWrapText(true);
        l.setMinWidth(0);
        l.widthProperty().addListener((o, was, now) -> {
            if (was.doubleValue() != now.doubleValue() && l.getParent() != null) l.getParent().requestLayout();
        });
        return l;
    }

    /** Muted/secondary text. */
    public static Label muted(String text) {
        Label l = new Label(text);
        l.getStyleClass().add(MUTED);
        return l;
    }

    /** Body text (e.g. detail content, list items). */
    public static Label body(String text) {
        Label l = new Label(text);
        l.setWrapText(true);
        l.getStyleClass().add(BODY);
        return l;
    }

    public static void searchField(TextField field) {
        field.getStyleClass().add(TRK_SEARCH);
    }

    public static void sort(ComboBox<String> box) {
        box.getStyleClass().add(TRK_SORT);
    }

    public static void datePicker(DatePicker datePicker) {
        datePicker.getStyleClass().add(TRK_DATE);
        datePicker.setShowWeekNumbers(false);
    }

    public static Button clearButton(String text) {
        Button b = new Button(text);
        b.getStyleClass().add(TRK_CLEAR);
        return b;
    }

    public static HBox separator(String text) {
        Separator left = new Separator();
        left.setPrefWidth(50);
        Separator right = new Separator();
        right.setPrefWidth(250);
        Label l = new Label(text);
        HBox hbox = new HBox(left, l, right);
        hbox.setAlignment(Pos.CENTER);

        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);

        l.setMinWidth(Region.USE_PREF_SIZE);
        return hbox;
    }

    public static Label error(String text) {
        Label l = new Label(text);
        l.getStyleClass().add(ERROR);
        return l;
    }

    /**
     * Empty label for {@link #showSaveConfirmation(Label)} after successful saves on the same screen.
     */
    public static Label inlineSaveConfirmation() {
        Label l = new Label();
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        l.setAlignment(Pos.CENTER);
        l.setTextAlignment(TextAlignment.CENTER);
        l.getStyleClass().add(INLINE_SAVE_CONFIRM);
        return l;
    }

    /** Every in-place save says "Saved." first, optionally followed by what happens next. */
    public static final String SAVED = "Saved.";

    public static void showSaveConfirmation(Label label) {
        label.setText(SAVED);
    }

    /** "Saved. " followed by a short note, e.g. "Saved. New bookings will use these defaults." */
    public static void showSaveConfirmation(Label label, String detail) {
        label.setText(detail == null || detail.isBlank() ? SAVED : SAVED + " " + detail);
    }

    public static HBox equalSpacing(HBox hbox) {
        int count = hbox.getChildren().size();

        DoubleBinding availableWidth = hbox.widthProperty()
                .subtract(hbox.getSpacing() * (count - 1));

        for (Node child : hbox.getChildren()) {
            if (child instanceof Region region) {
                region.prefWidthProperty().bind(availableWidth.divide(count));
            }
        }

        return hbox;
    }
    /**
     * Same label and styling as {@link #showSaveConfirmation(Label)} — typically the same
     * {@link #inlineSaveConfirmation()} instance at the bottom of the card.
     */
    public static void showTestEmailConfirmation(Label label) {
        label.setText("Test email sent.");
    }

    public static void showResetToDefaultsConfirmation(Label label) {
        label.setText("Reset to your saved defaults.");
    }

    /**
     * Non-interactive row matching {@link view.TrackingView#getEntryButton} layout (left + right labels).
     */
    public static HBox trackingListRow(String left, String right) {
        Label leftLabel = new Label(left);
        leftLabel.setAlignment(Pos.CENTER_LEFT);
        Label rightLabel = new Label(right);
        rightLabel.setAlignment(Pos.CENTER_RIGHT);
        Region space = new Region();
        HBox.setHgrow(space, Priority.ALWAYS);
        HBox row = new HBox(10, leftLabel, space, rightLabel);
        row.setAlignment(Pos.CENTER);
        row.setMaxWidth(Double.MAX_VALUE);
        row.getStyleClass().add(TRACKING_LIST_ROW);
        return row;
    }

    /** Header row for reminder hub cards — grows with the card, avoids fixed tracking row width. */
    public static HBox reminderCardHeader(String left, String right) {
        Label leftLabel = new Label(left);
        leftLabel.getStyleClass().add(TITLE_SM);
        leftLabel.setWrapText(true);
        leftLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(leftLabel, Priority.ALWAYS);
        Label rightLabel = new Label(right);
        HBox.setHgrow(rightLabel, Priority.ALWAYS);
        rightLabel.setWrapText(true);
        rightLabel.setMinWidth(80);
        HBox row = new HBox(12, leftLabel, rightLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        row.getStyleClass().add(REMINDER_CARD_HEADER);
        return row;
    }

    public static <T> void listViewHover(ListView<T> lv) {
        lv.getStyleClass().add(LIST_VIEW_HOVER);
    }

    public static TextField textField(String text) {
        TextField tf = new TextField(text);
        tf.getStyleClass().add(TEXT_FIELD);
        return tf;
    }

    // ---- Shared building blocks ----------------------------------------------------------------

    /** Simple filled 24x24 glyphs for empty states and badges. */
    public static final String ICON_CALENDAR = "M7 1.5h2v2.5H7z M15 1.5h2v2.5h-2z M3 4.5h18v16.5H3z M5 9h14v10H5z M7 11h4v3H7z";
    public static final String ICON_FLASK = "M9 2h6v2h-1v5l5.4 9.2A2 2 0 0 1 17.7 21H6.3a2 2 0 0 1-1.7-2.8L10 9V4H9z";
    public static final String ICON_PILL = "M4.2 13.4l9.2-9.2a4.5 4.5 0 0 1 6.4 6.4l-9.2 9.2a4.5 4.5 0 0 1-6.4-6.4z";
    public static final String ICON_BELL = "M12 2a2 2 0 0 1 2 2v.3A6 6 0 0 1 18 10v5l2 2v1H4v-1l2-2v-5a6 6 0 0 1 4-5.7V4a2 2 0 0 1 2-2z M10 19.5h4a2 2 0 0 1-4 0z";
    public static final String ICON_SEARCH = "M10 2a8 8 0 0 1 6.3 12.9l5.4 5.4-1.4 1.4-5.4-5.4A8 8 0 1 1 10 2z M10 4.5a5.5 5.5 0 1 0 0 11a5.5 5.5 0 0 0 0-11z";
    public static final String ICON_CHECK = "M9 16.2L4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4z";

    /** A shape-filled region (colour from CSS via -fx-background-color). */
    public static Region glyph(String svg, double size, String styleClass) {
        javafx.scene.shape.SVGPath path = new javafx.scene.shape.SVGPath();
        path.setContent(svg);
        path.setFillRule(javafx.scene.shape.FillRule.EVEN_ODD);
        Region r = new Region();
        r.setShape(path);
        r.setMinSize(size, size);
        r.setPrefSize(size, size);
        r.setMaxSize(size, size);
        r.getStyleClass().add(styleClass);
        return r;
    }

    /** Friendly empty state: a round icon, a short title and one line of help. */
    public static VBox emptyState(String icon, String title, String body) {
        StackPane badge = new StackPane(glyph(icon, 26, "empty-state-glyph"));
        badge.getStyleClass().add("empty-state-icon");
        Label t = new Label(title);
        t.getStyleClass().add("empty-state-title");
        t.setWrapText(true);
        t.setTextAlignment(TextAlignment.CENTER);
        Label b = new Label(body);
        b.getStyleClass().add("empty-state-body");
        b.setWrapText(true);
        b.setTextAlignment(TextAlignment.CENTER);
        b.setMaxWidth(320);
        VBox box = new VBox(6, badge, t, b);
        VBox.setMargin(t, new javafx.geometry.Insets(8, 0, 0, 0));
        box.getStyleClass().add("empty-state");
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    /** Page-top booking progress: "Step 2 of 5 · Service" over a segmented bar. */
    public static VBox steps(int step, int total, String name) {
        Label count = new Label("STEP " + step + " OF " + total);
        count.getStyleClass().add("booking-step-label");
        Label what = new Label("·  " + name);
        what.getStyleClass().add("booking-step-name");
        HBox labels = new HBox(6, count, what);
        labels.setAlignment(Pos.CENTER_LEFT);
        HBox bars = new HBox(4);
        for (int i = 1; i <= total; i++) {
            Region bar = new Region();
            bar.getStyleClass().add("booking-step-bar");
            if (i <= step) bar.getStyleClass().add("done");
            HBox.setHgrow(bar, Priority.ALWAYS);
            bar.setMaxWidth(Double.MAX_VALUE);
            bars.getChildren().add(bar);
        }
        VBox box = new VBox(6, labels, bars);
        box.getStyleClass().add("booking-steps");
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    /** Muted helper text under a page heading. */
    public static Label intro(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("page-intro");
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    public static Label pageHeading(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("page-heading");
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    /** A labelled value on a detail page ("CLINIC" above "Portswood Sexual Health Clinic"). */
    public static VBox detail(String label, String value) {
        Label l = new Label(label);
        l.getStyleClass().add("detail-label");
        Label v = new Label(value == null || value.isBlank() ? "Not recorded" : value);
        v.getStyleClass().add("detail-value");
        v.setWrapText(true);
        v.setMaxWidth(Double.MAX_VALUE);
        return new VBox(2, l, v);
    }

    /** Big bold title for a detail card. */
    public static Label detailTitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("detail-title");
        l.setWrapText(true);
        return l;
    }

    /** A tip with a shortcut button beside it, e.g. "Keep on top of your sexual health … [Manage reminders]". */
    public static HBox callout(String text, Button action) {
        Label l = body(text);
        l.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(l, Priority.ALWAYS);
        action.setMinWidth(Region.USE_PREF_SIZE);
        action.setMaxWidth(170);
        HBox row = new HBox(12, l, action);
        row.getStyleClass().add("callout");
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    /**
     * Gives a dialog the app's look: same stylesheets as the main window, no stock icon, the app
     * icon in the title bar, and a fixed width so long messages wrap instead of being cut off.
     */
    public static void styleDialog(Dialog<?> dialog, javafx.scene.Scene owner) {
        DialogPane pane = dialog.getDialogPane();
        if (owner != null) {
            pane.getStylesheets().setAll(owner.getStylesheets());
            if (owner.getWindow() != null && dialog.getOwner() == null) dialog.initOwner(owner.getWindow());
        }
        if (!pane.getStylesheets().contains(STYLE)) pane.getStylesheets().add(0, STYLE);
        dialog.setGraphic(null);
        pane.setGraphic(null);
        pane.setPrefWidth(380);
        pane.setMinHeight(Region.USE_PREF_SIZE);
        if (pane.getScene() != null && pane.getScene().getWindow() instanceof javafx.stage.Stage s) {
            s.getIcons().setAll(new javafx.scene.image.Image("/icons/safe.png"));
        }
        // Buttons keep their full label ("Cancel appointment", not "Cancel appoint...").
        Runnable fitButtons = () -> {
            for (ButtonType type : pane.getButtonTypes()) {
                if (pane.lookupButton(type) instanceof Button b) {
                    ButtonBar.setButtonUniformSize(b, false);
                    b.setMinWidth(Region.USE_PREF_SIZE);
                }
            }
        };
        fitButtons.run();
        pane.getButtonTypes().addListener((javafx.collections.ListChangeListener<ButtonType>) c -> fitButtons.run());
    }
}
