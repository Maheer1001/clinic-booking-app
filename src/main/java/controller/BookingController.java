package controller;

import view.ClinicMap;
import app.Data;
import dao.UserRepository;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.scene.layout.VBox;
import model.*;
import service.BookingCommunicationPrefs;
import service.AsyncNotifier;
import service.BookingService;
import service.NotificationDispatcher;
import service.email.BookingConfirmationEmail;
import view.DateTime;
import view.Navigator;
import view.PersonalInfo;
import view.Review;
import view.ServiceSelection;
import view.reminders.AppointmentRemindersView;
import view.notification.BookingConfirmationNotification;
import view.reminders.RemindersView;
import view.ui.UI;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import dao.UserDAO;

/**
 * Controller for the booking flow. Coordinates between booking views and BookingService.
 */
public class BookingController {

    private final Navigator nav;
    private final BookingService bookingService;
    private final NotificationDispatcher notificationDispatcher;
    private final ReminderDefaultsReader reminderDefaultsReader;
    private final UserRepository userRepository;
    private static Label messageLabel;
    private Map<Clinic, Double> postcodeMap = new HashMap<>();
    private ClinicMap clinicMap;

    public BookingController(Navigator nav) {
        this(nav, new BookingService(), new NotificationDispatcher(), ReminderDefaultsReader.production(), new UserDAO());
    }

    public BookingController(Navigator nav, BookingService bookingService, NotificationDispatcher notificationDispatcher,
                             ReminderDefaultsReader reminderDefaultsReader, UserRepository userRepository) {
        this.nav = nav;
        this.bookingService = bookingService;
        this.notificationDispatcher = notificationDispatcher;
        this.reminderDefaultsReader = reminderDefaultsReader;
        this.userRepository = userRepository;
        messageLabel = UI.error("");
        // Only takes up space when there is a message.
        messageLabel.managedProperty().bind(messageLabel.textProperty().isNotEmpty());
        messageLabel.visibleProperty().bind(messageLabel.textProperty().isNotEmpty());
    }

    public void checkForDefaultClinic() throws SQLException {
        if (userRepository.defaultClinicExists(Data.getCurrentUserId())) {
            int clinicID = userRepository.getDefaultClinic(Data.getCurrentUserId());
            String defaultCName = bookingService.findClinicNameById(clinicID);
            ButtonType defaultC = new ButtonType("Continue with "+ defaultCName);

            ButtonType ignore = new ButtonType("Select a different clinic", ButtonBar.ButtonData.CANCEL_CLOSE);
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Default Clinic");
            alert.setHeaderText(null);
            alert.setContentText("You have a default clinic saved, would you like to select this clinic?" + "\n\nDefault Clinic: " + defaultCName + "\n");

            DialogPane dialogPane = alert.getDialogPane();
            dialogPane.getStylesheets().addAll(Data.getScene().getStylesheets());
            Stage stage = (Stage) dialogPane.getScene().getWindow();
            stage.getIcons().add(new Image(getClass().getResource("/icons/safe.png").toExternalForm()));
            dialogPane.setGraphic(null);

            alert.getButtonTypes().setAll(defaultC, ignore);

            Button defaultBtn = (Button) dialogPane.lookupButton(defaultC);
            defaultBtn.getStyleClass().add(UI.ALERT_AGREE);
            Button ignoreBtn = (Button) dialogPane.lookupButton(ignore);
            ignoreBtn.getStyleClass().add(UI.ALERT_IGNORE);

            Optional<ButtonType> result = alert.showAndWait();

            if (result.isPresent() && result.get() == defaultC) {
                List<Service> services = bookingService.findServicesByClinicId(clinicID);
                Clinic clinic = bookingService.findClinicById(clinicID);
                clinic.setServices(services);
                Data.getBooking().setClinic(clinic);
                nav.goToPage(ServiceSelection.getPage(this), "Select Service", false);
            }
        }
    }

    public List<Clinic> getClinics() throws SQLException {
        return bookingService.findAllClinics();
    }

    public Map<Clinic, Double> sortClinicsByPostcode(List<Clinic> clinics, String postcode) {
        Map<Clinic, Double> dict = new HashMap<>();
        try {
            Coordinate userCoord = Postcode.getCoord(postcode);

            for (Clinic clinic : clinics) {
                dict.put(clinic, Postcode.haversine(userCoord, Postcode.getCoord(clinic.getPostcode())));
            }
            clinics.sort(
                    Comparator.comparingDouble(dict::get)
            );
            clinicMap.show(clinics, userCoord);
        } catch (IllegalArgumentException e) {
            messageLabel.setText("Invalid postcode");
            clinicMap.show(clinics, null);
        } catch (Exception e) {
            clinics.sort(
                    Comparator.comparingInt((Clinic c) -> Postcode.score(postcode, c.getPostcode()))
                            .reversed()
            );
            clinicMap.show(clinics, null);
        }
        return dict;
    }

