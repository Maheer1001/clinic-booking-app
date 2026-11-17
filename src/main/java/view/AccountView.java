package view;

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
        DatePicker dob = new DatePicker();
        dob.setShowWeekNumbers(false);
        // Force DOB to be a real date (picker only) to avoid typed junk.
        dob.setEditable(false);
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
            phone.setText(nullToEmpty(info.phone));
            dob.setValue(info.dob);
            immutableEmail = nullToEmpty(info.email);
            emailValue.setText(immutableEmail);
        }

        Runnable updateValidity = () -> {
            String p = phone.getText() != null ? phone.getText().trim() : "";
            int len = p.length();
            phoneCount.setText(len == 0 ? "" : (len + "/11"));

            boolean phoneOk = p.isEmpty() || p.matches("\\d{11}");
            boolean dobOk = dob.getValue() != null && !dob.getValue().isAfter(LocalDate.now());

            phone.getStyleClass().remove("input-invalid");
            dob.getStyleClass().remove("input-invalid");
            if (!phoneOk) phone.getStyleClass().add("input-invalid");
            if (!dobOk) dob.getStyleClass().add("input-invalid");

            save.setDisable(!(phoneOk && dobOk));
        };

        phone.textProperty().addListener((obs, o, n) -> updateValidity.run());
        dob.valueProperty().addListener((obs, o, n) -> updateValidity.run());
        updateValidity.run();

        save.setOnAction(e -> {
            String f = trimToNull(first.getText());
            String l = trimToNull(last.getText());
            String p = trimToNull(phone.getText());
            var d = dob.getValue();

            AccountService.SaveResult r = service.saveAccount(userId, f, l, p, d, immutableEmail);
            switch (r) {
                case SUCCESS -> {
                    msg.getStyleClass().setAll(UI.INLINE_SAVE_CONFIRM);
                    UI.showSaveConfirmation(msg);
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
                    msg.setText("Please enter a valid DOB (not in the future).");
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
        g.addRow(r++, new Label("First name: "), first);
        g.addRow(r++, new Label("Last name: "), last);
        VBox phoneBox = new VBox(4, phone, phoneCount);
        phoneBox.setAlignment(Pos.TOP_LEFT);
        phoneCount.setMaxWidth(Double.MAX_VALUE);
        phoneCount.setAlignment(Pos.CENTER_RIGHT);
        g.addRow(r++, new Label("Phone: "), phoneBox);
        g.addRow(r++, new Label("DOB: "), dob);
        g.addRow(r, new Label("Email: "), emailValue);
        g.setMaxWidth(Double.MAX_VALUE);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setMinWidth(Region.USE_PREF_SIZE);

        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS);

        g.getColumnConstraints().addAll(col1, col2);

        return new VBox(10, g, saveBox, msg);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
