package view;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/** The {@link Navigator#LEAVE_GUARD} hook used by Settings for "Discard your changes?". */
class NavigatorLeaveGuardTest {

    MainView view;
    Navigator nav;

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
        nav.goToPage(new VBox(), "Booking", true);
    }

    private static VBox guarded(Predicate<Runnable> guard) {
        VBox page = new VBox();
        page.getProperties().put(Navigator.LEAVE_GUARD, guard);
        return page;
    }

    @Test
    void pageWithoutGuardCanAlwaysBeLeft() {
        nav.goToPage(new VBox(), "Other", false);
        assertTrue(nav.mayLeave(() -> fail("no retry needed")));
    }

    @Test
    void refusingGuardKeepsThePageOnBack() {
        VBox settings = guarded(retry -> false);
        nav.goToPage(settings, "Settings", false);
        nav.goBack();
        assertSame(settings, nav.getRoot().getCenter());
        nav.goToSettings(); // the gear on Settings means "back" too
        assertSame(settings, nav.getRoot().getCenter());
    }

    @Test
    void refusingGuardKeepsThePageOnTabClick() {
        VBox settings = guarded(retry -> false);
        nav.goToPage(settings, "Settings", false);
        nav.handleTabClick("booking");
        assertSame(settings, nav.getRoot().getCenter());
        assertTrue(view.getBookingButton().isSelected(), "the current tab stays highlighted");
    }

    @Test
    void allowingGuardLeaves() {
        AtomicInteger asked = new AtomicInteger();
        VBox settings = guarded(retry -> { asked.incrementAndGet(); return true; });
        nav.goToPage(settings, "Settings", false);
        nav.goBack();
        assertNotSame(settings, nav.getRoot().getCenter());
        assertEquals(1, asked.get());
    }

    @Test
    void guardCanRepeatTheNavigationLater() {
        // like "Save": stay now, leave once the save has finished
        AtomicReference<Runnable> pending = new AtomicReference<>();
        boolean[] saved = {false};
        VBox settings = guarded(retry -> {
            if (saved[0]) return true;
            pending.set(retry);
            return false;
        });
        nav.goToPage(settings, "Settings", false);
        nav.goBack();
        assertSame(settings, nav.getRoot().getCenter());
        saved[0] = true;
        pending.get().run();
        Node now = nav.getRoot().getCenter();
        assertNotSame(settings, now);
    }
}
