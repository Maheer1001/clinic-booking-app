package view.staff;

import controller.StaffController;
import controller.StaffForms;
import dao.ServiceDAO;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import model.Service;
import model.StaffMember;
import model.StaffRole;
import model.StaffTimeslot;
import service.StaffResult;
import service.StaffService;
import service.StaffService.StaffCodeStatus;
import view.Navigator;
import view.ui.Dates;
import view.ui.UI;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Clinic tab (clinic admins): approve new staff, manage the staff list and roles, the clinic's staff
 * code, and timeslots for a day. At a clinic where the user is a clinician they see a short note
 * instead; the service refuses them anyway.
 */
public class StaffClinicView {

    private final Navigator nav;
    private final StaffController staff;
    private final VBox pending = new VBox(10);
    private final Label pendingMsg = StaffUi.message();
    private final VBox members = new VBox(10);
    private final Label membersMsg = StaffUi.message();
    private final VBox code = new VBox(10);
    private final VBox slots = new VBox(10);
    private final Label slotsMsg = StaffUi.message();
    private final DatePicker slotDate = new DatePicker(LocalDate.now());
    private final VBox newSlot = new VBox(10);

    public StaffClinicView(Navigator nav, StaffController staff) {
        this.nav = nav;
        this.staff = staff;
    }

    /**
     * Roles are per clinic, so the page follows the clinic switcher: switching to a clinic where the
     * user is only a clinician swaps the admin tools for the note (and back), and switching between
     * two clinics where they are admin reloads the tools for the new clinic.
     */
    public Node page() {
        if (staff.clinicId() == 0) return StaffUi.scroll(StaffUi.noClinic());
        ScrollPane page = StaffUi.scroll(build());
        StaffUi.onClinicChange(page, () -> {
            boolean admin = staff.isAdmin();
            if (admin != shownAdmin) page.setContent(build());
            else if (admin) loadAll();
        });
        return page;
    }

    /** Whether the page on show is the admin tools (true) or the clinician note (false). */
    private boolean shownAdmin;
    private boolean slotDateReady;

    private Node build() {
        shownAdmin = staff.isAdmin();
        if (!shownAdmin) {
            return UI.pageTop(StaffUi.clinicSwitcher(staff), UI.emptyState(UI.ICON_BELL,
                    "For clinic admins",
                    "Only a clinic admin can manage staff, the staff code and timeslots here. Ask your admin if something needs changing."));
        }
        if (!slotDateReady) {
            slotDateReady = true;
            StaffUi.datePicker(slotDate);
            slotDate.setAccessibleText("Timeslot date");
            slotDate.setMaxWidth(Double.MAX_VALUE);
            slotDate.valueProperty().addListener((o, was, now) -> {
                if (now == null) slotDate.setValue(was == null ? LocalDate.now() : was);
                else loadSlots();
            });
        }

        VBox content = UI.pageTop(StaffUi.clinicSwitcher(staff),
                StaffUi.section("Waiting for approval",
                        UI.muted("People who signed up with this clinic's staff code. They can't see anything until you approve them."),
                        pending, pendingMsg),
                StaffUi.section("Staff", members, membersMsg),
                StaffUi.section("Staff code", code),
                StaffUi.section("Timeslots", slotDate, slots, slotsMsg, newSlot));
        loadAll();
        return content;
    }

    private void loadAll() {
        for (Label l : List.of(pendingMsg, membersMsg, slotsMsg)) l.setText("");
        loadPending();
        loadMembers();
        loadCode(null);
        loadSlots();
        loadNewSlotForm();
    }

    // ------------------------------------------------------------ approvals

    private void loadPending() {
        pending.getChildren().setAll(StaffUi.loading());
        staff.<List<StaffMember>>call((s, u, c) -> s.pendingStaff(u, c), this::showPending);
    }

