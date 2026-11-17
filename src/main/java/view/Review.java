package view;

import app.Data;
import controller.BookingController;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import model.Book;
import model.Clinic;
import model.Item;
import model.Service;
import service.BookingService;
import view.ui.UI;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Review {

    private static final DateTimeFormatter DOB_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEE d MMM yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public static Node getPage(BookingController controller) {
        Book b = Data.getBooking();
        Clinic clinic = b.getClinic();
        List<Item> items = b.getItems();

        if (clinic == null || items == null || items.isEmpty()) {
            return UI.page(UI.muted("Please select a clinic, service and time first."));
        }

        VBox clinicInfo = UI.card(10, UI.heading("Clinic"));
        addLine(clinicInfo, "Name", clinic.getName());
        addLine(clinicInfo, "Address", clinic.getAddress());
        addLine(clinicInfo, "Postcode", clinic.getPostcode());

        VBox serviceInfo = UI.card(10, UI.heading(items.size() > 1 ? "Appointments" : "Appointment"));
        for (Item item : items) {
            Service service = item.getService();
            VBox details = UI.innerCard(8, UI.heading(service.getName()));
            if (service.getDescription() != null && !service.getDescription().isBlank()) {
                details.getChildren().add(UI.body(service.getDescription()));
            }
            details.getChildren().add(UI.body("When: " + describeTime(item)));
            details.getChildren().add(UI.body("Duration: " + service.getDurationMinutes() + " minutes"));
            serviceInfo.getChildren().add(details);
        }

        List<Label> labels = new ArrayList<>();
        String name = (nullToEmpty(b.getFirstName()) + " " + nullToEmpty(b.getLastName())).trim();
        if (!name.isEmpty()) {
            labels.add(UI.body("Name: " + name));
        }
        LocalDate dob = b.getDob();
        if (dob != null) {
            labels.add(UI.body("Date of birth: " + dob.format(DOB_FMT)));
        }
        addIfPresent(labels, "Email", b.getEmail());
        addIfPresent(labels, "Phone", b.getPhone());
        addIfPresent(labels, "Gender", b.getGender());
        addIfPresent(labels, "Sexuality", b.getSexuality());
        addIfPresent(labels, "Accessibility needs", b.getDisabilities());
        addIfPresent(labels, "Your notes", b.getReason());

        VBox clientInfo = UI.card(10, UI.heading("Your details"));
        if (labels.isEmpty()) {
            clientInfo.getChildren().add(UI.muted("No personal details entered."));
        } else {
            clientInfo.getChildren().addAll(labels);
        }

        Label confirmMessage = UI.error("");
        confirmMessage.setWrapText(true);
        confirmMessage.managedProperty().bind(confirmMessage.textProperty().isNotEmpty());
        confirmMessage.visibleProperty().bind(confirmMessage.textProperty().isNotEmpty());

        Button changeTime = UI.backButton("Choose a different time");
        changeTime.setOnAction(e -> controller.goToTimeslotsForItems());
        changeTime.setVisible(false);
        changeTime.managedProperty().bind(changeTime.visibleProperty());

        Button confirm = UI.primaryButton("Confirm booking");
        confirm.setOnAction(e -> {
            confirmMessage.setText("");
            if (Data.getCurrentUserId() < 0) {
                confirmMessage.setText("You must be logged in to book.");
                return;
            }
            if (items.stream().anyMatch(i -> i.getDate() == null || i.getTime() == null)) {
                confirmMessage.setText("Please choose a time for every appointment.");
                return;
            }
            // Disable straight away so a double click can't book twice.
            confirm.setDisable(true);
            BookingService.BookingResult result = controller.confirmBooking();
            if (!result.anyBooked()) {
                confirmMessage.setText("Sorry, that time is no longer available. Please choose a different time.");
                changeTime.setVisible(true);
                confirm.setDisable(false);
            }
        });

        ScrollPane sp = new ScrollPane(UI.pageTop(clinicInfo, serviceInfo, clientInfo, confirmMessage, confirm, changeTime));
        sp.setFitToWidth(true);

        return sp;
    }

    /** e.g. "Wed 25 Feb 2026, 10:30 - 11:00". */
    static String describeTime(Item item) {
        if (item.getDate() == null || item.getTime() == null) return "Not chosen";
        String text = item.getDate().format(DATE_FMT) + ", " + item.getTime().format(TIME_FMT);
        if (item.getService() != null && item.getService().getDurationMinutes() > 0) {
            text += " - " + item.getTime().plusMinutes(item.getService().getDurationMinutes()).format(TIME_FMT);
        }
        return text;
    }

    private static void addLine(VBox box, String label, String value) {
        if (value != null && !value.isBlank()) {
            box.getChildren().add(UI.body(label + ": " + value));
        }
    }

    private static void addIfPresent(List<Label> labels, String label, String value) {
        if (value != null && !value.isBlank()) {
            labels.add(UI.body(label + ": " + value));
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
