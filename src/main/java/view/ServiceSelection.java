package view;

import app.Data;
import controller.BookingController;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.*;
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

        List<Service> services = clinic.getServices() != null ? clinic.getServices() : List.of();

        Label clinicName = UI.pageHeading("What would you like to book?");
        Label hint = UI.intro("At " + clinic.getName() + ". Tick one or more services; open one to see what it involves.");

        if (services.isEmpty()) {
            return UI.pageTop(UI.steps(2, 5, "Service"),
                    UI.emptyState(UI.ICON_FLASK, "Nothing to book here online",
                            "This clinic has no services available to book online. Please choose another clinic."));
        }

        ListView<Service> list = new ListView<>();
        list.setItems(FXCollections.observableArrayList(services));

        list.setCellFactory(l -> new ListCell<>() {
            private final CheckBox checkBox = new CheckBox();
            private final VBox content = new VBox(10);
            private final TitledPane pane = new TitledPane();
            private Service current;

            {
                pane.setGraphic(checkBox);
                pane.setContent(content);
                pane.setExpanded(false);
                checkBox.setWrapText(true);
                checkBox.setMaxWidth(300);
                checkBox.setPrefHeight(Region.USE_COMPUTED_SIZE);
                pane.prefWidthProperty().bind(list.widthProperty().subtract(30));
            }

            @Override
            protected void updateItem(Service service, boolean empty) {
                super.updateItem(service, empty);
                if (current != null) {
                    checkBox.selectedProperty().unbindBidirectional(current.selectedProperty());
                    current = null;
                }

                setText(null);
                if (empty || service == null) {
                    setGraphic(null);
                } else {
                    current = service;
                    checkBox.setText(service.getName());
                    checkBox.selectedProperty().bindBidirectional(service.selectedProperty());
                    Label description = UI.body(service.getDescription() != null ? service.getDescription() : "");
                    Label duration = UI.muted("About " + service.getDurationMinutes() + " minutes");
                    // setAll, not addAll: cells are reused, so adding would pile up old labels.
                    content.getChildren().setAll(description, duration);
                    setGraphic(pane);
                }
            }
        });

        list.setSelectionModel(null);
        list.setPrefHeight(services.size() * 56 + 16);
        list.setMinHeight(Region.USE_PREF_SIZE);
        list.setFocusTraversable(false);

        Button next = UI.primaryButton("Continue");

        // Derived from every service's ticked state, so it is right on arrival too
        // (ticks are kept when the user comes back to this page).
        BooleanBinding anySelected = Bindings.createBooleanBinding(
                () -> services.stream().anyMatch(Service::isSelected),
                services.stream().map(Service::selectedProperty).toArray(Observable[]::new));
        next.disableProperty().bind(anySelected.not());

        next.setOnAction(e -> {
            List<Item> items = services.stream()
                    .filter(Service::isSelected)
                    .map(Item::new)
                    .toList();

            if (items.isEmpty()) return;

            Data.getBooking().setItems(items);
            // Use Navigator via controller instead of creating our own Scene.
            controller.goToTimeslotsForItems();
        });

        VBox page = UI.pageTop(UI.steps(2, 5, "Service"), new VBox(4, clinicName, hint), list, next);
        ScrollPane sp = new ScrollPane(page);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        return sp;
    }
}
