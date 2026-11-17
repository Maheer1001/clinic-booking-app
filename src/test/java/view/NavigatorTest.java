package view;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.layout.VBox;
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
}

