package view;

import app.Data;
import javafx.scene.layout.*;
import service.AccountService;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.text.TextAlignment;
import view.ui.UI;

import java.time.LocalDate;

public class AccountView {

    public static Node getPage(int userId) {
        AccountService service = new AccountService();

        Label msg = new Label();
        msg.setMaxWidth(Double.MAX_VALUE);
        msg.setAlignment(Pos.CENTER);
        msg.setTextAlignment(TextAlignment.CENTER);

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
        dob.setPromptText("Choose a date");
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
        Label formHint = UI.muted("");
        formHint.setWrapText(true);
        formHint.managedProperty().bind(formHint.textProperty().isNotEmpty());
        formHint.visibleProperty().bind(formHint.textProperty().isNotEmpty());
        Label emailValue = UI.body("");
        emailValue.setMaxWidth(Double.MAX_VALUE);

        Button save = UI.primaryButton("Save");
        VBox saveBox = new VBox(save);
        saveBox.setAlignment(Pos.CENTER);

        // Phone: digits-only + exactly 11 digits (UK).
        phone.setTextFormatter(new TextFormatter<String>(change -> {
            if (change.getText() == null || change.getText().isEmpty()) return change;
            String next = change.getControlNewText();
            if (next.length() > 11) return null;
            if (!change.getText().matches("\\d+")) return null;
            return change;
        }));

        AccountService.AccountInfo info = service.loadAccount(userId);
        final String immutableEmail;
        if (info == null) {
            msg.getStyleClass().setAll(UI.ERROR);
            msg.setText("Could not load account.");
            immutableEmail = "";
        } else {
            first.setText(nullToEmpty(info.firstName));
            last.setText(nullToEmpty(info.lastName));
            // the field only accepts digits, so a number saved as "07700 900123" would otherwise vanish
            phone.setText(nullToEmpty(info.phone).replaceAll("[^0-9]", ""));
            dob.setValue(info.dob);
            immutableEmail = nullToEmpty(info.email);
            emailValue.setText(immutableEmail);
        }

        Runnable updateValidity = () -> {
            String p = phone.getText() != null ? phone.getText().trim() : "";
            int len = p.length();
            phoneCount.setText(len == 0 ? "" : (len + "/11"));

            boolean phoneOk = p.isEmpty() || p.matches("\\d{11}");
            boolean dobOk = AccountService.isValidDob(dob.getValue());

            phone.getStyleClass().remove("input-invalid");
            dob.getStyleClass().remove("input-invalid");
            if (!phoneOk) phone.getStyleClass().add("input-invalid");
            if (!dobOk) dob.getStyleClass().add("input-invalid");

            // Say why Save is disabled instead of leaving the user guessing.
            if (!dobOk && !phoneOk) formHint.setText("Add your date of birth and an 11-digit phone number (or leave phone empty) to save.");
            else if (!dobOk) formHint.setText("Add your date of birth to save changes.");
            else if (!phoneOk) formHint.setText("Phone number must be 11 digits, or leave it empty.");
            else formHint.setText("");

            save.setDisable(!(phoneOk && dobOk));
        };

        phone.textProperty().addListener((obs, o, n) -> updateValidity.run());
        dob.valueProperty().addListener((obs, o, n) -> updateValidity.run());
        updateValidity.run();

        // A stale "saved" or error message shouldn't linger once the user edits again.
        first.textProperty().addListener((obs, o, n) -> msg.setText(""));
        last.textProperty().addListener((obs, o, n) -> msg.setText(""));
        phone.textProperty().addListener((obs, o, n) -> msg.setText(""));
        dob.valueProperty().addListener((obs, o, n) -> msg.setText(""));

        save.setOnAction(e -> {
            String f = trimToNull(first.getText());
            String l = trimToNull(last.getText());
            String p = trimToNull(phone.getText());
            var d = dob.getValue();

            AccountService.SaveResult r = service.saveAccount(userId, f, l, p, d, immutableEmail);
            switch (r) {
                case SUCCESS -> {
                    // show exactly what was stored (names are saved trimmed)
                    first.setText(nullToEmpty(f));
                    last.setText(nullToEmpty(l));
                    msg.getStyleClass().setAll(UI.INLINE_SAVE_CONFIRM);
                    UI.showSaveConfirmation(msg);
                    // keep the logged-in user's display name (used in emails) in step with the edit
                    if (Data.getCurrentUserId() == userId) {
                        String name = ((f != null ? f : "") + " " + (l != null ? l : "")).trim();
                        Data.setCurrentUser(userId, immutableEmail, name);
                    }
                }
                case INVALID_NAME -> {
                    msg.getStyleClass().setAll(UI.ERROR);
                    msg.setText("Names can be at most " + AccountService.MAX_NAME_LENGTH + " characters.");
                }
                case INVALID_EMAIL -> {
                    msg.getStyleClass().setAll(UI.ERROR);
                    msg.setText("Invalid email.");
                }
                case EMAIL_ALREADY_USED -> {
                    msg.getStyleClass().setAll(UI.ERROR);
                    msg.setText("Email already used.");
                }
                case INVALID_PHONE -> {
                    msg.getStyleClass().setAll(UI.ERROR);
                    msg.setText("Phone number must be exactly 11 digits.");
                }
                case INVALID_DOB -> {
                    msg.getStyleClass().setAll(UI.ERROR);
                    msg.setText("Please choose a valid date of birth (not in the future).");
                }
                case DB_ERROR -> {
                    msg.getStyleClass().setAll(UI.ERROR);
                    msg.setText("Database error.");
                }
            }
        });
        msg.visibleProperty().bind(msg.textProperty().isNotEmpty());
        msg.managedProperty().bind(msg.visibleProperty());

        GridPane g = new GridPane();
        g.getStyleClass().addAll(UI.PAGE, "account-form-grid");

        int r = 0;
        g.addRow(r++, formLabel("First name"), first);
        g.addRow(r++, formLabel("Last name"), last);
        VBox phoneBox = new VBox(4, phone, phoneCount);
        phoneBox.setAlignment(Pos.TOP_LEFT);
        phoneCount.setMaxWidth(Double.MAX_VALUE);
        phoneCount.setAlignment(Pos.CENTER_RIGHT);
        g.addRow(r++, formLabel("Phone"), phoneBox);
        g.addRow(r++, formLabel("Date of birth"), dob);
        g.addRow(r, formLabel("Email"), emailValue);
        g.setMaxWidth(Double.MAX_VALUE);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setMinWidth(Region.USE_PREF_SIZE);

        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);

        g.getColumnConstraints().addAll(col1, col2);

        return new VBox(10, g, formHint, saveBox, msg);
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

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static Label formLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("form-label");
        l.setMinWidth(Region.USE_PREF_SIZE);
        return l;
    }
}
