package view.staff;

import controller.StaffController;
import controller.StaffForms;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import model.StaffAppointment;
import view.Navigator;
import view.ui.Dates;
import view.ui.UI;

import java.time.LocalDate;
import java.util.List;

/**
 * Today tab: the clinic's appointments for a day. Cards show only the time, service, patient name and
 * status; personal and sexual-health details are on the appointment's own page.
 */
public class StaffDayView {

    private final Navigator nav;
    private final StaffController staff;
    private final DatePicker picker = new DatePicker(LocalDate.now());
    private final Label dayHeading = UI.pageHeading("");
    private final Label summary = UI.intro("");
    private final VBox list = new VBox(10);
    private int loadToken;

    public StaffDayView(Navigator nav, StaffController staff) {
        this.nav = nav;
        this.staff = staff;
    }

    public Node page() {
        if (staff.clinicId() == 0) return StaffUi.scroll(StaffUi.noClinic());

        UI.datePicker(picker);
        picker.setAccessibleText("Date");
        picker.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(picker, Priority.ALWAYS);
        Button prev = UI.backButton("‹");
        prev.setAccessibleText("Previous day");
        prev.setTooltip(new Tooltip("Previous day"));
        Button next = UI.backButton("›");
        next.setAccessibleText("Next day");
        next.setTooltip(new Tooltip("Next day"));
        Button today = UI.backButton("Today");
        prev.setOnAction(e -> picker.setValue(date().minusDays(1)));
        next.setOnAction(e -> picker.setValue(date().plusDays(1)));
        today.setOnAction(e -> picker.setValue(LocalDate.now()));
        HBox dateBar = new HBox(8, prev, picker, next, today);
        dateBar.getStyleClass().add("staff-date-bar");
        dateBar.setAlignment(Pos.CENTER_LEFT);
        picker.valueProperty().addListener((o, was, now) -> {
            if (now == null) picker.setValue(was == null ? LocalDate.now() : was);
            else reload();
        });

        VBox content = UI.pageTop(StaffUi.clinicSwitcher(staff), dateBar, new VBox(2, dayHeading, summary), list);
        content.setFillWidth(true);
        StaffUi.onClinicChange(content, this::reload);
        reload();
        return StaffUi.scroll(content);
    }

    private LocalDate date() {
        return picker.getValue() == null ? LocalDate.now() : picker.getValue();
    }

    /** Reloads the list for the chosen date (also after coming back from an appointment). */
    public void reload() {
        LocalDate day = date();
        int token = ++loadToken;
        dayHeading.setText(Dates.upcoming(day, LocalDate.now()).equals("Today")
                ? "Today, " + Dates.full(day) : Dates.full(day));
        summary.setText("Loading appointments…");
        list.getChildren().setAll(StaffUi.loading());
        staff.<List<StaffAppointment>>call((s, u, c) -> s.dayList(u, c, day), r -> {
            if (token != loadToken) return;  // a newer date was chosen meanwhile
            if (!r.ok()) {
                summary.setText("");
                list.getChildren().setAll(StaffUi.loadError(r.message(), this::reload));
                return;
            }
            show(r.value());
        });
    }

    private void show(List<StaffAppointment> appointments) {
        list.getChildren().clear();
        long active = appointments.stream()
                .filter(a -> a.status() != model.AppointmentStatus.CANCELLED).count();
        summary.setText(active == 0 ? "No appointments booked."
                : active + (active == 1 ? " appointment" : " appointments")
                  + (active < appointments.size() ? " (plus " + (appointments.size() - active) + " cancelled)" : ""));
        if (appointments.isEmpty()) {
            list.getChildren().add(UI.emptyState(UI.ICON_CALENDAR, "Nothing booked",
                    "No one has an appointment at this clinic on this day."));
            return;
        }
        for (StaffAppointment a : appointments) {
            list.getChildren().add(card(a));
        }
    }

    /** Time, service, patient name and status only. */
    private Node card(StaffAppointment a) {
        String time = Dates.timeRange(a.booking().startTime(), a.booking().endTime());
        String service = a.booking().serviceName() == null ? "Appointment" : a.booking().serviceName();
        return StaffUi.listCard(a.patientName(), time + " · " + service, StaffForms.appointmentPill(a.status()),
                () -> openAppointment(a.appId()));
    }

    private void openAppointment(int appId) {
        StaffAppointmentView view = new StaffAppointmentView(nav, staff, appId, this::reload);
        nav.goToPage(view.page(), "Appointment", false);
    }
}
