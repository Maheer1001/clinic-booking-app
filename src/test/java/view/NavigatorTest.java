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
}

