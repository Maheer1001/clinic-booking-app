package controller;

import view.ClinicMap;
import app.Data;
import dao.UserRepository;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;

import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.scene.layout.VBox;
import model.*;
import service.BookingCommunicationPrefs;
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
            ButtonType defaultC = new ButtonType("Yes, continue", ButtonBar.ButtonData.OK_DONE);

            ButtonType ignore = new ButtonType("Choose another", ButtonBar.ButtonData.CANCEL_CLOSE);
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Your clinic");
            alert.setHeaderText("Book at your usual clinic?");
            alert.setContentText(defaultCName + " is saved as your default clinic.");

            alert.getButtonTypes().setAll(defaultC, ignore);
            UI.styleDialog(alert, Data.getScene());
            DialogPane dialogPane = alert.getDialogPane();

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
                nav.goToPage(ServiceSelection.getPage(this), "Choose a service", false);
            }
        }
    }

    public List<Clinic> getClinics() throws SQLException {
        return bookingService.findAllClinics();
    }

    public Map<Clinic, Double> sortClinicsByPostcode(List<Clinic> clinics, String postcode) {
        Map<Clinic, Double> dict = new HashMap<>();
        if (postcode == null || postcode.isBlank()) {
            // Searching with an empty box used to ask the postcode API and then say "We couldn't find that postcode".
            messageLabel.setText("Enter your postcode to see the nearest clinics first.");
            return dict;
        }
        try {
            Coordinate userCoord = Postcode.getCoord(postcode);

            for (Clinic clinic : clinics) {
                // Use the clinic's stored position (the one its map pin uses) and only look the postcode up
                // when there isn't one: a web request per clinic froze the window for seconds on every search.
                Coordinate at = clinic.getCoords();
                if (at == null || !Double.isFinite(at.lat()) || !Double.isFinite(at.lon())) {
                    at = Postcode.getCoord(clinic.getPostcode());
                }
                dict.put(clinic, Postcode.haversine(userCoord, at));
            }
            clinics.sort(
                    Comparator.comparingDouble(dict::get)
            );
            clinicMap.show(clinics, userCoord);
        } catch (IllegalArgumentException e) {
            messageLabel.setText("We couldn't find that postcode. Check it and try again.");
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
        postcode.setPromptText("Your postcode, e.g. SO17 1BJ");
        postcode.setAccessibleText("Postcode");

        postcode.textProperty().addListener((observable, oldValue, newValue) -> messageLabel.setText(""));

        ListView<Clinic> list = new ListView<>();
        UI.listViewHover(list);
        list.getStyleClass().add("clinic-list");
        list.setFixedCellSize(-1);
        List<Clinic> clinics;
        try {clinics = getClinics();} catch (SQLException ex) {clinics = new ArrayList<>();}
        list.setItems(FXCollections.observableArrayList(clinics));
        // Tall enough for every clinic (collapsed) so none is hidden below the fold.
        list.setPrefHeight(Math.max(1, clinics.size()) * 66 + 12);
        list.setMinHeight(Region.USE_PREF_SIZE);
        list.setCellFactory(l -> new ListCell<>() {
            private final VBox content = new VBox(6);
            private final TitledPane pane = new TitledPane();
            private final Label name = new Label();
            private final Label meta = new Label();
            {
                name.getStyleClass().add("clinic-cell-name");
                meta.getStyleClass().add("clinic-cell-meta");
                name.setWrapText(true);
                pane.setGraphic(new VBox(1, name, meta));
                pane.setContent(content);
                pane.setExpanded(false);
                pane.setOnMouseClicked(e -> {
                    Clinic c = getItem();
                    if (c != null) {
                        list.getSelectionModel().select(c);
                    }
                });
                pane.prefWidthProperty().bind(list.widthProperty().subtract(20));
                name.maxWidthProperty().bind(list.widthProperty().subtract(70));
            }

            @Override
            protected void updateItem(Clinic item, boolean empty) {

                super.updateItem(item, empty);
                setText(null);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    name.setText(item.getName());
                    meta.setText(clinicMeta(item, postcodeMap.get(item)));
                    pane.setAccessibleText(item.getName() + ", " + meta.getText());
                    content.getChildren().clear();

                    if (item.getAddress() != null && !item.getAddress().isBlank()) {
                        content.getChildren().add(UI.detail("ADDRESS", item.getAddress()
                                + (item.getPostcode() != null && !item.getPostcode().isBlank() ? ", " + item.getPostcode() : "")));
                    }
                    if (item.getPhone() != null && !item.getPhone().isBlank()) {
                        content.getChildren().add(UI.detail("PHONE", item.getPhone()));
                    }
                    List<String> access = item.getDissAcc();
                    if (access != null && !access.isEmpty()) {
                        FlowPane chips = new FlowPane(6, 6);
                        for (String a : access) {
                            Label chip = new Label(a);
                            chip.getStyleClass().add("chip");
                            chips.getChildren().add(chip);
                        }
                        Label accessLabel = new Label("ACCESSIBILITY");
                        accessLabel.getStyleClass().add("detail-label");
                        content.getChildren().add(new VBox(4, accessLabel, chips));
                    }
                    setGraphic(pane);
                }
            }
        });

        Button search = UI.primaryButton("Search");
        search.setMaxWidth(Region.USE_PREF_SIZE);
        search.setMinWidth(Region.USE_PREF_SIZE);
        Button next = UI.primaryButton("Continue");
        next.disableProperty().bind(list.getSelectionModel().selectedItemProperty().isNull());

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
        postcode.setMaxWidth(Double.MAX_VALUE);

        clinicMap = new ClinicMap(380, 172);
        // "Choose this clinic" in a map popup picks the clinic just like the list does.
        clinicMap.setOnClinicSelected(c -> {
            list.getSelectionModel().select(c);
            list.scrollTo(c);
            next.fire();
        });
        clinicMap.show(clinics, null);

        VBox page = UI.pageTop(UI.steps(1, 5, "Clinic"), hbox, messageLabel, clinicMap.node(), list);
        page.setSpacing(12);
        if (clinics.isEmpty()) {
            page.getChildren().add(UI.emptyState(UI.ICON_SEARCH, "No clinics yet",
                    "Add clinics to the database to start booking (see docs/sample-data.sql)."));
        }
        page.getChildren().add(next);
        ScrollPane sp = new ScrollPane(page);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return sp;
    }

    /** "1.3 km away · SO17 2FW" after a search, otherwise just the postcode. */
    static String clinicMeta(Clinic clinic, Double km) {
        String pc = clinic.getPostcode() == null ? "" : clinic.getPostcode().strip();
        if (km == null) return pc;
        String distance = String.format(java.util.Locale.UK, "%.1f km away", km);
        return pc.isEmpty() ? distance : distance + "  ·  " + pc;
    }

    public void selectClinic(Clinic clinic) throws SQLException {
        int userID = Data.getCurrentUserId();
        if (!userRepository.defaultClinicExists(userID)) {

            ButtonType update = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
            ButtonType cancel = new ButtonType("Not now", ButtonBar.ButtonData.CANCEL_CLOSE);
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Default clinic");
            alert.setHeaderText("Make this your default clinic?");
            alert.setContentText(clinic.getName() + " will be offered first next time you book. You can change it at any time.");

            alert.getButtonTypes().setAll(update, cancel);
            UI.styleDialog(alert, Data.getScene());
            DialogPane dialogPane = alert.getDialogPane();

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
                ButtonType update = new ButtonType("Change", ButtonBar.ButtonData.OK_DONE);
                ButtonType cancel = new ButtonType("Keep current", ButtonBar.ButtonData.CANCEL_CLOSE);
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Default clinic");
                alert.setHeaderText("Change your default clinic?");
                Clinic current = bookingService.findClinicById(userRepository.getDefaultClinic(userID));
                alert.setContentText("Use " + clinic.getName() + " as your default clinic"
                        + (current != null ? " instead of " + current.getName() : "") + "?");

                alert.getButtonTypes().setAll(update, cancel);
                UI.styleDialog(alert, Data.getScene());
                DialogPane dialogPane = alert.getDialogPane();

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
        nav.goToPage(ServiceSelection.getPage(this), "Choose a service", false);
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
        nav.goToPage(DateTime.getPage(this), "Pick a time", false);
    }

    /**
     * Navigates back to the service selection page for the current clinic.
     */
    public void goToServiceSelection() {
        nav.goToPage(ServiceSelection.getPage(this), "Choose a service", false);
    }

    public void goToDetails() {
        nav.goToPage(PersonalInfo.getPage(this), "Your details", false);
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
        String recipientName = recipientName(b, Data.getCurrentUserName());
        Clinic clinic = b.getClinic();
        String clinicName = clinic != null ? clinic.getName() : "";
        boolean hasEmail = recipientEmail != null && !recipientEmail.isBlank();

        // Filled in once the confirmation emails have actually gone (or failed), instead of claiming
        // they were sent before trying.
        Label sub = UI.intro(hasEmail
                ? "Sending a confirmation to " + recipientEmail + "…"
                : "Your booking is saved. Add an email address to get confirmations.");
        int[] emailsLeft = {0};
        boolean[] anyFailed = {false};
        for (BookingService.BookedAppointment appt : outcome.booked()) {
            if (appt.date() == null || appt.time() == null) continue;
            String bookingRef = "APP-" + appt.appId(); // same reference the cancel and reschedule emails use
            LocalDateTime appointmentDateTime = LocalDateTime.of(appt.date(), appt.time());
            String serviceName = appt.service() != null ? appt.service().getName() : "";
            if (hasEmail) emailsLeft[0]++;
            notificationDispatcher.notifyUserAsync(
                    userId,
                    hasEmail ? new BookingConfirmationEmail(
                            recipientEmail, recipientName, bookingRef, serviceName,
                            appointmentDateTime, clinicName, "Clinician", confirmationDiscreet,
                            confirmationTpl) : null,
                    new BookingConfirmationNotification(
                            recipientName, bookingRef, serviceName, appointmentDateTime, clinicName, "Clinician",
                            confirmationDiscreet,
                            confirmationTpl
                    ),
                    hasEmail ? sent -> {
                        if (!sent) anyFailed[0] = true;
                        if (--emailsLeft[0] == 0) {
                            sub.setText(anyFailed[0]
                                    ? "Your booking is saved, but we couldn't send the confirmation email to "
                                      + recipientEmail + ". Check your email settings and try again later."
                                    : "We've emailed a confirmation to " + recipientEmail
                                      + ". If it isn't in your inbox, check your spam folder.");
                        }
                    } : null
            );
        }
        Data.reset();

        // Every new booking gets a reminder straight away (the user's saved default, or one day before).
        // Before, nothing was set unless the user went to Reminders and switched each one on.
        Label blurbLabel = UI.intro("Setting up your reminder…");
        List<Integer> savedIds = List.copyOf(newAppIds);
        String reminderEmail = hasEmail ? recipientEmail : "";
        String reminderName = recipientName;
        Background.run(
                () -> new ReminderController(userId).applyReminderForNewBookings(savedIds, reminderEmail, reminderName),
                on -> blurbLabel.setText(on
                        ? (hasEmail ? (savedIds.size() > 1
                                        ? "We'll email you a reminder before each appointment. You can change or turn them off in Reminders."
                                        : "We'll email you a reminder before your appointment. You can change or turn it off in Reminders.")
                                    : "Reminders are on, but you need an email address on your account to receive them.")
                        : "Reminders are off (your saved default). You can turn them on in Reminders."),
                ex -> blurbLabel.setText("We couldn't set up a reminder automatically. You can set one in Reminders."));

        Button setupRemindersBtn = UI.secondaryButton("Change reminder");
        Button skipBtn = UI.primaryButton("Done");

        setupRemindersBtn.setOnAction(e -> {nav.goToPageNewTab("reminders", new RemindersView(userId).getRemindersScene("appointments"), "Reminders", true); nav.clearTabStack("booking");});
        skipBtn.setOnAction(e -> nav.goToHome());

        setupRemindersBtn.setMaxWidth(Double.MAX_VALUE);
        skipBtn.setMaxWidth(Double.MAX_VALUE);
        VBox buttonRow = new VBox(8, skipBtn, setupRemindersBtn);

        VBox summary = UI.card(10);
        for (BookingService.BookedAppointment appt : outcome.booked()) {
            String service = appt.service() != null ? appt.service().getName() : "Appointment";
            String when = appt.date() != null && appt.time() != null
                    ? view.ui.Dates.dateTime(appt.date(), appt.time(), appt.service() != null && appt.service().getDurationMinutes() > 0
                            ? appt.time().plusMinutes(appt.service().getDurationMinutes()) : null)
                    : "";
            VBox row = new VBox(4, UI.detailTitle(service));
            if (!when.isEmpty()) row.getChildren().add(UI.detail("WHEN", when));
            if (!clinicName.isBlank()) row.getChildren().add(UI.detail("CLINIC", clinicName));
            row.getChildren().add(UI.detail("REFERENCE", "APP-" + appt.appId()));
            summary.getChildren().add(row);
        }

        StackPane badge = new StackPane(UI.glyph(UI.ICON_CHECK, 30, "success-glyph"));
        badge.getStyleClass().add("success-badge");
        Label title = new Label("You're booked in");
        title.getStyleClass().add("success-title");
        sub.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        sub.setAlignment(Pos.CENTER);
        VBox hero = new VBox(8, badge, title, sub);
        hero.setAlignment(Pos.CENTER);

        javafx.scene.layout.VBox prompt = UI.pageTop(hero, summary);
        if (!outcome.unavailable().isEmpty()) {
            Label notBooked = UI.error("Not booked, the time was no longer available: "
                    + String.join(", ", outcome.unavailable()) + ". You can book it again from Booking.");
            notBooked.setWrapText(true);
            prompt.getChildren().add(notBooked);
        }
        blurbLabel.setWrapText(true);
        sub.setWrapText(true);
        prompt.getChildren().addAll(blurbLabel, buttonRow);

        // Scrolls like the other booking steps. Without it, booking two services overflowed the window and the
        // email result and reminder lines were cut to "We've emailed a confirmation to ...".
        ScrollPane page = new ScrollPane(prompt);
        page.setFitToWidth(true);
        page.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        nav.goToPage(page, "Booking confirmed", true);
        return outcome;
    }

    /**
     * Who the confirmation is addressed to: the name typed on the details step, else the account's name.
     * The account name was used first, and for an account without a name that is its email address,
     * so the email opened "Dear someone@example.com" even though the patient had just typed their name.
     */
    static String recipientName(Book b, String accountName) {
        String typed = ((b.getFirstName() == null ? "" : b.getFirstName().strip()) + " "
                + (b.getLastName() == null ? "" : b.getLastName().strip())).strip();
        if (!typed.isEmpty()) return typed;
        if (accountName != null && !accountName.isBlank()) return accountName.strip();
        return "Patient";
    }


    public void goToReview() {
        nav.goToPage(Review.getPage(this), "Review booking", false);
    }

    public BookingService getBookingService() {
        return bookingService;
    }

}