    public Node getClinicSearchPage() {
        TextField postcode = new TextField();
        UI.searchField(postcode);
        postcode.setPromptText("Enter postcode");

        postcode.textProperty().addListener((observable, oldValue, newValue) -> messageLabel.setText(""));

        ListView<Clinic> list = new ListView<>();
        UI.listViewHover(list);
        list.setFixedCellSize(-1);
        List<Clinic> clinics;
        try {clinics = getClinics();} catch (SQLException ex) {clinics = new ArrayList<>();}
        list.setItems(FXCollections.observableArrayList(clinics));
        list.setPrefHeight(clinics.size() * 40 + 2);
        list.setCellFactory(l -> new ListCell<>() {
            private final VBox content = new VBox(6);
            private final TitledPane pane = new TitledPane();
            {
                pane.setContent(content);
                pane.setExpanded(false);
                pane.setOnMouseClicked(e -> {
                    Clinic c = getItem();
                    if (c != null) {
                        list.getSelectionModel().select(c);
                    }
                });
            }

            @Override
            protected void updateItem(Clinic item, boolean empty) {

                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    String header = postcodeMap.containsKey(item)
                            ? item.getName() + "   —   " + String.format("%.1f", postcodeMap.get(item)) + " km away"
                            : item.getName();
                    pane.setText(header);
                    content.getChildren().clear();

                    if (item.getAddress() != null && !item.getAddress().isBlank()) {
                        Label address = new Label("Address: " + item.getAddress());
                        address.setWrapText(true);
                        content.getChildren().add(address);
                    }

                    if (item.getPostcode() != null && !item.getPostcode().isBlank()) {
                        Label postcode = new Label ("Postcode: " + item.getPostcode());
                        postcode.setWrapText(true);
                        content.getChildren().add(postcode);
                    }

                    if (item.getPhone() != null && !item.getPhone().isBlank()) {
                        Label phone = new Label("Phone no.: " + item.getPhone());
                        content.getChildren().add(phone);
                    }

                    List<String> diss_acc = item.getDissAcc();
                    if (diss_acc != null && !diss_acc.isEmpty()) {
                        Label dissaccLbl = new Label("Disability accessibility: " + diss_acc);
                        dissaccLbl.setWrapText(true);
                        content.getChildren().add(dissaccLbl);
                    }

                    pane.prefWidthProperty().bind(list.widthProperty().subtract(20));
                    setGraphic(pane);
                    setText(null);
                }
            }
        });

        Label emptyMessage = UI.muted("No clinics available. Add clinics to the database to book.");
        emptyMessage.setWrapText(true);

        Button search = UI.primaryButton("Search");
        Button next = UI.primaryButton("Select Clinic");

        postcode.setOnAction(e -> search.fire()); // Enter searches
        search.setOnAction(e -> {
            postcodeMap = sortClinicsByPostcode(list.getItems(), postcode.getText());
            list.refresh();
        });

        next.setOnAction(e -> {
            Clinic c = list.getSelectionModel().getSelectedItem();
            if (c == null) return;
            try {
                selectClinic(c);
            } catch (SQLException ignored) {}
        });

        HBox hbox = new HBox(10, postcode, search);
        hbox.setAlignment(Pos.CENTER);
        HBox.setHgrow(postcode, Priority.ALWAYS);

        clinicMap = new ClinicMap(380, 240);
        clinicMap.show(clinics, null);

