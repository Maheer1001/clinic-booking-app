package view;

import app.Data;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import model.Clinic;
import model.Service;
import view.ui.UI;

import java.util.List;

public class ServiceInfo {

    public static Node getPage() {
        Clinic clinic = Data.getBooking().getClinic();
        if (clinic == null) {
            return UI.page(UI.muted("Please select a clinic first."));
        }

        List<Service> services = clinic.getServices();

        Label clinicName = UI.heading("Services Provided by clinic: " + clinic.getName());
        Label emptyMessage = services.isEmpty() ? UI.muted("No services available for this clinic.") : new Label();

        ListView<Service> listview = new ListView<>();
        listview.setItems(FXCollections.observableArrayList(services));


        listview.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Service service, boolean empty) {
                super.updateItem(service, empty);

                if (empty || service == null) {
                    setGraphic(null);
                    return;
                }

                Label name = new Label(service.getName());
                name.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

                Label duration = new Label(
                        "Estimated duration: " + service.getDurationMinutes() + " Minutes"
                );

                Label descriptionTitle = new Label("Description:");
                descriptionTitle.setStyle("-fx-font-weight: bold;");

                Label description = new Label(service.getDescription());
                description.setWrapText(true);
                description.setMaxWidth(Double.MAX_VALUE);

                VBox container = new VBox(5, name, duration, descriptionTitle, description);
                container.setFillWidth(true);
                container.setPadding(new Insets(10));

                setGraphic(container);
            }
        });
        listview.setFixedCellSize(-1);

        return UI.page(listview, emptyMessage);

    }
}
