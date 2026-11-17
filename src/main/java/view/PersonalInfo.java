package view;

import app.Data;
import controller.BookingController;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import model.Book;
import service.AccountService;
import view.ui.UI;

public class PersonalInfo {

    public static Node getPage(BookingController controller) {
        TextField first = UI.textField(null);
        TextField last = UI.textField(null);
        DatePicker dob = new DatePicker();
        dob.setShowWeekNumbers(false);
        TextField phone = UI.textField(null);
        TextField email = UI.textField(null);
        TextField gender = UI.textField(null);
        gender.setPromptText("Optional");
        TextField sexuality = UI.textField(null);
        sexuality.setPromptText("Optional");
        TextField disabilities = UI.textField(null);
        disabilities.setPromptText("Optional");
        TextArea reason = new TextArea();
        reason.setPromptText("Optional - let your clinician know anything in advance");
        reason.setWrapText(true);
        reason.setPrefRowCount(3);

        // Pre-populate from existing booking data or account info if logged in.
        Book booking = Data.getBooking();
        int currentUserId = Data.getCurrentUserId();
        AccountService.AccountInfo accountInfo = null;
        if (currentUserId > 0) {
            AccountService accountService = new AccountService();
            accountInfo = accountService.loadAccount(currentUserId);
        }

        if (booking.getFirstName() != null && !booking.getFirstName().isBlank()) {
            first.setText(booking.getFirstName());
        } else if (accountInfo != null && accountInfo.firstName != null) {
            first.setText(accountInfo.firstName);
        }

        if (booking.getLastName() != null && !booking.getLastName().isBlank()) {
            last.setText(booking.getLastName());
        } else if (accountInfo != null && accountInfo.lastName != null) {
            last.setText(accountInfo.lastName);
        }

        if (booking.getDob() != null) {
            dob.setValue(booking.getDob());
        } else if (accountInfo != null && accountInfo.dob != null) {
            dob.setValue(accountInfo.dob);
        }

        if (booking.getGender() != null && !booking.getGender().isBlank()) {
            gender.setText(booking.getGender());
        }
        if (booking.getSexuality() != null && !booking.getSexuality().isBlank()) {
            sexuality.setText(booking.getSexuality());
        }
        if (booking.getPhone() != null && !booking.getPhone().isBlank()) {
            phone.setText(booking.getPhone());
        } else if (accountInfo != null && accountInfo.phone != null) {
            phone.setText(accountInfo.phone);
        }
        if (booking.getEmail() != null && !booking.getEmail().isBlank()) {
            email.setText(booking.getEmail());
        } else if (accountInfo != null && accountInfo.email != null) {
            email.setText(accountInfo.email);
        }
        if (booking.getDisabilities() != null && !booking.getDisabilities().isBlank()) {
            disabilities.setText(booking.getDisabilities());
        }
        if (booking.getReason() != null && !booking.getReason().isBlank()) {
            reason.setText(booking.getReason());
        }

        Button next = UI.primaryButton("Next");

        next.setOnAction(e -> {
            var b = Data.getBooking();
            b.setFirstName(first.getText());
            b.setLastName(last.getText());
            b.setDob(dob.getValue());
            b.setGender(gender.getText());
            b.setSexuality(sexuality.getText());
            b.setPhone(phone.getText());
            b.setEmail(email.getText());
            b.setDisabilities(disabilities.getText());
            b.setReason(reason.getText());

            controller.goToReview();
        });

        GridPane g = new GridPane();
        g.setVgap(10);
        g.setHgap(10);

        g.addRow(0, new Label("First Name:"), first);
        g.addRow(1, new Label("Last Name:"), last);
        g.addRow(2, new Label("DOB:"), dob);
        g.addRow(3, new Label("Phone:"), phone);
        //g.addRow(4, new Label("Email:"), email);
        g.addRow(5, new Label("Gender:"), gender);
        g.addRow(6, new Label("Sexuality:"), sexuality);
        g.addRow(7, new Label("Disabilities:"), disabilities);
        g.addRow(8, new Label("Your Notes:"), reason);
        g.setAlignment(Pos.CENTER);

        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setHgrow(Priority.NEVER);
        ColumnConstraints inputCol = new ColumnConstraints();
        inputCol.setHgrow(Priority.ALWAYS);
        inputCol.setFillWidth(true);
        g.getColumnConstraints().addAll(labelCol, inputCol);

        Label l = new Label("All optional fields are given if you would like to receive accommodations in advance of your appointment.");
        l.setWrapText(true);

        reason.setPrefWidth(first.getWidth());
        GridPane.setHgrow(dob, Priority.ALWAYS);
        dob.setMaxWidth(Double.MAX_VALUE);

        VBox box = new VBox(UI.pageTop(g, l, next));
        box.setPadding(new Insets(0, 25, 0, 25));

        ScrollPane sp = new ScrollPane(box);
        sp.setFitToWidth(true);

        return sp;
    }
}

