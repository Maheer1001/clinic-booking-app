package view;

import app.Data;
import controller.AccountController;
import controller.Background;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import service.AccountService;
import view.ui.UI;

import java.time.LocalDate;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

public class AccountView {

    /** The choices in the "Discard your changes?" dialog. */
    enum LeaveChoice { SAVE, DISCARD, KEEP_EDITING }

    /**
     * Key in the Settings page's {@link Node#getProperties()} for its {@code BooleanSupplier} leave guard;
     * {@link Navigator#goToSettings()} registers it with {@link Navigator#setLeaveGuard}.
     */
    public static final String LEAVE_GUARD = "settings.leaveGuard";

    /** Shows the dialog; replaced in tests. */
    static Supplier<LeaveChoice> askDiscard = AccountView::askDiscard;

    /**
     * Name, phone and date of birth. The returned node carries a leave guard that asks
     * "Discard your changes?" when the user leaves Settings with unsaved edits. The email is not edited
     * here (see {@link AccountSecurityView}); Save always sends back the email stored in the database.
     * The node carries the guard under {@link #LEAVE_GUARD}.
     */
    public static Node getPage(int userId) {
        AccountController controller = new AccountController();

        Label msg = new Label();
        msg.setMaxWidth(Double.MAX_VALUE);
        msg.setAlignment(Pos.CENTER);
        msg.setTextAlignment(TextAlignment.CENTER);
        msg.setWrapText(true);

        TextField first = UI.textField("");
        TextField last = UI.textField("");
        TextField phone = UI.textField("");
        Label phoneCount = UI.muted("");
        first.setTextFormatter(maxLength(AccountService.MAX_NAME_LENGTH));
        last.setTextFormatter(maxLength(AccountService.MAX_NAME_LENGTH));
        first.setAccessibleText("First name");
        last.setAccessibleText("Last name");
        phone.setAccessibleText("Phone number");
        phone.setPromptText("Optional, 11 digits");

        DatePicker dob = new DatePicker();
        dob.setShowWeekNumbers(false);
        dob.setAccessibleText("Date of birth");
        dob.setPromptText("Optional");
        dob.setMaxWidth(Double.MAX_VALUE);
        // Force DOB to be a real date (picker only) to avoid typed junk.
        dob.setEditable(false);
        // Grey out days that can't be a date of birth.
        dob.setDayCellFactory(p -> new DateCell() {
            @Override
            public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setDisable(empty || item == null || !AccountService.isValidDob(item));
            }
        });
        // Date of birth is optional, and the picker can't be emptied by typing, so offer Clear.
        Button clearDob = UI.backButton("Clear");
        clearDob.setAccessibleText("Clear date of birth");
        clearDob.setMinWidth(Region.USE_PREF_SIZE);
        clearDob.setOnAction(e -> dob.setValue(null));
        clearDob.visibleProperty().bind(dob.valueProperty().isNotNull());
        clearDob.managedProperty().bind(clearDob.visibleProperty());
        HBox.setHgrow(dob, Priority.ALWAYS);
        HBox dobRow = new HBox(8, dob, clearDob);
        dobRow.setAlignment(Pos.CENTER_LEFT);

        Label formHint = UI.muted("");
        formHint.setWrapText(true);
        formHint.managedProperty().bind(formHint.textProperty().isNotEmpty());
        formHint.visibleProperty().bind(formHint.textProperty().isNotEmpty());

        Button save = UI.primaryButton("Save");
        VBox saveBox = new VBox(save);
        saveBox.setAlignment(Pos.CENTER);

        // Phone: digits-only + exactly 11 digits (UK).
        phone.setTextFormatter(phoneDigits());

        AccountService.AccountInfo info = controller.loadAccount(userId);
        // What is in the database: the baseline for "is anything unsaved?".
        AccountFormState[] saved = new AccountFormState[1];
        if (info == null) {
            msg.getStyleClass().setAll(UI.ERROR);
            msg.setText("Could not load account.");
            saved[0] = new AccountFormState("", "", "", null);
        } else {
            first.setText(nullToEmpty(info.firstName));
            last.setText(nullToEmpty(info.lastName));
            // the field only accepts digits, so a number saved as "07700 900123" would otherwise vanish
            phone.setText(nullToEmpty(info.phone).replaceAll("[^0-9]", ""));
            dob.setValue(info.dob);
            saved[0] = new AccountFormState(first.getText(), last.getText(), phone.getText(), dob.getValue());
        }
        Supplier<AccountFormState> current = () ->
                new AccountFormState(first.getText(), last.getText(), phone.getText(), dob.getValue());

