package view;

import app.Data;
import controller.BookingController;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import model.Clinic;
import model.Item;
import model.Service;
import view.ui.UI;

import java.util.List;

public class ServiceSelection {

    public static Node getPage(BookingController controller) {

        Clinic clinic = Data.getBooking().getClinic();

        if (clinic == null) {
            return UI.page(UI.muted("Please select a clinic first."));
        }

        List<Service> services = clinic.getServices();

        Label clinicName = UI.heading("Available services for " + clinic.getName());
        Label emptyMessage = services.isEmpty()
                ? UI.muted("No services available for this clinic.")
                : new Label();

        ListView<Service> list = new ListView<>();
        list.setItems(FXCollections.observableArrayList(services));
        list.setCellFactory(CheckBoxListCell.forListView(Service::selectedProperty));

        list.setCellFactory(l -> new ListCell<>() {
            private final CheckBox checkBox = new CheckBox();
            private final VBox vbox = new VBox();
            private final VBox content = new VBox(10);
            private final TitledPane pane = new TitledPane();
            private Service current;

            {
                vbox.getChildren().add(checkBox);
                pane.setGraphic(vbox);
                pane.setContent(content);
                pane.setExpanded(false);
            }

            @Override
            protected void updateItem(Service service, boolean empty) {
                super.updateItem(service, empty);
                if (current != null) {
                    checkBox.selectedProperty().unbindBidirectional(current.selectedProperty());
                }

                if (empty || service == null) {
                    setGraphic(null);
                } else {
                    current = service;
                    checkBox.setText(service.getName());
                    checkBox.selectedProperty().bindBidirectional(service.selectedProperty());
                    checkBox.setWrapText(true);
                    checkBox.setMaxWidth(250);
                    checkBox.setPrefHeight(Region.USE_COMPUTED_SIZE);
                    Label description = new Label(service.getDescription());
                    description.setWrapText(true);
                    Label duration = new Label("Duration: " + service.getDurationMinutes() + " minutes");
                    duration.setWrapText(true);
                    content.getChildren().addAll(description, duration);
                    setGraphic(pane);
                    pane.prefWidthProperty().bind(list.widthProperty().subtract(200));
                }
            }
        });

        Label description = new Label("Select services");
        description.setWrapText(true);

        list.setSelectionModel(null);
        list.setFocusTraversable(false);

        Button next = UI.primaryButton("Confirm Services");
        next.setDisable(true);

        list.getItems().forEach(service ->
                service.selectedProperty().addListener((obs, oldVal, newVal) -> {
                    boolean anySelected = list.getItems().stream().anyMatch(Service::isSelected);
                    next.setDisable(!anySelected);
                })
        );

        next.setOnAction(e -> {
            List<Item> items = list.getItems().stream()
                    .filter(Service::isSelected)
                    .map(Item::new)
                    .toList();

            if (items.isEmpty()) return;

            Data.getBooking().setItems(items);
            // Use Navigator via controller instead of creating our own Scene.
            controller.goToTimeslotsForItems();
        });

        return UI.pageTop(clinicName, list, emptyMessage, next);
    }
}