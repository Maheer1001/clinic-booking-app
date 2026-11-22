package view;

import app.Data;
import controller.TrackingController;
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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Controls navigation between pages in the main application.
 * Root is a BorderPane with header (back, title, settings), centre content, and a persistent bottom tab bar.
 *
 * <p>Each tab keeps its own history: the page it is showing and a back stack. Switching tabs never
 * touches another tab's pages, so coming back to a tab shows exactly the page it was left on (the same
 * node, so the same scroll position, half-filled fields and chosen sub-tab). Back only moves within the
 * current tab. A tab's home page has no back button; closing the app is left to the window controls.
 */
public class Navigator {
    private record Page(Node page, String title, boolean isHome) {}

    /** One tab's history: the page it shows (null until first visited) and the pages Back returns to. */
    private static final class TabState {
        final Deque<Page> back = new ArrayDeque<>();
        Page shown;
    }

    private static final List<String> TABS = List.of("booking", "tracking", "reminders");

    private final MainView view;
    private final BorderPane root = new BorderPane();
    private final Map<String, TabState> tabs = new HashMap<>();
    private final StringProperty currentTab = new SimpleStringProperty("booking");
    private final ObjectProperty<Node> currentPage = new SimpleObjectProperty<>();
    private final StringProperty title = new SimpleStringProperty();
    private final BooleanProperty isHome = new SimpleBooleanProperty(true);
    private final Map<String, Supplier<Page>> homePages = new HashMap<>();
    private final Map<String, Runnable> tabReturnActions = new HashMap<>();
    /** Leave guards, keyed by the page they protect (by identity). */
    private final Map<Node, BooleanSupplier> leaveGuards = new IdentityHashMap<>();
    private final BooleanProperty isSettings = new SimpleBooleanProperty(false);

    private static final String HEADER_BTN = "app-header-btn";
    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
    /** Horizontal space reserved on each side of the header title for the icon buttons. */
    private static final double TITLE_SIDE_GUTTER = 64;

    private static final String BACK_ICON = "M19,11H9l3.29-3.29a1,1,0,0,0,0-1.42,1,1,0,0,0-1.41,0l-4.29,4.3A2,2,0,0,0,6,12H6a2,2,0,0,0,.59,1.4l4.29,4.3a1,1,0,1,0,1.41-1.42L9,13H19a1,1,0,0,0,0-2Z";
    private static final String SETTINGS_ICON = "M34.283,384c17.646,30.626,56.779,41.148,87.405,23.502c0.021-0.012,0.041-0.024,0.062-0.036l9.493-5.483   c17.92,15.332,38.518,27.222,60.757,35.072V448c0,35.346,28.654,64,64,64s64-28.654,64-64v-10.944   c22.242-7.863,42.841-19.767,60.757-35.115l9.536,5.504c30.633,17.673,69.794,7.167,87.467-23.467   c17.673-30.633,7.167-69.794-23.467-87.467l0,0l-9.472-5.461c4.264-23.201,4.264-46.985,0-70.187l9.472-5.461   c30.633-17.673,41.14-56.833,23.467-87.467c-17.673-30.633-56.833-41.14-87.467-23.467l-9.493,5.483   C362.862,94.638,342.25,82.77,320,74.944V64c0-35.346-28.654-64-64-64s-64,28.654-64,64v10.944   c-22.242,7.863-42.841,19.767-60.757,35.115l-9.536-5.525C91.073,86.86,51.913,97.367,34.24,128s-7.167,69.794,23.467,87.467l0,0   l9.472,5.461c-4.264,23.201-4.264,46.985,0,70.187l-9.472,5.461C27.158,314.296,16.686,353.38,34.283,384z M256,170.667   c47.128,0,85.333,38.205,85.333,85.333S303.128,341.333,256,341.333S170.667,303.128,170.667,256S208.872,170.667,256,170.667z";