    private void showPending(StaffResult<List<StaffMember>> r) {
        if (!r.ok()) {
            pending.getChildren().setAll(StaffUi.loadError(r.message(), this::loadPending));
            return;
        }
        pending.getChildren().clear();
        if (r.value().isEmpty()) {
            pending.getChildren().add(UI.body("No one is waiting for approval."));
            return;
        }
        for (StaffMember m : r.value()) {
            Button approve = UI.positiveButton("Approve");
            Button reject = UI.dangerButton("Reject");
            approve.setOnAction(e -> staff.<List<StaffMember>>call((s, u, c) -> s.approveStaff(u, c, m.userId()), res -> {
                pendingResult(res);
                if (res.ok()) loadMembers();
            }));
            reject.setOnAction(e -> {
                if (!StaffUi.confirm(pending, "Reject this request?",
                        m.email() + " won't get access to this clinic. Their account stays, but it can't use the staff area.",
                        "Reject")) return;
                staff.<List<StaffMember>>call((s, u, c) -> s.rejectStaff(u, c, m.userId()), this::pendingResult);
            });
            pending.getChildren().add(personRow(m, null, approve, reject));
        }
    }

    private void pendingResult(StaffResult<List<StaffMember>> r) {
        if (r.ok()) {
            StaffUi.showOk(pendingMsg, StaffForms.savedText(r.message()));
            showPending(r);
        } else {
            StaffUi.showError(pendingMsg, r.message());
        }
    }

    // ------------------------------------------------------------ staff list

    private void loadMembers() {
        members.getChildren().setAll(StaffUi.loading());
        staff.<List<StaffMember>>call((s, u, c) -> s.listStaff(u, c), r -> {
            if (!r.ok()) {
                members.getChildren().setAll(StaffUi.loadError(r.message(), this::loadMembers));
                return;
            }
            showMembers(r.value());
        });
    }

    private void showMembers(List<StaffMember> list) {
        members.getChildren().clear();
        for (StaffMember m : list) {
            boolean admin = m.role() == StaffRole.CLINIC_ADMIN;
            Button role = UI.backButton(admin ? "Make clinician" : "Make admin");
            Button remove = UI.dangerButton("Remove");
            role.setOnAction(e -> staff.<List<StaffMember>>call((s, u, c) ->
                    admin ? s.demoteStaff(u, c, m.userId()) : s.promoteStaff(u, c, m.userId()), this::membersResult));
            remove.setOnAction(e -> {
                if (!StaffUi.confirm(members, "Remove from this clinic?",
                        m.email() + " will lose access to this clinic straight away. Their account stays.",
                        "Remove")) return;
                staff.<List<StaffMember>>call((s, u, c) -> s.removeStaff(u, c, m.userId()), this::membersResult);
            });
            boolean me = m.userId() == staff.staffUserId();
            members.getChildren().add(personRow(m, StaffForms.rolePill(m.role()), role, remove));
            if (me) role.setAccessibleText(role.getText() + " (you)");
        }
    }

    private void membersResult(StaffResult<List<StaffMember>> r) {
        if (r.ok()) {
            StaffUi.showOk(membersMsg, StaffForms.savedText(r.message()));
            showMembers(r.value());
        } else {
            // e.g. "A clinic needs at least one admin. Make someone else an admin first."
            StaffUi.showError(membersMsg, r.message());
        }
    }

