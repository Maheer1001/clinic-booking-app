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
import view.ui.UI;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class Review {

    public static Node getPage(BookingController controller) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yy");
        Book b = Data.getBooking();
        Clinic clinic = b.getClinic();
        List<Item> items = b.getItems();

        Label clinicName = new Label("Name: " + clinic.getName());
        clinicName.setWrapText(true);
        Label clinicAddress = new Label("Address: " + clinic.getAddress());
        clinicAddress.setWrapText(true);
        Label clinicPostcode = new Label("Postcode: " + clinic.getPostcode());
        clinicPostcode.setWrapText(true);

        VBox clinicInfo = UI.card(10, UI.heading("Clinic Information"),
                clinicName, clinicAddress, clinicPostcode);

        VBox serviceInfo;
        if (items.size() > 1) {
            serviceInfo = UI.card(10, UI.heading("Appointments:"));
            for (Item item : items) {
                Service service = item.getService();
                Label serviceName = new Label(service.getName());
                serviceName.setWrapText(true);
                Label serviceDescription = new Label(service.getDescription());
                serviceDescription.setWrapText(true);
                Label serviceDuration = new Label("Duration: " + item.getService().getDurationMinutes() + " minutes");
                serviceDuration.setWrapText(true);
                Label serviceDate = new Label("Appointment Date: " +
                        item.getTime() + " " +
                        item.getDate().format(formatter));
                serviceDate.setWrapText(true);

                serviceInfo.getChildren().add(UI.innerCard(8, serviceName, serviceDescription, serviceDuration, serviceDate));
            }
        } else {
            serviceInfo = UI.card(10, UI.heading("Appointment Details:"));
            for (Item item : items) {
                Service service = item.getService();
                Label serviceName = new Label(service.getName());
                serviceName.setWrapText(true);
                Label serviceDescription = new Label(service.getDescription());
                serviceDescription.setWrapText(true);
                Label serviceDuration = new Label("Duration: " + item.getService().getDurationMinutes() + " minutes");
                serviceDuration.setWrapText(true);
                Label serviceDate = new Label("Appointment Date: " +
                        item.getTime() + " " +
                        item.getDate().format(formatter));
                serviceDate.setWrapText(true);

                serviceInfo.getChildren().add(UI.card(8, serviceName, serviceDescription, serviceDuration, serviceDate));
            }
        }


        List<Label> labels = new ArrayList<>();

        String fName = b.getFirstName();
        String lName = b.getLastName();
        if (fName != null && !fName.isEmpty() && lName != null && !lName.isEmpty()) {
            labels.add(new Label("Name: " + fName + " " + lName));
        } else if (fName != null && !fName.isEmpty()) {
            labels.add(new Label("First name: " + fName));
        } else if (lName != null && !lName.isEmpty()) {
            labels.add(new Label("Last name: " + lName));
        }
        LocalDate dob = b.getDob();
        if (dob != null) {
            labels.add(new Label("Date of Birth: " + dob.format(formatter)));
        }
        String email = b.getEmail();
        if (email != null && !email.isEmpty()) {
            labels.add(new Label("Email: " + email));
        }
        String phone = b.getPhone();
        if (phone != null && !phone.isEmpty()) {
            labels.add(new Label("Phone: " + phone));
        }
        String gender = b.getGender();
        if (gender != null && !gender.isEmpty()) {
            labels.add(new Label("Gender: " + gender));
        }
        String sexuality = b.getSexuality();
        if (sexuality != null && !sexuality.isEmpty()) {
            labels.add(new Label("Sexuality: " + sexuality));
        }
        String disabilities = b.getDisabilities();
        if (disabilities != null && !disabilities.isEmpty()) {
            labels.add(new Label("Disabilities: " + disabilities));
        }
        String reason = b.getReason();
        if (reason != null && !reason.isEmpty()) {
            labels.add(new Label("Your notes: " + reason));
        }

        VBox clientInfo = UI.card(10, UI.heading("Personal Information:"));

        for (Label label : labels) {
            label.setWrapText(true);
            clientInfo.getChildren().add(label);
        }

        Label confirmMessage = new Label("");
        Button confirm = UI.primaryButton("Confirm");
        confirm.setOnAction(e -> {
            int userId = Data.getCurrentUserId();
            int timeslotId = b.getTimeslotId();
            if (userId < 0) {
                confirmMessage.setText("You must be logged in to book.");
                return;
            }
            if (timeslotId < 0) {
                confirmMessage.setText("Please select a time slot.");
                return;
            }
            controller.confirmBooking();
        });

        ScrollPane sp = new ScrollPane(UI.pageTop(clinicInfo, serviceInfo, clientInfo, confirm, confirmMessage));
        sp.setFitToWidth(true);

        return sp;
    }
}
