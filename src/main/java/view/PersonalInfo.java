package view;

import app.Data;
import controller.BookingController;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
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

        first.setAccessibleText("First name");
        last.setAccessibleText("Last name");
        dob.setAccessibleText("Date of birth");
        phone.setAccessibleText("Phone");
        gender.setAccessibleText("Gender, optional");
        sexuality.setAccessibleText("Sexuality, optional");
        disabilities.setAccessibleText("Accessibility needs, optional");
        reason.setAccessibleText("Notes for your clinician, optional");
        dob.setMaxWidth(Double.MAX_VALUE);
        dob.setPromptText("DD/MM/YYYY");
        disabilities.setPromptText("Optional, e.g. step-free access");

        HBox names = new HBox(10, field("First name", first), field("Last name", last));
        for (Node n : names.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);
        HBox about = new HBox(10, field("Date of birth", dob), field("Phone", phone));
        for (Node n : about.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);
        HBox identity = new HBox(10, field("Gender", gender), field("Sexuality", sexuality));
        for (Node n : identity.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        VBox form = UI.card(12, names, about, identity,
                field("Accessibility needs", disabilities),
                field("Anything your clinician should know?", reason));

        Label l = UI.intro("Optional details help the clinic arrange any support you need. Only the clinic sees them.");
        next.setText("Review booking");

        VBox page = UI.pageTop(UI.steps(4, 5, "Your details"), form, l, next);
        ScrollPane sp = new ScrollPane(page);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        return sp;
    }

    /** A form field with its label above it. */
    private static VBox field(String label, Control input) {
        Label l = new Label(label);
        l.getStyleClass().add("form-label");
        l.setLabelFor(input);
        input.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(4, l, input);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }
}