    /**
     * Name and email on full-width lines, then two small buttons with the optional role pill on the
     * right. (With the pill beside the name, a long email was cut mid-word at 420px.)
     */
    private Node personRow(StaffMember m, StaffForms.Pill pill, Button a, Button b) {
        boolean me = m.userId() == staff.staffUserId();
        // Accounts without a name show their email as the name: don't repeat it underneath.
        boolean named = m.displayName() != null && !m.displayName().equalsIgnoreCase(m.email());
        Label name = UI.wrappingLabel((named ? m.displayName() : m.email()) + (me ? " (you)" : ""));
        name.getStyleClass().add(UI.HEADING);
        name.setMaxWidth(Double.MAX_VALUE);
        HBox buttons = new HBox(8, a, b);
        if (pill != null) {
            Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            buttons.getChildren().addAll(gap, StaffUi.pill(pill));
        }
        buttons.setAlignment(Pos.CENTER_LEFT);
        VBox row = UI.innerCard(6, name);
        if (named) {
            Label email = UI.muted(m.email());
            email.setWrapText(true);
            row.getChildren().add(email);
        }
        row.getChildren().add(buttons);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    // ------------------------------------------------------------ staff code

    private void loadCode(String newCode) {
        code.getChildren().setAll(StaffUi.loading());
        staff.<StaffCodeStatus>call((s, u, c) -> s.staffCodeStatus(u, c), r -> {
            if (!r.ok()) {
                code.getChildren().setAll(StaffUi.loadError(r.message(), () -> loadCode(null)));
                return;
            }
            code.getChildren().clear();
            StaffCodeStatus st = r.value();
            if (newCode != null) code.getChildren().add(newCodeBox(newCode));
            if (r.message() != null) {
                Label nag = UI.wrappingLabel(r.message());
                nag.getStyleClass().add(UI.ERROR);
                code.getChildren().add(nag);
            } else if (newCode == null) {
                code.getChildren().add(UI.body("Staff use this code to sign up for this clinic. You approve each one."));
            }
            Button rotate = UI.secondaryButton(st.hasCode() ? "Rotate staff code" : "Create staff code");
            rotate.setMaxWidth(Double.MAX_VALUE);
            rotate.setOnAction(e -> {
                if (st.hasCode() && !StaffUi.confirm(code, "Rotate the staff code?",
                        "The old code stops working straight away. Staff who already have access keep it.",
                        "Rotate code")) return;
                rotate.setDisable(true);
                staff.<String>call((s, u, c) -> s.rotateStaffCode(u, c), res -> {
                    rotate.setDisable(false);
                    if (!res.ok()) {
                        Label err = UI.error(res.message());
                        err.setWrapText(true);
                        code.getChildren().add(err);
                        return;
                    }
                    loadCode(res.value());
                });
            });
            code.getChildren().add(rotate);
        });
    }

    /** The new code, shown this once with a Copy button. */
    private Node newCodeBox(String newCode) {
        TextField field = new TextField(newCode);
        field.setEditable(false);
        field.getStyleClass().add("staff-code");
        field.setAccessibleText("New staff code");
        field.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(field, Priority.ALWAYS);
        Label copied = StaffUi.message();
        Button copy = UI.backButton("Copy");
        copy.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        copy.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(newCode);
            Clipboard.getSystemClipboard().setContent(cc);
            StaffUi.showOk(copied, "Copied.");
        });
        HBox row = new HBox(8, field, copy);
        row.setAlignment(Pos.CENTER_LEFT);
        Label note = UI.wrappingLabel("New staff code. Write it down or copy it now: it won't be shown again. "
                + "Share it only with staff at this clinic.");
        note.getStyleClass().add(UI.INLINE_SAVE_CONFIRM);
        return new VBox(8, note, row, copied);
    }

    // ------------------------------------------------------------ timeslots

    private void loadSlots() {
        LocalDate day = slotDate.getValue();
        slots.getChildren().setAll(StaffUi.loading());
        staff.<List<StaffTimeslot>>call((s, u, c) -> s.timeslots(u, c, day), r -> {
            if (!r.ok()) {
                slots.getChildren().setAll(StaffUi.loadError(r.message(), this::loadSlots));
                return;
            }
            slots.getChildren().clear();
            if (r.value().isEmpty()) {
                slots.getChildren().add(UI.body("No timeslots on " + Dates.full(day) + "."));
            }
            for (StaffTimeslot t : r.value()) slots.getChildren().add(slotRow(t));
        });
    }

    private Node slotRow(StaffTimeslot t) {
        Label time = UI.heading(Dates.timeRange(t.startTime(), t.endTime()));
        Label service = UI.muted(t.serviceName() + " · " + StaffForms.capacityText(t.bookedCount(), t.capacity()));
        service.setWrapText(true);
        Button edit = UI.backButton("Edit");
        VBox row = UI.innerCard(6, time, service, edit);
        row.setMaxWidth(Double.MAX_VALUE);
        edit.setOnAction(e -> row.getChildren().setAll(slotEditor(t)));
        return row;
    }

    /** Edit a slot in place: times (only when nothing is booked) and capacity. */
    private Node slotEditor(StaffTimeslot t) {
        TextField start = UI.textField(Dates.time(t.startTime()));
        TextField end = UI.textField(Dates.time(t.endTime()));
        start.setAccessibleText("Start time");
        end.setAccessibleText("End time");
        boolean booked = t.bookedCount() > 0;
        start.setDisable(booked);
        end.setDisable(booked);
        Spinner<Integer> capacity = capacitySpinner(Math.max(1, t.capacity()));
        Label msg = StaffUi.message();
        Button save = UI.primaryButton("Save timeslot");
        Button cancel = UI.secondaryButton("Cancel");
        save.setOnAction(e -> {
            LocalTime s = StaffForms.parseTime(start.getText());
            LocalTime en = StaffForms.parseTime(end.getText());
            if (s == null || en == null) {
                StaffUi.showError(msg, "Enter times like 09:30.");
                return;
            }
            int cap = capacity.getValue();
            staff.<StaffTimeslot>call((sv, u, c) -> sv.updateTimeslot(u, c, t.timeslotId(), t.date(), s, en, cap), r -> {
                if (!r.ok()) {
                    StaffUi.showError(msg, r.message());
                    return;
                }
                loadSlots();
                StaffUi.showOk(slotsMsg, StaffForms.savedText(r.message()));
            });
        });
        cancel.setOnAction(e -> loadSlots());
        VBox box = new VBox(8, UI.heading(t.serviceName()),
                times(start, end),
                booked ? UI.muted(StaffForms.capacityText(t.bookedCount(), t.capacity())
                        + ", so only the capacity can change.") : UI.muted("Times use the 24-hour clock, e.g. 14:30."),
                labelled("CAPACITY", capacity), StaffUi.pair(save, cancel), msg);
        return box;
    }

    private void loadNewSlotForm() {
        newSlot.getChildren().clear();
        int clinicId = staff.clinicId();
        staff.load(() -> new ServiceDAO().findByClinicId(clinicId), r -> {
            newSlot.getChildren().clear();
            if (!r.ok() || r.value().isEmpty()) return;
            newSlot.getChildren().add(newSlotForm(r.value()));
        });
    }

    private Node newSlotForm(List<Service> services) {
        ComboBox<Service> service = new ComboBox<>();
        service.getItems().setAll(services);
        service.setCellFactory(l -> serviceCell());
        service.setButtonCell(serviceCell());
        service.setValue(services.get(0));
        service.setMaxWidth(Double.MAX_VALUE);
        service.setAccessibleText("Service");
        TextField start = UI.textField("09:00");
        TextField end = UI.textField("09:30");
        start.setAccessibleText("Start time");
        end.setAccessibleText("End time");
        Spinner<Integer> capacity = capacitySpinner(1);
        Label msg = StaffUi.message();
        Button add = UI.primaryButton("Add timeslot");
        add.setMaxWidth(Double.MAX_VALUE);
        add.setOnAction(e -> {
            LocalTime s = StaffForms.parseTime(start.getText());
            LocalTime en = StaffForms.parseTime(end.getText());
            if (s == null || en == null) {
                StaffUi.showError(msg, "Enter times like 09:30.");
                return;
            }
            if (service.getValue() == null) {
                StaffUi.showError(msg, "Choose a service.");
                return;
            }
            int serviceId = service.getValue().getId();
            LocalDate day = slotDate.getValue();
            int cap = capacity.getValue();
            staff.<StaffTimeslot>call((sv, u, c) -> sv.createTimeslot(u, c, serviceId, day, s, en, cap), r -> {
                if (!r.ok()) {
                    StaffUi.showError(msg, r.message());
                    return;
                }
                StaffUi.showOk(msg, StaffForms.savedText(r.message()));
                loadSlots();
            });
        });
        Label heading = UI.heading("Add a timeslot on this date");
        return new VBox(8, heading, labelled("SERVICE", service), times(start, end),
                labelled("CAPACITY", capacity), add, msg);
    }

    private static ListCell<Service> serviceCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Service s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : s.getName());
                setWrapText(true);
            }
        };
    }

    private static Spinner<Integer> capacitySpinner(int value) {
        Spinner<Integer> sp = new Spinner<>(1, StaffService.MAX_CAPACITY, Math.min(value, StaffService.MAX_CAPACITY));
        sp.setEditable(true);
        UI.tuneIntSpinner(sp);
        sp.setAccessibleText("Capacity");
        return sp;
    }

    private static HBox times(TextField start, TextField end) {
        VBox a = labelled("START", start);
        VBox b = labelled("END", end);
        for (VBox v : List.of(a, b)) {
            HBox.setHgrow(v, Priority.ALWAYS);
            v.setMaxWidth(Double.MAX_VALUE);
            v.setPrefWidth(1);
        }
        return new HBox(12, a, b);
    }

    private static VBox labelled(String text, Node field) {
        Label l = new Label(text);
        l.getStyleClass().add("detail-label");
        return new VBox(4, l, field);
    }
}
