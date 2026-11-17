package view;

import app.Data;
import controller.TrackingController;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import service.TrackingService;
import view.reminders.RemindersView;
import view.ui.UI;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.function.Supplier;

/**
 * Controls navigation between pages in the main application.
 * Root is a BorderPane with header (back, title, home), centre content, and a persistent bottom tab bar.
 */
public class Navigator {
    private record Page(Node page, String title, boolean isHome) {}

    private final MainView view;
    private final BorderPane root = new BorderPane();
    private final Map<String, Stack<Page>> tabHistory = new HashMap<>();
    private final StringProperty currentTab = new SimpleStringProperty("booking");
    private final ObjectProperty<Node> currentPage = new SimpleObjectProperty<>();
    private final StringProperty title = new SimpleStringProperty();
    private final BooleanProperty isHome = new SimpleBooleanProperty(true);
    private final Map<String, Supplier<Page>> homePages = new HashMap<>();
    private final BooleanProperty isSettings = new SimpleBooleanProperty(false);

    public Navigator(MainView view) {
        this.view = view;

        tabHistory.put("booking", new Stack<>());
        tabHistory.put("tracking", new Stack<>());
        tabHistory.put("reminders", new Stack<>());

        int userID = Data.getCurrentUserId();

        homePages.put("booking", () -> new Page(view.getPage(this), "Booking", true));
        homePages.put("tracking", () -> {
            TrackingController tc = new TrackingController(this, new TrackingService(userID));
            return new Page(tc.getTrackingMainScene(), "Tracking", true);
        });
        homePages.put("reminders", () -> new Page(new RemindersView(userID).getRemindersScene("appointments"), "Reminders", true));

        Label title = new Label();
        title.getStyleClass().add(UI.TITLE);
        Button settingsButton = UI.backButton("");
        Button backButton = UI.backButton("");

        SVGPath backIcon = new SVGPath();
        backIcon.setContent("M19,11H9l3.29-3.29a1,1,0,0,0,0-1.42,1,1,0,0,0-1.41,0l-4.29,4.3A2,2,0,0,0,6,12H6a2,2,0,0,0,.59,1.4l4.29,4.3a1,1,0,1,0,1.41-1.42L9,13H19a1,1,0,0,0,0-2Z");
        SVGPath settingsIcon = new SVGPath();
        settingsIcon.setContent("M34.283,384c17.646,30.626,56.779,41.148,87.405,23.502c0.021-0.012,0.041-0.024,0.062-0.036l9.493-5.483   c17.92,15.332,38.518,27.222,60.757,35.072V448c0,35.346,28.654,64,64,64s64-28.654,64-64v-10.944   c22.242-7.863,42.841-19.767,60.757-35.115l9.536,5.504c30.633,17.673,69.794,7.167,87.467-23.467   c17.673-30.633,7.167-69.794-23.467-87.467l0,0l-9.472-5.461c4.264-23.201,4.264-46.985,0-70.187l9.472-5.461   c30.633-17.673,41.14-56.833,23.467-87.467c-17.673-30.633-56.833-41.14-87.467-23.467l-9.493,5.483   C362.862,94.638,342.25,82.77,320,74.944V64c0-35.346-28.654-64-64-64s-64,28.654-64,64v10.944   c-22.242,7.863-42.841,19.767-60.757,35.115l-9.536-5.525C91.073,86.86,51.913,97.367,34.24,128s-7.167,69.794,23.467,87.467l0,0   l9.472,5.461c-4.264,23.201-4.264,46.985,0,70.187l-9.472,5.461C27.158,314.296,16.686,353.38,34.283,384z M256,170.667   c47.128,0,85.333,38.205,85.333,85.333S303.128,341.333,256,341.333S170.667,303.128,170.667,256S208.872,170.667,256,170.667z");
        SVGPath exitIcon = new SVGPath();
        exitIcon.setContent("M301.258,256.01L502.645,54.645c12.501-12.501,12.501-32.769,0-45.269c-12.501-12.501-32.769-12.501-45.269,0l0,0   L256.01,210.762L54.645,9.376c-12.501-12.501-32.769-12.501-45.269,0s-12.501,32.769,0,45.269L210.762,256.01L9.376,457.376   c-12.501,12.501-12.501,32.769,0,45.269s32.769,12.501,45.269,0L256.01,301.258l201.365,201.387   c12.501,12.501,32.769,12.501,45.269,0c12.501-12.501,12.501-32.769,0-45.269L301.258,256.01z");

        Region backRegion = new Region();
        backRegion.setShape(backIcon);
        backRegion.setPrefHeight(20);
        backRegion.setPrefWidth(30);
        backIcon.setScaleX(0.8);
        backIcon.setScaleY(0.8);
        backRegion.getStyleClass().add("app-btn-icon");

        Region settingsRegion = new Region();
        settingsRegion.setShape(settingsIcon);
        settingsRegion.setPrefHeight(20);
        settingsRegion.setPrefWidth(26);
        exitIcon.setScaleX(0.8);
        exitIcon.setScaleY(0.8);
        settingsRegion.getStyleClass().add("app-btn-icon");

        Region exitRegion = new Region();
        exitRegion.setShape(exitIcon);
        exitRegion.setPrefHeight(20);
        exitRegion.setPrefWidth(25);
        exitIcon.setScaleX(0.8);
        exitIcon.setScaleY(0.8);
        exitRegion.getStyleClass().add("app-btn-icon");

        settingsButton.setGraphic(settingsRegion);
        backButton.graphicProperty().bind(Bindings.when(isHome).then(exitRegion).otherwise(backRegion));
        StackPane header = getHeader(backButton, title, settingsButton);
        root.setTop(header);
        StackPane navBar = getNavBar(view.getBookingButton(), view.getTrackingButton(), view.getRemindersButton());
        root.setBottom(navBar);

        root.centerProperty().bind(currentPage);
        title.textProperty().bind(this.title);

        isSettings.bind(this.title.isEqualTo("Settings"));

        backButton.setOnAction(e -> {
            if (isHome.get()) {
                exit();
            } else {
                goBack();
            }
                }
        );
        settingsButton.setOnAction(e -> {
            /* if (isHome.get()) {
                goToSettings();
            } else {
                goToHome();
            }
             */
            goToSettings();
        });

    }

