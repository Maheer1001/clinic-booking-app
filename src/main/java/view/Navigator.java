package view;

import app.Data;
import controller.TrackingController;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import service.TrackingService;
import view.reminders.RemindersView;
import view.ui.UI;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.function.Predicate;
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

    /**
     * Leave guard hook. A page can put a {@code Predicate<Runnable>} in its {@link Node#getProperties()}
     * under this key; it is asked before Back, the Settings gear, a tab click or log out takes the user
     * off that page. It returns true to leave now, or false to stay (e.g. it asked "Discard your
     * changes?"), and may later run the Runnable it was given to repeat the navigation (after a save).
     */
    public static final String LEAVE_GUARD = "navigator.leaveGuard";

    private static final String HEADER_BTN = "app-header-btn";
    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
    /** Horizontal space reserved on each side of the header title for the icon buttons. */
    private static final double TITLE_SIDE_GUTTER = 64;

    private static final String BACK_ICON = "M19,11H9l3.29-3.29a1,1,0,0,0,0-1.42,1,1,0,0,0-1.41,0l-4.29,4.3A2,2,0,0,0,6,12H6a2,2,0,0,0,.59,1.4l4.29,4.3a1,1,0,1,0,1.41-1.42L9,13H19a1,1,0,0,0,0-2Z";
    private static final String SETTINGS_ICON = "M34.283,384c17.646,30.626,56.779,41.148,87.405,23.502c0.021-0.012,0.041-0.024,0.062-0.036l9.493-5.483   c17.92,15.332,38.518,27.222,60.757,35.072V448c0,35.346,28.654,64,64,64s64-28.654,64-64v-10.944   c22.242-7.863,42.841-19.767,60.757-35.115l9.536,5.504c30.633,17.673,69.794,7.167,87.467-23.467   c17.673-30.633,7.167-69.794-23.467-87.467l0,0l-9.472-5.461c4.264-23.201,4.264-46.985,0-70.187l9.472-5.461   c30.633-17.673,41.14-56.833,23.467-87.467c-17.673-30.633-56.833-41.14-87.467-23.467l-9.493,5.483   C362.862,94.638,342.25,82.77,320,74.944V64c0-35.346-28.654-64-64-64s-64,28.654-64,64v10.944   c-22.242,7.863-42.841,19.767-60.757,35.115l-9.536-5.525C91.073,86.86,51.913,97.367,34.24,128s-7.167,69.794,23.467,87.467l0,0   l9.472,5.461c-4.264,23.201-4.264,46.985,0,70.187l-9.472,5.461C27.158,314.296,16.686,353.38,34.283,384z M256,170.667   c47.128,0,85.333,38.205,85.333,85.333S303.128,341.333,256,341.333S170.667,303.128,170.667,256S208.872,170.667,256,170.667z";
    private static final String EXIT_ICON = "M301.258,256.01L502.645,54.645c12.501-12.501,12.501-32.769,0-45.269c-12.501-12.501-32.769-12.501-45.269,0l0,0   L256.01,210.762L54.645,9.376c-12.501-12.501-32.769-12.501-45.269,0s-12.501,32.769,0,45.269L210.762,256.01L9.376,457.376   c-12.501,12.501-12.501,32.769,0,45.269s32.769,12.501,45.269,0L256.01,301.258l201.365,201.387   c12.501,12.501,32.769,12.501,45.269,0c12.501-12.501,12.501-32.769,0-45.269L301.258,256.01z";

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
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        title.setAlignment(Pos.CENTER);
        Button settingsButton = UI.backButton("");
        Button backButton = UI.backButton("");
        settingsButton.getStyleClass().add(HEADER_BTN);
        backButton.getStyleClass().add(HEADER_BTN);

        // Sized to each glyph's proportions so the three icons look the same weight.
        Region backRegion = icon(BACK_ICON, 20, 17);
        Region settingsRegion = icon(SETTINGS_ICON, 20, 20);
        Region exitRegion = icon(EXIT_ICON, 15, 15);

        settingsButton.setGraphic(settingsRegion);
        settingsButton.setAccessibleText("Settings");
        settingsButton.setTooltip(new Tooltip("Settings"));
        backButton.graphicProperty().bind(Bindings.when(isHome).then(exitRegion).otherwise(backRegion));
        Tooltip backTip = new Tooltip();
        backTip.textProperty().bind(Bindings.when(isHome).then("Close app").otherwise("Back (Alt+Left)"));
        backButton.setTooltip(backTip);
        backButton.accessibleTextProperty().bind(Bindings.when(isHome).then("Close app").otherwise("Back"));

        StackPane header = getHeader(backButton, title, settingsButton);
        // Keep long titles clear of the buttons on either side; they are truncated with an ellipsis.
        title.maxWidthProperty().bind(header.widthProperty().subtract(TITLE_SIDE_GUTTER * 2));
        root.setTop(header);
        StackPane navBar = getNavBar(view.getBookingButton(), view.getTrackingButton(), view.getRemindersButton());
        root.setBottom(navBar);

        root.centerProperty().bind(currentPage);
        title.textProperty().bind(this.title);

        isSettings.bind(this.title.isEqualTo("Settings"));
        isSettings.addListener((obs, was, now) -> settingsButton.pseudoClassStateChanged(ACTIVE, now));

        // Keep the highlighted tab in sync however the tab changed (click, cross-tab link, ...).
        currentTab.addListener((obs, old, tab) -> selectTabButton(tab));

        backButton.setOnAction(e -> {
            if (isHome.get()) {
                exit();
            } else {
                goBack();
            }
        });
        settingsButton.setOnAction(e -> goToSettings());

        // Keyboard shortcut for Back, like a browser.
        root.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.LEFT && e.isAltDown() && !isHome.get()) {
                goBack();
                e.consume();
            }
        });
    }

    private static Region icon(String svg, double width, double height) {
        SVGPath path = new SVGPath();
        path.setContent(svg);
        Region region = new Region();
        region.setShape(path);
        region.setMinSize(width, height);
        region.setPrefSize(width, height);
        region.setMaxSize(width, height);
        region.getStyleClass().add("app-btn-icon");
        return region;
    }

    private void selectTabButton(String tab) {
        ToggleButton button = switch (tab) {
            case "booking" -> view.getBookingButton();
            case "tracking" -> view.getTrackingButton();
            case "reminders" -> view.getRemindersButton();
            default -> null;
        };
        if (button != null && !button.isSelected()) {
            button.setSelected(true);
        }
    }

    /** The tab whose pages are currently shown ("booking", "tracking" or "reminders"). */
    public String getCurrentTab() { return currentTab.get(); }

    /** Returns the root BorderPane for the main scene. */
    public BorderPane getRoot() { return root; }

    public void handleTabClick(String tab) {
        if (!mayLeave(() -> handleTabClick(tab))) {
            selectTabButton(currentTab.get()); // the clicked tab had already lit up
            return;
        }
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
        pushCurrentPage(currentTab.get());
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

    /**
     * Shows a page in another tab (e.g. "Set up reminders" from a booking). The current page is kept
     * in its own tab's history, so returning to that tab later restores it.
     */
    public void goToPageNewTab(String tab, Node page, String title, boolean isHome) {
        if (!tabHistory.containsKey(tab)) {
            throw new IllegalArgumentException("Unknown tab: " + tab);
        }
        pushCurrentPage(currentTab.get());
        currentTab.set(tab); // the listener highlights the matching nav button
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
            pushCurrentPage(currentTab.get());
            navigateTo(page, title, false);
        }
    }

    /** Remembers the page on screen in the given tab's history (nothing is on screen before the first page). */
    private void pushCurrentPage(String tab) {
        Node page = currentPage.get();
        if (page != null) {
            tabHistory.get(tab).push(new Page(page, title.get(), isHome.get()));
        }
    }

    /** Number of pages Back can return to in the current tab (for tests and diagnostics). */
    int backStackSize() {
        return tabHistory.get(currentTab.get()).size();
    }

    /** Pops the previous page from the stack and displays it (bound to the Back button). */
    public void goBack() {
        if (!mayLeave(this::goBack)) return;
        Stack<Page> stack = tabHistory.get(currentTab.get());
        if (!stack.isEmpty()) {
            Page previousPage = stack.pop();
            if (isSettings.get() && previousPage.isHome()) {
                // Settings can change what a home page shows (e.g. the "Hi <name>" greeting),
                // so rebuild it rather than returning to the stale copy.
                Supplier<Page> factory = homePages.get(currentTab.get());
                if (factory != null) previousPage = factory.get();
            }
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

    /** Bottom tab bar: three equal-width tabs that share the window width as it is resized. */
    public StackPane getNavBar(ToggleButton booking, ToggleButton tracking, ToggleButton reminders) {
        HBox tabs = new HBox(8, booking, tracking, reminders);
        tabs.setAlignment(Pos.CENTER);
        for (ToggleButton b : new ToggleButton[]{booking, tracking, reminders}) {
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
        }

        StackPane navBar = new StackPane(tabs);
        navBar.setPadding(new Insets(8, 10, 10, 10));
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

    /**
     * Asks the page on screen whether it may be left (see {@link #LEAVE_GUARD}). True means go ahead
     * now; false means stay, and the page may call {@code retry} later to repeat the navigation.
     */
    @SuppressWarnings("unchecked")
    public boolean mayLeave(Runnable retry) {
        Node page = currentPage.get();
        if (page == null || !(page.getProperties().get(LEAVE_GUARD) instanceof Predicate<?> guard)) return true;
        return ((Predicate<Runnable>) guard).test(retry);
    }

    public void exit() { Platform.exit(); }
}