        BooleanProperty saving = new SimpleBooleanProperty(false);
        Runnable updateValidity = () -> {
            AccountFormState s = current.get();
            int len = s.phone().length();
            phoneCount.setText(len == 0 ? "" : (len + "/11"));

            phone.getStyleClass().remove("input-invalid");
            dob.getStyleClass().remove("input-invalid");
            if (!s.phoneOk()) phone.getStyleClass().add("input-invalid");
            if (!s.dobOk()) dob.getStyleClass().add("input-invalid");

            // Say why Save is disabled instead of leaving the user guessing.
            formHint.setText(s.hint());
            save.setDisable(saving.get() || !s.canSave());
        };

        phone.textProperty().addListener((obs, o, n) -> updateValidity.run());
        dob.valueProperty().addListener((obs, o, n) -> updateValidity.run());
        saving.addListener((obs, o, n) -> updateValidity.run());
        updateValidity.run();

        // A stale "saved" or error message shouldn't linger once the user edits again.
        first.textProperty().addListener((obs, o, n) -> msg.setText(""));
        last.textProperty().addListener((obs, o, n) -> msg.setText(""));
        phone.textProperty().addListener((obs, o, n) -> msg.setText(""));
        dob.valueProperty().addListener((obs, o, n) -> msg.setText(""));

        // Puts a finished save on screen; true when it worked.
        Function<AccountService.SaveResult, Boolean> showResult = r -> {
            saving.set(false);
            save.setText("Save");
            if (r == AccountService.SaveResult.SUCCESS) {
                AccountFormState s = current.get();
                // show exactly what was stored (names are saved trimmed)
                first.setText(s.firstName());
                last.setText(s.lastName());
                saved[0] = current.get();
                msg.getStyleClass().setAll(UI.INLINE_SAVE_CONFIRM);
                UI.showSaveConfirmation(msg, "Your details are up to date.");
                return true;
            }
            msg.getStyleClass().setAll(UI.ERROR);
            msg.setText(saveError(r));
            return false;
        };
        Runnable doSave = () -> {
            AccountFormState s = current.get();
            if (!s.canSave() || saving.get()) return;
            saving.set(true);
            save.setText("Saving…");
            Background.run(() -> persist(controller, userId, s), showResult::apply,
                    ex -> showResult.apply(AccountService.SaveResult.DB_ERROR));
        };
        save.setOnAction(e -> doSave.run());
        msg.visibleProperty().bind(msg.textProperty().isNotEmpty());
        msg.managedProperty().bind(msg.visibleProperty());

        GridPane g = new GridPane();
        g.getStyleClass().addAll(UI.PAGE, "account-form-grid");

