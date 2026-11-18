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

    private static final DateTimeFormatter DOB_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.UK);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("EEE d MMM yyyy", java.util.Locale.UK);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public static Node getPage(BookingController controller) {
        Book b = Data.getBooking();
        Clinic clinic = b.getClinic();
        List<Item> items = b.getItems();

        if (clinic == null || items == null || items.isEmpty()) {
            return UI.page(UI.muted("Please select a clinic, service and time first."));
        }

        VBox serviceInfo = UI.card(12);
        for (Item item : items) {
            Service service = item.getService();
            serviceInfo.getChildren().addAll(UI.detailTitle(service.getName()),
                    UI.detail("WHEN", describeTime(item)));
        }
        String address = clinic.getAddress() == null ? "" : clinic.getAddress();
        if (clinic.getPostcode() != null && !clinic.getPostcode().isBlank() && !address.contains(clinic.getPostcode())) {
            address = address.isBlank() ? clinic.getPostcode() : address + ", " + clinic.getPostcode();
        }
        serviceInfo.getChildren().add(UI.detail("CLINIC", clinic.getName() + (address.isBlank() ? "" : "\n" + address)));

        VBox clientInfo = UI.card(10, sectionHeading("Your details"));
        String name = (nullToEmpty(b.getFirstName()) + " " + nullToEmpty(b.getLastName())).trim();
        int before = clientInfo.getChildren().size();
        addIfPresent(clientInfo, "NAME", name);
        LocalDate dob = b.getDob();
        if (dob != null) {
            addIfPresent(clientInfo, "DATE OF BIRTH", dob.format(DOB_FMT));
        }
        addIfPresent(clientInfo, "EMAIL", b.getEmail());
        addIfPresent(clientInfo, "PHONE", b.getPhone());
        addIfPresent(clientInfo, "GENDER", b.getGender());
        addIfPresent(clientInfo, "SEXUALITY", b.getSexuality());
        addIfPresent(clientInfo, "ACCESSIBILITY NEEDS", b.getDisabilities());
        addIfPresent(clientInfo, "YOUR NOTES", b.getReason());
        if (clientInfo.getChildren().size() == before) {
            clientInfo.getChildren().add(UI.muted("No personal details entered."));
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

        Label note = UI.intro("You'll get a confirmation email and can reschedule or cancel at any time from Booking.");
        ScrollPane sp = new ScrollPane(UI.pageTop(UI.steps(5, 5, "Review"),
                UI.pageHeading("Check and confirm"), serviceInfo, clientInfo, note, confirmMessage, confirm, changeTime));
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        return sp;
    }

    /** e.g. "Wed 25 Feb 2026, 10:30 – 11:00". */
    static String describeTime(Item item) {
        if (item.getDate() == null || item.getTime() == null) return "Not chosen";
        String text = item.getDate().format(DATE_FMT) + ", " + item.getTime().format(TIME_FMT);
        if (item.getService() != null && item.getService().getDurationMinutes() > 0) {
            text += " – " + item.getTime().plusMinutes(item.getService().getDurationMinutes()).format(TIME_FMT);
        }
        return text;
    }

    private static void addIfPresent(VBox box, String label, String value) {
        if (value != null && !value.isBlank()) {
            box.getChildren().add(UI.detail(label, value.trim()));
        }
    }

    private static Label sectionHeading(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section-heading");
        return l;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
