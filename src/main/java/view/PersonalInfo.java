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

import java.time.LocalDate;
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

        Label problem = UI.error("");
        problem.setWrapText(true);
        problem.managedProperty().bind(problem.textProperty().isNotEmpty());
        problem.visibleProperty().bind(problem.textProperty().isNotEmpty());
        first.textProperty().addListener((o, a, n) -> problem.setText(""));
        last.textProperty().addListener((o, a, n) -> problem.setText(""));
        phone.textProperty().addListener((o, a, n) -> problem.setText(""));
        dob.valueProperty().addListener((o, a, n) -> problem.setText(""));

        next.setOnAction(e -> {
            // Nothing was checked before, so a blank name, a birthday in the future or "abc" as a
            // phone number went straight through to the review page.
            String error = validate(first.getText(), last.getText(), dob.getValue(), phone.getText());
            if (!error.isEmpty()) {
                problem.setText(error);
                return;
            }
            var b = Data.getBooking();
            b.setFirstName(first.getText().strip());
            b.setLastName(last.getText().strip());
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
        dob.setPromptText("Optional, choose a date");
        // Picked from the calendar only (as on Account): typed text that didn't parse was silently ignored.
        dob.setEditable(false);
        dob.setDayCellFactory(p -> new DateCell() {
            @Override
            public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setDisable(empty || item == null || !AccountService.isValidDob(item));
            }
        });
        phone.setPromptText("Optional, 11 digits");
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

        VBox page = UI.pageTop(UI.steps(4, 5, "Your details"), form, l, problem, next);
        ScrollPane sp = new ScrollPane(page);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        return sp;
    }

    /**
     * What is wrong with the details, or "" if they can be used. Names are required; date of birth and
     * phone are optional but must be real (same rules as the Account page).
     */
    static String validate(String first, String last, LocalDate dob, String phone) {
        boolean noFirst = first == null || first.isBlank();
        boolean noLast = last == null || last.isBlank();
        if (noFirst || noLast) {
            return noFirst && noLast ? "Enter your first and last name."
                    : noFirst ? "Enter your first name." : "Enter your last name.";
        }
        if (first.strip().length() > AccountService.MAX_NAME_LENGTH || last.strip().length() > AccountService.MAX_NAME_LENGTH) {
            return "Names can be at most " + AccountService.MAX_NAME_LENGTH + " characters.";
        }
        if (dob != null && !AccountService.isValidDob(dob)) {
            return "Enter a real date of birth. It can't be in the future.";
        }
        String digits = phone == null ? "" : phone.replaceAll("[\\s-]", "");
        if (!digits.isEmpty() && !digits.matches("\\d{11}")) {
            return "Phone number must be 11 digits, or leave it empty.";
        }
        return "";
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

