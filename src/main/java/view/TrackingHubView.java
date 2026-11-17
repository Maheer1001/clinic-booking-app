package view;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import view.ui.UI;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracking home: tabbed Appointments, Test Results, and Prescriptions on one page (same pattern as {@link view.reminders.RemindersView}).
 */
public final class TrackingHubView {

    private TrackingHubView() {
    }

    public static ScrollPane wrapTabBody(Node inner) {
        ScrollPane sp = new ScrollPane(inner);
        sp.setFitToWidth(true);
        return sp;
    }

    public static VBox createHub(Node appointmentsBody,
                                 Node testResultsBody,
                                 Node prescriptionsBody) {
        ToggleGroup group = new ToggleGroup();
        ToggleButton tabAppointments = new ToggleButton("Appointments");
        ToggleButton tabTests = new ToggleButton("Test Results");
        ToggleButton tabPrescriptions = new ToggleButton("Prescriptions");
        tabAppointments.setToggleGroup(group);
        tabTests.setToggleGroup(group);
        tabPrescriptions.setToggleGroup(group);
        tabAppointments.getStyleClass().add(UI.REMINDER_TAB);
        tabTests.getStyleClass().add(UI.REMINDER_TAB);
        tabPrescriptions.getStyleClass().add(UI.REMINDER_TAB);

        Node apPane = wrapTabBody(appointmentsBody);
        Node testPane = wrapTabBody(testResultsBody);
        Node rxPane = wrapTabBody(prescriptionsBody);

        StackPane stack = new StackPane(apPane, testPane, rxPane);
        for (Node layer : stack.getChildren()) {
            layer.setVisible(false);
            StackPane.setAlignment(layer, Pos.TOP_CENTER);
            if (layer instanceof Region r) {
                r.setMaxWidth(Double.MAX_VALUE);
            }
        }

        Runnable syncVisibility = () -> {
            var selToggle = group.getSelectedToggle();
            ToggleButton sel = selToggle instanceof ToggleButton tb ? tb : tabAppointments;
            apPane.setVisible(sel == tabAppointments);
            testPane.setVisible(sel == tabTests);
            rxPane.setVisible(sel == tabPrescriptions);
        };

        AtomicBoolean suppressSubTabListeners = new AtomicBoolean(false);
        group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (!suppressSubTabListeners.get() && newToggle == null) {
                suppressSubTabListeners.set(true);
                try {
                    group.selectToggle(oldToggle != null ? oldToggle : tabAppointments);
                } finally {
                    suppressSubTabListeners.set(false);
                }
            }
            syncVisibility.run();
        });

        HBox tabRow = new HBox(8, tabAppointments, tabTests, tabPrescriptions);
        tabRow.setAlignment(Pos.CENTER);

        VBox root = new VBox(12);
        root.getStyleClass().add(UI.PAGE);
        root.getChildren().addAll(tabRow, stack);
        VBox.setVgrow(stack, Priority.ALWAYS);

        tabAppointments.setSelected(true);
        syncVisibility.run();

        return root;
    }
}
