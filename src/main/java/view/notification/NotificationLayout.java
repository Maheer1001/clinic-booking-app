package view.notification;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import view.ui.UI;

/**
 * One layout for every in-app notification popup and preview: the title, "Hi Sam," when there is a
 * name, an optional lead sentence, a card of "Label  value" rows and an optional muted footnote.
 * Before, the booking confirmation used labelled rows while reminders used plain "Date: …" lines.
 */
public final class NotificationLayout {

    private NotificationLayout() {}

    public static HBox detailRow(String label, String value) {
        Label l = new Label(label);
        l.getStyleClass().add(UI.MUTED);
        l.setMinWidth(Region.USE_PREF_SIZE);
        Label v = new Label(value);
        v.getStyleClass().add(UI.BODY);
        v.setWrapText(true);
        v.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(v, Priority.ALWAYS);
        HBox row = new HBox(12, l, v);
        row.setAlignment(Pos.TOP_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    /**
     * True when a value is worth showing. Blank values and the "Clinician" placeholder (used while the
     * clinician is not recorded) are left out, so a preview never reads "Clinician: Clinician".
     */
    static boolean hasValue(String label, String value) {
        if (value == null || value.isBlank()) return false;
        return !(label.equalsIgnoreCase("Clinician") && value.strip().equalsIgnoreCase("Clinician"));
    }

    /** A card of rows from label/value pairs, e.g. {@code detailsCard("Date", d, "Time", t)}; empty values are skipped. */
    public static VBox detailsCard(String... labelValuePairs) {
        VBox card = UI.card(8);
        for (int i = 0; i + 1 < labelValuePairs.length; i += 2) {
            if (hasValue(labelValuePairs[i], labelValuePairs[i + 1])) {
                card.getChildren().add(detailRow(labelValuePairs[i], labelValuePairs[i + 1].strip()));
            }
        }
        return card;
    }

    /**
     * A card listing entries of two lines each: a heading line (e.g. a test type), then a muted detail
     * line (e.g. "20 Nov 2026 · Available"). For lists, where a long heading next to its value would
     * squeeze the value into a narrow column; pairs with a blank heading are left out.
     */
    public static VBox listCard(String... headingDetailPairs) {
        VBox card = UI.card(10);
        for (int i = 0; i + 1 < headingDetailPairs.length; i += 2) {
            String heading = headingDetailPairs[i];
            if (heading == null || heading.isBlank()) continue;
            Label h = new Label(heading.strip());
            h.getStyleClass().add(UI.BODY);
            h.setWrapText(true);
            h.setMaxWidth(Double.MAX_VALUE);
            VBox entry = new VBox(2, h);
            String detail = headingDetailPairs[i + 1];
            if (detail != null && !detail.isBlank()) {
                Label d = new Label(detail.strip());
                d.getStyleClass().add(UI.MUTED);
                d.setWrapText(true);
                d.setMaxWidth(Double.MAX_VALUE);
                entry.getChildren().add(d);
            }
            card.getChildren().add(entry);
        }
        return card;
    }

    /** Plain-text version of {@link #detailsCard} for {@code getMessage()}: one "Label: value" line per row. */
    public static String detailsText(String... labelValuePairs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < labelValuePairs.length; i += 2) {
            if (hasValue(labelValuePairs[i], labelValuePairs[i + 1])) {
                if (!sb.isEmpty()) sb.append('\n');
                sb.append(labelValuePairs[i]).append(": ").append(labelValuePairs[i + 1].strip());
            }
        }
        return sb.toString();
    }

    /**
     * The shared popup body. Any of name, lead, card and footnote may be null/blank and is then left out.
     */
    public static VBox page(String title, String name, String lead, Node card, String footnote) {
        VBox box = new VBox(10, UI.titleSm(title));
        if (name != null && !name.isBlank()) box.getChildren().add(UI.body("Hi " + name.strip() + ","));
        if (lead != null && !lead.isBlank()) box.getChildren().add(UI.body(lead));
        if (card != null && !(card instanceof VBox v && v.getChildren().isEmpty())) box.getChildren().add(card);
        if (footnote != null && !footnote.isBlank()) box.getChildren().add(UI.muted(footnote));
        return box;
    }

    /** A custom message the user wrote for this notification, in the same frame as the default layout. */
    public static VBox custom(String title, String text) {
        Label body = UI.body(text);
        return new VBox(10, UI.titleSm(title), UI.card(10, body));
    }
}
