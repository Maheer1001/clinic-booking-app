package view;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import view.ui.UI;

import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NavigatorTest {

    MainView view;
    Navigator nav;
    TrackingViewTest test = new TrackingViewTest();

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already initialized in another test class.
        }
    }

    @BeforeEach
    void setUp() {
        view = new MainView();
        nav = new Navigator(view);
    }

    @Test
    void testInitialState() {
        assertNotNull(nav.getRoot().getTop());
    }

    @Test
    void testNavigateToUpdatesCentreAndTitle() {
        VBox page = new VBox();
        nav.navigateTo(page, "test", false);
        assertEquals(page, nav.getRoot().getCenter());
        assertTrue(test.containsLabelWithGivenText((Parent) nav.getRoot().getTop(), "test"));
    }

    @Test
    void testGoBackReturnsPreviousPage() {
        Node initialPage = nav.getRoot();
        nav.goToPage(new VBox(), "", false);
        nav.goBack();
        assertEquals(initialPage, nav.getRoot());
    }

    @Test
    void testGoBackKeepsSamePageIfStackEmpty() {
        Node initialPage = nav.getRoot();
        nav.goBack();
        assertEquals(initialPage, nav.getRoot());
    }

    @Test
    void testGoToPageChangesPage() {
        VBox vbox = new VBox();
        nav.goToPage(vbox, "test", false);
        assertEquals(vbox, nav.getRoot().getCenter());
        assertTrue(test.containsLabelWithGivenText((Parent) nav.getRoot().getTop(), "test"));
    }

    @Test
    void testGoToPageHomeClearsStack() {
        VBox vbox = new VBox();
        nav.goToPage(vbox, "test", true);
        assertEquals(vbox, nav.getRoot().getCenter());
        assertTrue(test.containsLabelWithGivenText((Parent) nav.getRoot().getTop(), "test"));
        nav.goBack();
        // Stack should be cleared; going back returns to the tab's home page.
        assertNotEquals(vbox, nav.getRoot().getCenter());
    }

    @Test
    void firstPageDoesNotPushAnEmptyEntry() {
        VBox a = new VBox();
        nav.goToPage(a, "A", false);
        assertEquals(0, nav.backStackSize(), "nothing was on screen before the first page");

        VBox b = new VBox();
        nav.goToPage(b, "B", false);
        assertEquals(1, nav.backStackSize());
        nav.goBack();
        assertSame(a, nav.getRoot().getCenter());
        assertNotNull(nav.getRoot().getCenter());
    }

    @Test
    void crossTabLinkHighlightsTabAndKeepsOtherTabsHistory() {
        VBox bookingPage = new VBox();
        nav.goToPage(bookingPage, "Booking page", false);

        VBox remindersPage = new VBox();
        nav.goToPageNewTab("reminders", remindersPage, "Reminders", true);
        assertEquals("reminders", nav.getCurrentTab());
        assertTrue(view.getRemindersButton().isSelected());
        assertSame(remindersPage, nav.getRoot().getCenter());

        // Switching back to Booking restores the page that was open there.
        nav.handleTabClick("booking");
        assertTrue(view.getBookingButton().isSelected());
        assertSame(bookingPage, nav.getRoot().getCenter());

        // ...and Reminders restores its page too.
        nav.handleTabClick("reminders");
        assertSame(remindersPage, nav.getRoot().getCenter());
    }

    @Test
    void unknownTabIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> nav.goToPageNewTab("nope", new VBox(), "x", false));
    }

    @Test
    void headerButtonsHaveAccessibleNames() {
        nav.goToPage(new VBox(), "Somewhere", false);
        var buttons = nav.getRoot().getTop().lookupAll(".app-header-btn");
        assertEquals(2, buttons.size());
        for (var b : buttons) {
            assertNotNull(((javafx.scene.control.Button) b).getAccessibleText());
            assertNotNull(((javafx.scene.control.Button) b).getTooltip());
        }
    }

    @Test
    void backFromSettingsRebuildsTheHomePage() {
        // a home page built before the user edited their name in Settings would still greet the old name
        VBox staleHome = new VBox();
        nav.goToPage(staleHome, "Booking", true);
        nav.goToPage(new VBox(), "Settings", false);
        nav.goBack();
        assertNotSame(staleHome, nav.getRoot().getCenter());
        assertNotNull(nav.getRoot().getCenter());
    }

    @Test
    void backFromOtherPagesKeepsTheSameHomePage() {
        VBox home = new VBox();
        nav.goToPage(home, "Booking", true);
        nav.goToPage(new VBox(), "Find a clinic", false);
        nav.goBack();
        assertSame(home, nav.getRoot().getCenter());
    }

    /** The header's back button (the left one). */
    private javafx.scene.control.Button backButton() {
        return (javafx.scene.control.Button) ((javafx.scene.layout.StackPane) nav.getRoot().getTop()).getChildren().get(1);
    }

    /** Puts a home page and one deeper page in a tab (without building the real, database-backed homes). */
    private VBox[] seedTab(String tab) {
        VBox home = new VBox();
        VBox deeper = new VBox();
        nav.goToPageNewTab(tab, home, tab + " home", true);
        nav.goToPage(deeper, tab + " deeper", false);
        return new VBox[]{home, deeper};
    }

    @Test
    void eachTabKeepsItsOwnPageAndBackStack() {
        VBox[] booking = seedTab("booking");
        VBox[] tracking = seedTab("tracking");
        VBox[] reminders = seedTab("reminders");

        nav.handleTabClick("booking");
        assertSame(booking[1], nav.getRoot().getCenter(), "booking returns to the page it was left on");
        nav.handleTabClick("tracking");
        assertSame(tracking[1], nav.getRoot().getCenter());

        // Back stays inside the tab: tracking goes to its own home, booking's stack is untouched.
        nav.goBack();
        assertSame(tracking[0], nav.getRoot().getCenter());
        nav.handleTabClick("reminders");
        assertSame(reminders[1], nav.getRoot().getCenter());
        nav.handleTabClick("booking");
        assertSame(booking[1], nav.getRoot().getCenter());
        assertEquals(1, nav.backStackSize());
        nav.goBack();
        assertSame(booking[0], nav.getRoot().getCenter());

        nav.handleTabClick("tracking");
        assertSame(tracking[0], nav.getRoot().getCenter(), "tracking still shows the home Back took it to");
    }

    @Test
    void switchingTabsDoesNotGrowTheBackStack() {
        seedTab("booking");
        seedTab("reminders");
        for (int i = 0; i < 5; i++) {
            nav.handleTabClick("booking");
            nav.handleTabClick("reminders");
        }
        assertEquals(1, nav.backStackSize());
        nav.handleTabClick("booking");
        assertEquals(1, nav.backStackSize());
    }

    @Test
    void clickingTheOpenTabStartsItAgainFromItsHome() {
        VBox[] booking = seedTab("booking");
        nav.handleTabClick("booking");
        assertNotSame(booking[1], nav.getRoot().getCenter());
        assertNotSame(booking[0], nav.getRoot().getCenter(), "a fresh home page is built");
        assertEquals(0, nav.backStackSize());
        assertTrue(test.containsLabelWithGivenText((Parent) nav.getRoot().getTop(), "Booking"));
    }

    @Test
    void homePagesHaveNoBackButton() {
        seedTab("reminders");
        assertTrue(backButton().isVisible(), "a deeper page can go back");
        nav.goBack();
        assertFalse(backButton().isVisible(), "a tab's home page has nothing to go back to");
        assertEquals("Back", backButton().getAccessibleText());
        assertFalse(backButton().getTooltip().getText().contains("Close"));
    }

    @Test
    void altLeftGoesBackButNotPastAHomePage() {
        VBox[] reminders = seedTab("reminders");
        Scene scene = new Scene(nav.getRoot(), 420, 720);
        javafx.scene.input.KeyEvent altLeft = new javafx.scene.input.KeyEvent(
                javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", javafx.scene.input.KeyCode.LEFT,
                false, false, true, false);
        javafx.event.Event.fireEvent(nav.getRoot(), altLeft.copyFor(nav.getRoot(), nav.getRoot()));
        assertSame(reminders[0], nav.getRoot().getCenter());
        javafx.event.Event.fireEvent(nav.getRoot(), altLeft.copyFor(nav.getRoot(), nav.getRoot()));
        assertSame(reminders[0], nav.getRoot().getCenter(), "Alt+Left does nothing on a home page");
        assertNotNull(scene);
    }

    @Test
    void tabReturnActionRunsOnlyWhenComingBack() {
        int[] runs = {0};
        nav.setOnTabReturn("reminders", () -> runs[0]++);
        seedTab("booking");
        seedTab("reminders"); // first visit: a page is opened, not returned to
        assertEquals(0, runs[0]);
        nav.handleTabClick("booking");
        nav.handleTabClick("reminders");
        assertEquals(1, runs[0]);
        nav.goToPage(new VBox(), "Deeper still", false); // moving within the tab is not a return
        assertEquals(1, runs[0]);
        nav.setOnTabReturn("reminders", null);
        nav.handleTabClick("booking");
        nav.handleTabClick("reminders");
        assertEquals(1, runs[0]);
    }

    @Test
    void leaveGuardCanCancelEveryWayOut() {
        seedTab("reminders");
        VBox[] booking = seedTab("booking");
        boolean[] allow = {false};
        int[] asked = {0};
        nav.setLeaveGuard(() -> { asked[0]++; return allow[0]; });

        nav.goBack();
        nav.goToPage(new VBox(), "Elsewhere", false);
        nav.goToHome();
        nav.goToPageNewTab("reminders", new VBox(), "Reminders", true);
        view.getRemindersButton().setSelected(true); // the tab button toggles before the click is handled
        nav.handleTabClick("reminders");
        assertEquals(5, asked[0]);
        assertSame(booking[1], nav.getRoot().getCenter(), "every navigation was cancelled");
        assertEquals("booking", nav.getCurrentTab());
        assertTrue(view.getBookingButton().isSelected(), "the highlighted tab snaps back");

        allow[0] = true;
        nav.handleTabClick("reminders");
        assertEquals("reminders", nav.getCurrentTab());
        nav.handleTabClick("booking");
        assertSame(booking[1], nav.getRoot().getCenter());
        nav.goBack();
        assertEquals(7, asked[0], "the guard stays with its page and is asked again");
        assertSame(booking[0], nav.getRoot().getCenter());
        nav.goToPage(new VBox(), "Unguarded", false);
        nav.goBack();
        assertEquals(7, asked[0], "other pages are not guarded");
    }

    @Test
    void clearingAnotherTabsHistoryMakesItStartFromHome() {
        VBox[] reminders = seedTab("reminders");
        seedTab("booking");
        nav.clearTabStack("reminders");
        nav.goToPageNewTab("reminders", new VBox(), "Reminders", true);
        assertNotSame(reminders[1], nav.getRoot().getCenter());
        assertEquals(0, nav.backStackSize());
    }

    @Test
    void resetForgetsEveryTab() {
        seedTab("reminders");
        seedTab("booking");
        int[] runs = {0};
        nav.setOnTabReturn("booking", () -> runs[0]++);
        nav.reset();
        VBox fresh = new VBox();
        nav.goToPageNewTab("reminders", fresh, "Reminders", true);
        assertEquals(0, nav.backStackSize());
        nav.goToPageNewTab("booking", new VBox(), "Booking", true);
        nav.handleTabClick("reminders");
        assertSame(fresh, nav.getRoot().getCenter());
        nav.handleTabClick("booking");
        assertEquals(0, runs[0], "return actions belong to the old session");
    }

    @Test
    void tabLabelsFitAtEveryTextSize() throws Exception {
        // measure with the app's real font; the default font is narrower and would hide the problem
        for (String f : new String[]{"Regular", "SemiBold", "Bold", "ExtraBold"}) {
            try (var in = getClass().getResourceAsStream("/fonts/Nunito-" + f + ".ttf")) {
                Font.loadFont(in, 14);
            }
        }
        for (String size : new String[]{null, UI.TEXT_SIZE_UP_STYLE, UI.TEXT_SIZE_DOWN_STYLE}) {
            MainView v = new MainView();
            Navigator n = new Navigator(v);
            v.getRemindersButton().setSelected(true); // the selected tab uses the heaviest weight
            Scene scene = new Scene(n.getRoot(), 420, 720);
            scene.getStylesheets().addAll(UI.STYLE, UI.BOLD_STYLE);
            if (size != null) scene.getStylesheets().add(size);
            n.getRoot().resize(420, 720);
            n.getRoot().applyCss();
            n.getRoot().layout();
            for (var b : List.of(v.getBookingButton(), v.getTrackingButton(), v.getRemindersButton())) {
                Text needed = new Text(b.getText());
                needed.setFont(b.getFont());
                double room = b.getWidth() - b.snappedLeftInset() - b.snappedRightInset();
                // a few px of slack: on a real (scaled) display "Reminders" was ellipsised with under 1px to spare
                assertTrue(needed.getLayoutBounds().getWidth() + 8 <= room,
                        "'" + b.getText() + "' is cut short with " + size + ": needs "
                                + needed.getLayoutBounds().getWidth() + "px, has " + room + "px");
            }
        }
    }

    // ---------------------------------------------------------------- custom tab config

    /** Two tabs whose home pages are counted, so tests can see when a home is (re)built. */
    private static final class CustomTabs {
        final int[] builds = new int[2];
        final List<Navigator.Tab> tabs = List.of(
                Navigator.Tab.of("today", "Today", n -> { builds[0]++; return new VBox(); }),
                Navigator.Tab.of("patients", "Patients", n -> { builds[1]++; return new VBox(); }));
        final VBox settings = new VBox();
        final Navigator nav = new Navigator(tabs, () -> settings);
    }

    @Test
    void patientConfigKeepsItsThreeTabsInOrder() {
        assertEquals(List.of("booking", "tracking", "reminders"), nav.getTabIds());
        assertEquals("booking", nav.getCurrentTab());
    }

    @Test
    void customTabsAreShownInOrderAndStartOnTheFirst() {
        CustomTabs c = new CustomTabs();
        assertEquals(List.of("today", "patients"), c.nav.getTabIds());
        var bar = c.nav.getRoot().getBottom().lookupAll("." + UI.NAV_BTN);
        assertEquals(2, bar.size(), "only the configured tabs are in the tab bar");

        c.nav.start();
        assertEquals("today", c.nav.getCurrentTab());
        assertTrue(c.tabs.get(0).button().isSelected());
        assertEquals(1, c.builds[0]);
        assertEquals(0, c.builds[1], "other tabs are built on first visit");
        assertTrue(test.containsLabelWithGivenText((Parent) c.nav.getRoot().getTop(), "Today"));
    }

    @Test
    void customTabsKeepTheirOwnHistory() {
        CustomTabs c = new CustomTabs();
        c.nav.start();
        VBox deeper = new VBox();
        c.nav.goToPage(deeper, "Appointment", false);
        c.tabs.get(1).button().fire(); // a real click on the tab button
        assertEquals("patients", c.nav.getCurrentTab());
        assertTrue(c.tabs.get(1).button().isSelected());
        assertEquals(1, c.builds[1]);
        c.tabs.get(0).button().fire();
        assertSame(deeper, c.nav.getRoot().getCenter(), "Today comes back on the page it was left on");
        assertEquals(1, c.nav.backStackSize());
        c.tabs.get(0).button().fire(); // the open tab again: fresh home, still selected
        assertEquals(2, c.builds[0]);
        assertTrue(c.tabs.get(0).button().isSelected());
        assertEquals(0, c.nav.backStackSize());
    }

    @Test
    void customSettingsPageAndUnknownTabs() {
        CustomTabs c = new CustomTabs();
        c.nav.start();
        c.nav.goToSettings();
        assertSame(c.settings, c.nav.getRoot().getCenter());
        c.nav.goToSettings(); // pressing Settings again goes back
        assertNotSame(c.settings, c.nav.getRoot().getCenter());
        assertThrows(IllegalArgumentException.class, () -> c.nav.handleTabClick("booking"));
    }

    @Test
    void tabConfigMustBeNonEmptyAndUnique() {
        assertThrows(IllegalArgumentException.class, () -> new Navigator(List.of(), VBox::new));
        assertThrows(IllegalArgumentException.class, () -> new Navigator(List.of(
                Navigator.Tab.of("a", "A", n -> new VBox()),
                Navigator.Tab.of("a", "Again", n -> new VBox())), VBox::new));
    }
}

