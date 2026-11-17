package controller;

import javafx.scene.control.ScrollPane;

/**
 * Calls back once the user has seen the whole content of a scroll pane: either by scrolling
 * to the bottom, or immediately if the content is short enough to fit without scrolling
 * (in which case the scroll position never changes and a pure scroll listener would never fire).
 */
final class ReadToEndTracker {

    private ReadToEndTracker() {}

    static void onReadToEnd(ScrollPane scrollPane, Runnable onRead) {
        boolean[] fired = {false};
        Runnable check = () -> {
            if (fired[0]) return;
            if (reachedEnd(scrollPane)) {
                fired[0] = true;
                onRead.run();
            }
        };
        scrollPane.vvalueProperty().addListener((obs, o, v) -> check.run());
        scrollPane.viewportBoundsProperty().addListener((obs, o, v) -> check.run());
        if (scrollPane.getContent() != null) {
            scrollPane.getContent().layoutBoundsProperty().addListener((obs, o, v) -> check.run());
        }
    }

    private static boolean reachedEnd(ScrollPane sp) {
        if (sp.getVvalue() >= sp.getVmax() - 0.001) return true;
        if (sp.getContent() == null) return false;
        double viewport = sp.getViewportBounds().getHeight();
        double content = sp.getContent().getLayoutBounds().getHeight();
        return viewport > 0 && content > 0 && content <= viewport + 1;
    }
}
