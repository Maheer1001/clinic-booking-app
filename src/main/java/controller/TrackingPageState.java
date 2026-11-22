package controller;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.DatePicker;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * What the user has set on a Tracking list page: search text, sort, From/To dates and how far the
 * list is scrolled. Captured from the old page and put onto a freshly built one, so reloading the
 * data does not reset what the user chose. Every Tracking list page has one search field, one sort
 * box and a From then a To date picker; an empty-state page has none and captures as {@link #NONE}.
 */
record TrackingPageState(String search, String sort, LocalDate from, LocalDate to, double scroll) {

    static final TrackingPageState NONE = new TrackingPageState("", null, null, null, 0);

    /** Reads the state off a list page (or anything containing one). */
    static TrackingPageState capture(Node page) {
        Controls c = Controls.find(page);
        return new TrackingPageState(
                c.search == null || c.search.getText() == null ? "" : c.search.getText(),
                c.sort == null ? null : c.sort.getValue(),
                c.dates.size() > 0 ? c.dates.get(0).getValue() : null,
                c.dates.size() > 1 ? c.dates.get(1).getValue() : null,
                c.scroll == null ? 0 : c.scroll.getVvalue());
    }

    /** Sets this state on a freshly built page; its own listeners then filter and sort the new data. */
    void applyTo(Node page) {
        Controls c = Controls.find(page);
        if (c.search != null) c.search.setText(search);
        if (c.sort != null && sort != null && c.sort.getItems().contains(sort)) c.sort.setValue(sort);
        if (c.dates.size() > 0) c.dates.get(0).setValue(from);
        if (c.dates.size() > 1) c.dates.get(1).setValue(to);
        if (c.scroll != null) c.scroll.setVvalue(scroll);
    }

    /** The page's own controls, found by walking it (not into the internals of other controls). */
    private static final class Controls {
        TextField search;
        ComboBox<String> sort;
        final List<DatePicker> dates = new ArrayList<>();
        ScrollPane scroll;

        static Controls find(Node page) {
            Controls c = new Controls();
            c.walk(page);
            return c;
        }

        @SuppressWarnings("unchecked")
        private void walk(Node node) {
            if (node == null) return;
            if (node instanceof ScrollPane sp) {
                if (scroll == null) scroll = sp;
                walk(sp.getContent());
            } else if (node instanceof DatePicker dp) {
                dates.add(dp);
            } else if (node instanceof ComboBox<?> cb) {
                if (sort == null) sort = (ComboBox<String>) cb;
            } else if (node instanceof TextField tf) {
                if (search == null) search = tf;
            } else if (node instanceof Control) {
                // labels, buttons...: their children are skin internals, not page content
            } else if (node instanceof Parent p) {
                for (Node child : p.getChildrenUnmodifiable()) walk(child);
            }
        }
    }
}