        if (clinics.isEmpty()) {
            return UI.pageTop(hbox, messageLabel, clinicMap.node(), list, emptyMessage, next);
        } else {
            return UI.pageTop(hbox, messageLabel, clinicMap.node(), list, next);
        }
    }
    public void selectClinic(Clinic clinic) throws SQLException {
        int userID = Data.getCurrentUserId();
        if (!userRepository.defaultClinicExists(userID)) {

            ButtonType update = new ButtonType("Save");
            ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle(clinic.getName());
            alert.setHeaderText("Save default clinic");
            alert.setContentText("Would you like to save " + clinic.getName() + " as your default clinic?");

            DialogPane dialogPane = alert.getDialogPane();
            dialogPane.getStylesheets().addAll(Data.getScene().getStylesheets());
            Stage stage = (Stage) dialogPane.getScene().getWindow();
            stage.getIcons().add(new Image(getClass().getResource("/icons/safe.png").toExternalForm()));
            dialogPane.setGraphic(null);

            alert.getButtonTypes().setAll(update, cancel);

            Button defaultBtn = (Button) dialogPane.lookupButton(update);
            defaultBtn.getStyleClass().add(UI.ALERT_AGREE);
            Button ignoreBtn = (Button) dialogPane.lookupButton(cancel);
            ignoreBtn.getStyleClass().add(UI.ALERT_IGNORE);

            Optional<ButtonType> result = alert.showAndWait();

            if (result.isPresent() && result.get() == update) {
                userRepository.updateDefaultClinic(userID, clinic.getId());
            }
        } else {
            if (userRepository.getDefaultClinic(userID) !=  clinic.getId()) {
                ButtonType update = new ButtonType("Update");
                ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle(clinic.getName());
                alert.setHeaderText("Update default clinic");
                Clinic current = bookingService.findClinicById(userRepository.getDefaultClinic(userID));
                alert.setContentText("Would you like to save " + clinic.getName() + " as your default clinic?"
                        + (current != null ? "\nYour current default clinic: " + current.getName() : ""));

                DialogPane dialogPane = alert.getDialogPane();
                dialogPane.getStylesheets().addAll(Data.getScene().getStylesheets());
                Stage stage = (Stage) dialogPane.getScene().getWindow();
                stage.getIcons().add(new Image(getClass().getResource("/icons/safe.png").toExternalForm()));
                dialogPane.setGraphic(null);

                alert.getButtonTypes().setAll(update, cancel);

                Button defaultBtn = (Button) dialogPane.lookupButton(update);
                defaultBtn.getStyleClass().add(UI.ALERT_AGREE);
                Button ignoreBtn = (Button) dialogPane.lookupButton(cancel);
                ignoreBtn.getStyleClass().add(UI.ALERT_IGNORE);

                Optional<ButtonType> result = alert.showAndWait();

                if (result.isPresent() && result.get() == update) {
                    userRepository.updateDefaultClinic(userID, clinic.getId());
                }
            }

        }

        List<Service> services = bookingService.findServicesByClinicId(clinic.getId());
        clinic.setServices(services);
        Data.getBooking().setClinic(clinic);
        nav.goToPage(ServiceSelection.getPage(this), "Select Service", false);
    }

    public void selectService(Service service) {
        Book booking = Data.getBooking();
        booking.setService(service);

        // Ensure there is at least one item for the selected service (single-service flow).
        if (booking.getItems() == null || booking.getItems().isEmpty()) {
            booking.setItems(java.util.List.of(new Item(service)));
        }

        goToTimeslotsForItems();
    }

    /**
     * Navigates to the timeslot selection page for the currently selected clinic and items
     * in the multi-service booking flow.
     */
    public void goToTimeslotsForItems() {
        nav.goToPage(DateTime.getPage(this), "Select Timeslots", false);
    }

    /**
     * Navigates back to the service selection page for the current clinic.
     */
    public void goToServiceSelection() {
        nav.goToPage(ServiceSelection.getPage(this), "Select Service", false);
    }

    public void goToDetails() {
        nav.goToPage(PersonalInfo.getPage(this), "Enter Details", false);
    }

    /**
     * Books the current {@link Data#getBooking()} and, if at least one appointment was saved, shows the
     * "Booking confirmed" page and sends confirmations for the saved appointments only.
     *
     * @return the outcome; when nothing was booked the booking is kept so the user can pick another time
     */
    public BookingService.BookingResult confirmBooking() {
        Book b = Data.getBooking();
        int userId = Data.getCurrentUserId();
        if (userId < 0) {
            return new BookingService.BookingResult(List.of(), List.of());
        }
        BookingService.BookingResult outcome = bookingService.placeBooking(b, userId);
        if (!outcome.anyBooked()) {
            return outcome;
        }
        List<Integer> newAppIds = outcome.booked().stream().map(BookingService.BookedAppointment::appId).toList();

        // Send a booking confirmation email + in-app notification for each saved appointment.
        BookingCommunicationPreferences commPrefs = BookingCommunicationPrefs.load(userId);
        boolean confirmationDiscreet = commPrefs.confirmationDiscreet();
        ReminderTemplateOverrides confirmationTpl = commPrefs.confirmationTemplate();
        String recipientEmail = Data.getCurrentUserEmail() != null ? Data.getCurrentUserEmail() : b.getEmail();
        String recipientName = Data.getCurrentUserName() != null ? Data.getCurrentUserName()
                : (b.getFirstName() + " " + b.getLastName()).trim();
        if (recipientName.isBlank()) {
            recipientName = "Patient";
        }
        Clinic clinic = b.getClinic();
        String clinicName = clinic != null ? clinic.getName() : "";
        boolean hasEmail = recipientEmail != null && !recipientEmail.isBlank();

        for (BookingService.BookedAppointment appt : outcome.booked()) {
            if (appt.date() == null || appt.time() == null) continue;
            String bookingRef = "APP-" + appt.appId(); // same reference the cancel and reschedule emails use
            LocalDateTime appointmentDateTime = LocalDateTime.of(appt.date(), appt.time());
            String serviceName = appt.service() != null ? appt.service().getName() : "";
            AsyncNotifier.notifyUser(
                    notificationDispatcher,
                    userId,
                    hasEmail ? new BookingConfirmationEmail(
                            recipientEmail, recipientName, bookingRef, serviceName,
                            appointmentDateTime, clinicName, "Clinician", confirmationDiscreet,
                            confirmationTpl) : null,
                    new BookingConfirmationNotification(
                            recipientName, bookingRef, serviceName, appointmentDateTime, clinicName, "Clinician",
                            confirmationDiscreet,
                            confirmationTpl
                    )
            );
        }
        Data.reset();

        Optional<AppointmentReminderDefaults> defaultTemplate = userId >= 0
                ? reminderDefaultsReader.load(userId)
                : Optional.empty();
        boolean showUseDefault = !newAppIds.isEmpty()
                && defaultTemplate.filter(AppointmentReminderDefaults::hasSavedTemplate).isPresent();

        Button setupRemindersBtn = UI.primaryButton("Set up reminders");
        Button defaultRemindersBtn = UI.backButton("Use default reminders");
        Button skipBtn = UI.backButton("Skip for now");

        List<Integer> savedIds = List.copyOf(newAppIds);

        setupRemindersBtn.setOnAction(e -> {nav.goToPageNewTab("reminders", new RemindersView(userId).getRemindersScene("appointments"), "Reminders", true); nav.clearTabStack("booking");});
        defaultRemindersBtn.setOnAction(e -> {
            defaultTemplate.filter(AppointmentReminderDefaults::hasSavedTemplate).ifPresent(def -> {
                if (savedIds.isEmpty()) {
                    return;
                }
                ReminderController reminders = new ReminderController(userId);
                String email = Data.getCurrentUserEmail() != null ? Data.getCurrentUserEmail() : "";
                String rawName = Data.getCurrentUserName();
                final String reminderName = (rawName == null || rawName.isBlank()) ? email : rawName;
                for (int appId : savedIds) {
                    reminders.saveAppointmentReminder(appId, def.notificationsEnabled(), def.customDays(), def.customMessage());
                    if (def.notificationsEnabled()) {
                        reminders.getUpcomingAppointments().stream()
                                .filter(a -> a.getAppId() == appId)
                                .findFirst()
                                .ifPresent(appt -> reminders.scheduleAppointmentReminderWithSchedule(
                                        appt, email, reminderName, def.customDays(), def.customMessage()));
                    }
                }
            });
            nav.goToHome();
        });
        skipBtn.setOnAction(e -> nav.goToHome());

        String blurb = showUseDefault
                ? "You can apply your saved reminder defaults, open Reminders to customise per appointment, or finish later from the Reminders tab."
                : "You can set up reminders for your new appointment(s) now, or do it later from the Reminders tab.";

        HBox buttonRow = new HBox(10);
        buttonRow.setAlignment(Pos.CENTER);
        buttonRow.getChildren().add(setupRemindersBtn);
        if (showUseDefault) {
            buttonRow.getChildren().add(defaultRemindersBtn);
        }
        buttonRow.getChildren().add(skipBtn);

        VBox summary = UI.card(8);
        for (BookingService.BookedAppointment appt : outcome.booked()) {
            String service = appt.service() != null ? appt.service().getName() : "Appointment";
            String when = appt.date() != null && appt.time() != null
                    ? appt.date().format(CONFIRMED_DATE_FMT) + " at " + appt.time().format(CONFIRMED_TIME_FMT)
                    : "";
            VBox row = new VBox(2, UI.heading(service));
            if (!when.isEmpty()) row.getChildren().add(UI.body(when));
            if (!clinicName.isBlank()) row.getChildren().add(UI.muted(clinicName));
            summary.getChildren().add(row);
        }

        javafx.scene.layout.VBox prompt = UI.page(
                UI.titleSm("Booking confirmed"),
                summary
        );
        if (!outcome.unavailable().isEmpty()) {
            Label notBooked = UI.error("Not booked, the time was no longer available: "
                    + String.join(", ", outcome.unavailable()) + ". You can book it again from Booking.");
            notBooked.setWrapText(true);
            prompt.getChildren().add(notBooked);
        }
        prompt.getChildren().addAll(UI.body(blurb), buttonRow);

        nav.goToPage(prompt, "Booking confirmed", true);
        return outcome;
    }

    private static final java.time.format.DateTimeFormatter CONFIRMED_DATE_FMT =
            java.time.format.DateTimeFormatter.ofPattern("EEE d MMM yyyy", java.util.Locale.UK);
    private static final java.time.format.DateTimeFormatter CONFIRMED_TIME_FMT =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm");

    public void goToReview() {
        nav.goToPage(Review.getPage(this), "Review", false);
    }

    public BookingService getBookingService() {
        return bookingService;
    }

}