    public Navigator(MainView view) {
        this.view = view;

        for (String tab : TABS) {
            tabs.put(tab, new TabState());
        }

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

        // Sized to each glyph's proportions so the two icons look the same weight.
        Region backRegion = icon(BACK_ICON, 20, 17);
        Region settingsRegion = icon(SETTINGS_ICON, 20, 20);

        settingsButton.setGraphic(settingsRegion);
        settingsButton.setAccessibleText("Settings");
        settingsButton.setTooltip(new Tooltip("Settings"));
        backButton.setGraphic(backRegion);
        backButton.setAccessibleText("Back");
        backButton.setTooltip(new Tooltip("Back (Alt+Left)"));
        // A tab's home page is the top of that tab, so there is nothing to go back to and no button
        // (the window's own controls close the app). It stays managed so the title never shifts.
        backButton.visibleProperty().bind(isHome.not());

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

        backButton.setOnAction(e -> goBack());
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

    private TabState state(String tab) {
        TabState state = tabs.get(tab);
        if (state == null) {
            throw new IllegalArgumentException("Unknown tab: " + tab);
        }
        return state;
    }

    private TabState current() { return state(currentTab.get()); }

    /** The tab whose pages are currently shown ("booking", "tracking" or "reminders"). */
    public String getCurrentTab() { return currentTab.get(); }

    /** Returns the root BorderPane for the main scene. */
    public BorderPane getRoot() { return root; }

    /**
     * A bottom-tab click. Another tab: show it exactly as it was left (or its home on first visit).
     * The tab already open: start that tab again from a fresh home page, like most mobile apps.
     */
    public void handleTabClick(String tab) {
        state(tab);
        if (!confirmLeave()) {
            selectTabButton(currentTab.get()); // the clicked button already toggled itself on
            return;
        }
        if (currentTab.get().equals(tab)) {
            current().back.clear();
            showHome();
        } else {
            switchTab(tab);
        }
    }

    private void switchTab(String tab) {
        currentTab.set(tab);
        TabState state = current();
        if (state.shown == null) {
            showHome();
        } else {
            show(state.shown);
            Runnable onReturn = tabReturnActions.get(tab);
            if (onReturn != null) onReturn.run();
        }
    }

    /** Builds the current tab's home page and shows it, without touching the back stack. */
    private void showHome() {
        Supplier<Page> factory = homePages.get(currentTab.get());
        if (factory != null) show(factory.get());
    }

    private void show(Page page) {
        current().shown = page;
        currentPage.set(page.page());
        title.set(page.title());
        isHome.set(page.isHome());
    }

    /**
     * Shows a page in another tab (e.g. "Set up reminders" from a booking). The tab being left keeps its
     * page, so returning to it later restores it. A home page clears the target tab's back stack;
     * otherwise Back from the new page returns to what that tab was showing.
     */
    public void goToPageNewTab(String tab, Node page, String title, boolean isHome) {
        TabState target = state(tab);
        if (!confirmLeave()) return;
        currentTab.set(tab); // the listener highlights the matching nav button
        if (isHome) {
            target.back.clear();
        } else if (target.shown != null) {
            target.back.push(target.shown);
        }
        show(new Page(page, title, isHome));
    }

    /**
     * Forgets a tab's history. For the tab on screen that is its back stack; for another tab it is
     * everything, so the next visit opens its home page (e.g. after a booking is finished).
     */
    public void clearTabStack(String tab) {
        TabState state = state(tab);
        state.back.clear();
        if (!tab.equals(currentTab.get())) {
            state.shown = null;
        }
    }

    /** Forgets every tab's history and listener (used on log out). */
    public void reset() {
        for (TabState state : tabs.values()) {
            state.back.clear();
            state.shown = null;
        }
        tabReturnActions.clear();
        leaveGuards.clear();
    }

    /**
     * Runs {@code action} each time the user comes back to {@code tab} from another tab and its kept
     * page is shown again (not on its first visit, nor when the tab is rebuilt from its home).
     * One action per tab; a later call replaces it, {@code null} removes it.
     */
    public void setOnTabReturn(String tab, Runnable action) {
        state(tab);
        if (action == null) tabReturnActions.remove(tab);
        else tabReturnActions.put(tab, action);
    }

    /**
     * Guards the page currently on screen: before any navigation away from it (Back, Alt+Left, a tab
     * click, a cross-tab link, the Settings button, going home) the guard is asked, and the navigation
     * is cancelled if it returns false. It may show a dialog (e.g. Save / Discard / Keep editing) and
     * should return true once it is fine to leave. It stays with that page, so it applies again if the
     * page is returned to. {@code null} removes the page's guard.
     */
    public void setLeaveGuard(BooleanSupplier guard) {
        Node page = currentPage.get();
        if (page == null) return;
        if (guard == null) leaveGuards.remove(page);
        else leaveGuards.put(page, guard);
    }

    /** Asks the current page's leave guard, if any; true when navigation may go ahead. */
    public boolean confirmLeave() {
        Node page = currentPage.get();
        BooleanSupplier guard = page == null ? null : leaveGuards.get(page);
        return guard == null || guard.getAsBoolean();
    }

    /** Updates the displayed page and title without changing history. */
    public void navigateTo(Node page, String title, Boolean isHome) {
        show(new Page(page, title, isHome));
    }

    /** Displays the given page; a home page clears the tab's back stack, any other is pushed onto it. */
    public void goToPage(Node page, String title, boolean isHome) {
        if (!confirmLeave()) return;
        TabState state = current();
        if (isHome) {
            state.back.clear();
        } else if (state.shown != null) {
            // nothing is on screen before the first page, so there is nothing to push
            state.back.push(state.shown);
        }
        show(new Page(page, title, isHome));
    }

    /** Number of pages Back can return to in the current tab (for tests and diagnostics). */
    int backStackSize() {
        return current().back.size();
    }

    /** Pops the previous page from the current tab's stack and displays it (bound to the Back button). */
    public void goBack() {
        TabState state = current();
        if (state.back.isEmpty()) {
            goToHome();
            return;
        }
        if (!confirmLeave()) return;
        Page previousPage = state.back.pop();
        if (isSettings.get() && previousPage.isHome()) {
            // Settings can change what a home page shows (e.g. the "Hi <name>" greeting),
            // so rebuild it rather than returning to the stale copy.
            Supplier<Page> factory = homePages.get(currentTab.get());
            if (factory != null) previousPage = factory.get();
        }
        show(previousPage);
    }

    /** Builds the header grid (back button, title label, settings button). */
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

    /** Shows a fresh home page for the current tab and clears its back stack. */
    public void goToHome() {
        if (!confirmLeave()) return;
        current().back.clear();
        showHome();
    }

    public void goToSettings() {
        if (isSettings.get()) {
            goBack();
        } else {
            goToPage(view.getSettingsScene(), "Settings", false);
        }
    }
}