        int r = 0;
        g.addRow(r++, formLabel("First name"), first);
        g.addRow(r++, formLabel("Last name"), last);
        // The "7/11" counter gets its own row under the field, so the Phone label stays level with
        // the field like the other labels (it sat lower while the counter shared the row).
        phoneCount.setMaxWidth(Double.MAX_VALUE);
        phoneCount.setAlignment(Pos.CENTER_RIGHT);
        phoneCount.managedProperty().bind(phoneCount.textProperty().isNotEmpty());
        phoneCount.visibleProperty().bind(phoneCount.managedProperty());
        g.addRow(r++, formLabel("Phone"), phone);
        g.add(phoneCount, 1, r++);
        g.addRow(r, formLabel("Date of birth"), dobRow);
        g.setMaxWidth(Double.MAX_VALUE);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setMinWidth(Region.USE_PREF_SIZE);

        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);

        g.getColumnConstraints().addAll(col1, col2);

        VBox page = new VBox(10, g, formHint, saveBox, msg);

        // Asked by Navigator before leaving Settings (Back, the gear, a tab, going home) and by Log Out.
        BooleanSupplier leaveGuard = () -> {
            AccountFormState s = current.get();
            if (!s.differsFrom(saved[0]) || saving.get()) return true;
            switch (askDiscard.get()) {
                case DISCARD -> {
                    // Settings stays open in its tab's history, so put the saved values back
                    AccountFormState back = saved[0];
                    first.setText(back.firstName());
                    last.setText(back.lastName());
                    phone.setText(back.phone());
                    dob.setValue(back.dob());
                    msg.setText("");
                    return true;
                }
                case SAVE -> {
                    if (!s.canSave()) {
                        // stay so the user can fix it; the hint under the form says what is wrong
                        updateValidity.run();
                        (s.phoneOk() ? dob : phone).requestFocus();
                        return false;
                    }
                    // The guard must answer now, so this one save waits for the database; it only
                    // happens after the user chose Save in the dialog. Stays on Settings if it fails.
                    AccountService.SaveResult result;
                    try {
                        result = persist(controller, userId, s);
                    } catch (RuntimeException ex) {
                        result = AccountService.SaveResult.DB_ERROR;
                    }
                    return showResult.apply(result);
                }
                default -> {
                    return false;
                }
            }
        };
        page.getProperties().put(LEAVE_GUARD, leaveGuard);
        return page;
    }

    /** "Discard your changes?" with Save / Discard / Keep editing (closing the dialog keeps editing). */
    static LeaveChoice askDiscard() {
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.YES);
        ButtonType discardType = new ButtonType("Discard", ButtonBar.ButtonData.NO);
        ButtonType keepType = new ButtonType("Keep editing", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", saveType, discardType, keepType);
        alert.setTitle("Unsaved changes");
        alert.setHeaderText("Discard your changes?");
        alert.setContentText("You changed your account details but haven't saved them.");
        UI.styleDialog(alert, Data.getScene());
        DialogPane pane = alert.getDialogPane();
        pane.lookupButton(saveType).getStyleClass().add(UI.ALERT_AGREE);
        pane.lookupButton(discardType).getStyleClass().add(UI.BTN_DANGER);
        pane.lookupButton(keepType).getStyleClass().add(UI.ALERT_IGNORE);
        ((Button) pane.lookupButton(saveType)).setDefaultButton(true);
        ButtonType chosen = alert.showAndWait().orElse(keepType);
        if (chosen == saveType) return LeaveChoice.SAVE;
        if (chosen == discardType) return LeaveChoice.DISCARD;
        return LeaveChoice.KEEP_EDITING;
    }

    /**
     * Saves name, phone and date of birth. The email is never edited here: it sends back exactly what is
     * stored now (it may have been changed through "Change email" since the page was built).
     */
    static AccountService.SaveResult persist(AccountController controller, int userId, AccountFormState s) {
        AccountService.AccountInfo now = controller.loadAccount(userId);
        if (now == null || now.email == null) return AccountService.SaveResult.DB_ERROR;
        return controller.saveAccount(userId, trimToNull(s.firstName()), trimToNull(s.lastName()),
                trimToNull(s.phone()), s.dob(), now.email);
    }

    static String saveError(AccountService.SaveResult r) {
        return switch (r) {
            case SUCCESS -> "";
            case INVALID_NAME -> "Names can be at most " + AccountService.MAX_NAME_LENGTH + " characters.";
            case INVALID_EMAIL -> "Invalid email.";
            case EMAIL_ALREADY_USED -> "Email already used.";
            case INVALID_PHONE -> "Phone number must be exactly 11 digits.";
            case INVALID_DOB -> "Please choose a valid date of birth (not in the future).";
            case DB_ERROR -> "We couldn't reach the server. Please try again.";
        };
    }

    /** Caps the text length; an over-long paste is cut to fit instead of being dropped entirely. */
    static TextFormatter<String> maxLength(int max) {
        return new TextFormatter<>(change -> {
            int overflow = change.getControlNewText().length() - max;
            if (overflow <= 0) return change;
            String added = change.getText();
            if (added.length() < overflow) return null;
            change.setText(added.substring(0, added.length() - overflow));
            // keep the caret/anchor inside the shortened text
            int end = change.getRangeStart() + change.getText().length();
            change.selectRange(end, end);
            return change;
        });
    }

    /**
     * Digits only, at most 11. Spaces, dashes and brackets in a pasted number such as "07700 900123"
     * are dropped instead of the whole paste being refused (it used to do nothing at all).
     */
    static TextFormatter<String> phoneDigits() {
        return new TextFormatter<>(change -> {
            if (change.getText() == null || change.getText().isEmpty()) return change;
            String digits = change.getText().replaceAll("\\D", "");
            int newLength = change.getControlText().length() - (change.getRangeEnd() - change.getRangeStart()) + digits.length();
            if (newLength > 11) return null;
            change.setText(digits);
            int end = change.getRangeStart() + digits.length();
            change.selectRange(end, end);
            return change;
        });
    }

    static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    static Label formLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("form-label");
        l.setMinWidth(Region.USE_PREF_SIZE);
        return l;
    }
}