    /** Returns the root BorderPane for the main scene. */
    public BorderPane getRoot() { return root; }

    public void handleTabClick(String tab) {
        if (currentTab.get().equals(tab)) {
            tabHistory.get(tab).clear();
            Supplier<Page> factory = homePages.get(tab);
            if (factory != null) {
                Page curr = factory.get();
                navigateTo(curr.page, curr.title, curr.isHome);
            }
        } else {
            switchTab(tab);
        }
    }

    private void switchTab(String tab) {
        String oldTab = currentTab.get();
        Node oldPage = currentPage.get();
        if (oldPage != null) {
            tabHistory.get(oldTab).push(new Page(oldPage, title.get(), isHome.get()));
        }
        currentTab.set(tab);
        Stack<Page> stack = tabHistory.get(tab);
        if (stack.isEmpty()) {
            Supplier<Page> factory = homePages.get(tab);
            if (factory != null) {
                Page curr = factory.get();
                navigateTo(curr.page, curr.title, curr.isHome);
            }
        } else {
            Page next = stack.pop();
            navigateTo(next.page, next.title, next.isHome);
        }
    }

    public void goToPageNewTab(String tab, Node page, String title, boolean isHome) {
        Stack<Page> oldStack = tabHistory.get(currentTab.get());
        oldStack.push(new Page(currentPage.get(), this.title.get(), this.isHome.get()));
        currentTab.set(tab);
        switch (tab) {
            case "booking":
                view.getBookingButton().selectedProperty().set(true);
                break;
            case "tracking":
                view.getTrackingButton().selectedProperty().set(true);
                break;
            case "reminders":
                view.getRemindersButton().selectedProperty().set(true);
                break;
        }
        Stack<Page> stack = tabHistory.get(tab);
        if (isHome) {
            stack.clear();
        }
        navigateTo(page, title, isHome);
    }

    public void clearTabStack(String tab) {
        tabHistory.get(tab).clear();
    }

    /** Updates the displayed page and title without changing history. */
    public void navigateTo(Node page, String title, Boolean isHome) {
        currentPage.set(page);
        this.title.set(title);
        this.isHome.set(isHome);
    }

    /** Displays the given page, optionally pushing the current page onto the back stack. */
    public void goToPage(Node page, String title, boolean isHome) {
        Stack<Page> stack = tabHistory.get(currentTab.get());
        if (isHome) {
            navigateTo(page, title, true);
            stack.clear();
        } else {
            stack.push(new Page(currentPage.get(), this.title.get(), this.isHome.get()));
            navigateTo(page, title, false);
        }
    }

    /** Pops the previous page from the stack and displays it (bound to the Back button). */
    public void goBack() {
        Stack<Page> stack = tabHistory.get(currentTab.get());
        if (!stack.isEmpty()) {
            Page previousPage = stack.pop();
            navigateTo(previousPage.page(), previousPage.title(), previousPage.isHome());
        } else {
            goToHome();
        }
    }

    /** Builds the header grid (back button, title label, home button). */
    public StackPane getHeader(Button back, Label title, Button home) {
        StackPane header = new StackPane();
        header.setPadding(new Insets(10));
        header.getStyleClass().add("app-header");

        StackPane.setAlignment(title, Pos.CENTER);
        StackPane.setAlignment(back, Pos.CENTER_LEFT);
        StackPane.setAlignment(home, Pos.CENTER_RIGHT);

        header.getChildren().addAll(title, back, home);
        return header;
    }

    public StackPane getNavBar(ToggleButton booking, ToggleButton tracking, ToggleButton reminders) {
        StackPane navBar = new StackPane();
        navBar.setPadding(new Insets(10));

        StackPane.setAlignment(booking, Pos.CENTER_LEFT);
        StackPane.setAlignment(tracking, Pos.CENTER);
        StackPane.setAlignment(reminders, Pos.CENTER_RIGHT);

        navBar.getChildren().addAll(booking, tracking, reminders);
        navBar.getStyleClass().add(UI.MAIN_NAV);
        return navBar;
    }

    public void goToHome() {
        String tab = currentTab.get();
        Page home = homePages.get(tab).get();
        goToPage(home.page, home.title, home.isHome);
    }

    public void goToSettings() {
        if (isSettings.get()) {
            goBack();
        } else {
            goToPage(view.getSettingsScene(), "Settings", false);
        }
    }

    public void exit() { Platform.exit(); }
}
