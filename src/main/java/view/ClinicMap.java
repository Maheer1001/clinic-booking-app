package view;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.concurrent.Worker;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import model.Clinic;
import model.Coordinate;
import netscape.javascript.JSException;
import netscape.javascript.JSObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Interactive clinic map (Leaflet + OpenStreetMap tiles in a WebView). Needs no API key.
 * Shows a pin per clinic (hover for the name, click for details); after a postcode search it also
 * pins the user and zooms to them and their nearest clinics.
 *
 * <p>A WebView is expensive (its own browser engine, page and tile cache), and the booking flow
 * builds a new clinic search page every time it is opened. All ClinicMap instances therefore share
 * one WebView, which moves into whichever map is currently on screen and redraws that map's pins.
 *
 * <p>Leaflet is loaded from the internet; without a connection the map shows a short message and the
 * clinic list keeps working. It retries the next time a map is shown or when "Try again" is pressed.
 */
public class ClinicMap {

    /** After a search, the view is fitted to the user plus this many nearest clinics. */
    private static final int NEAREST_TO_FIT = 3;

    private static final String HTML = """
        <!doctype html>
        <html><head><meta charset="utf-8">
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
        <script>L_DISABLE_3D = true; // JavaFX WebView misplaces 3D-transformed tiles on HiDPI screens</script>
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        <style>
          html, body, #map { margin: 0; height: 100%; width: 100%; background: #efeaf5; }
          .leaflet-tooltip { font: 600 12px 'Nunito', 'Segoe UI', sans-serif; color: #3b1c5a; }
          .leaflet-popup-content { font: 13px 'Nunito', 'Segoe UI', sans-serif; color: #1e1030; margin: 10px 12px; }
          .leaflet-popup-content b { display: block; font-size: 14px; margin-bottom: 2px; }
          .leaflet-popup-content a { color: #5b2a86; font-weight: 700; cursor: pointer; }
          .leaflet-popup-content-wrapper { border-radius: 10px; }
        </style></head>
        <body><div id="map"></div>
        <script>
          var ready = typeof L !== 'undefined';
          var map, layer, last = null, userMoved = false;
          function esc(s) {
            return String(s == null ? '' : s).replace(/[&<>"']/g, function (ch) {
              return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch];
            });
          }
          if (ready) {
            map = L.map('map', { zoomControl: true, attributionControl: true,
              zoomAnimation: false, fadeAnimation: false, markerZoomAnimation: false, inertia: false });
            L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
              maxZoom: 19, attribution: '&copy; OpenStreetMap contributors'
            }).addTo(map);
            map.setView([50.91, -1.40], 11);
            layer = L.layerGroup().addTo(map);
            // Once the user pans or zooms, a window resize no longer snaps the view back.
            var el = document.getElementById('map');
            el.addEventListener('mousedown', function () { userMoved = true; });
            el.addEventListener('wheel', function () { userMoved = true; });
            el.addEventListener('click', function (e) {
              var t = e.target;
              if (t && t.getAttribute && t.getAttribute('data-clinic') !== null && window.bridge) {
                window.bridge.select(parseInt(t.getAttribute('data-clinic'), 10));
              }
            });
          }
          function fit() {
            if (!last) return;
            var focus = last.focus;
            if (focus.length > 1) { map.fitBounds(focus, { padding: [36, 36], maxZoom: 15, animate: false }); }
            else if (focus.length === 1) { map.setView(focus[0], 14, { animate: false }); }
          }
          // clinics: [{i, lat, lon, name, address}] (nearest first when user is set); user: {lat, lon} or null
          function setMarkers(clinics, user, nearest, selectable) {
            if (!ready) return false;
            map.invalidateSize();
            layer.clearLayers();
            var all = [];
            clinics.forEach(function (c) {
              var html = '<b>' + esc(c.name) + '</b>' + esc(c.address);
              if (selectable) { html += '<br><a data-clinic="' + c.i + '">Choose this clinic</a>'; }
              L.circleMarker([c.lat, c.lon], { radius: 8, color: '#ffffff', weight: 2,
                fillColor: '#6b3fa0', fillOpacity: 1 })
                .bindTooltip(esc(c.name), { direction: 'top', offset: [0, -6] })
                .bindPopup(html, { offset: [0, -4], closeButton: false })
                .addTo(layer);
              all.push([c.lat, c.lon]);
            });
            var focus = all;
            if (user) {
              L.circleMarker([user.lat, user.lon], { radius: 9, color: '#ffffff', weight: 3,
                fillColor: '#e0457b', fillOpacity: 1 })
                .bindTooltip('You', { permanent: true, direction: 'top', offset: [0, -8] }).addTo(layer);
              focus = [[user.lat, user.lon]].concat(all.slice(0, nearest));
            }
            last = { focus: focus };
            userMoved = false;
            fit();
            return true;
          }
          function resized() {
            if (!ready) return;
            map.invalidateSize();
            if (!userMoved) fit();
          }
        </script></body></html>
        """;

    // ---- Shared engine -------------------------------------------------------------------------

    private static WebView sharedWeb;
    private static ClinicMap owner;
    private static final ReadOnlyBooleanWrapper available = new ReadOnlyBooleanWrapper(true);
    private static final ReadOnlyBooleanWrapper loading = new ReadOnlyBooleanWrapper(false);
    private static boolean pageReady;
    /** Held strongly: WebView only keeps a weak reference to objects exposed to JavaScript. */
    private static final Bridge BRIDGE = new Bridge();

    /** Called from JavaScript when "Choose this clinic" is clicked in a popup. Must be public. */
    public static final class Bridge {
        public void select(int index) {
            ClinicMap map = owner;
            if (map == null || map.onClinicSelected == null) return;
            if (index < 0 || index >= map.shown.size()) return;
            Clinic clinic = map.shown.get(index);
            // Leave the JavaScript call stack before running app code (which may open dialogs).
            Platform.runLater(() -> map.onClinicSelected.accept(clinic));
        }
    }

    private static WebView web() {
        if (sharedWeb == null) {
            sharedWeb = new WebView();
            sharedWeb.setContextMenuEnabled(false);
            WebEngine engine = sharedWeb.getEngine();
            // OpenStreetMap's tile policy asks clients to identify themselves.
            engine.setUserAgent(engine.getUserAgent()
                    + " ClinicBookingApp/1.0 (+https://github.com/Maheer1001/clinic-booking-app)");
            engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
                if (state == Worker.State.SUCCEEDED) {
                    onPageLoaded(engine);
                } else if (state == Worker.State.FAILED || state == Worker.State.CANCELLED) {
                    pageReady = false;
                    loading.set(false);
                    available.set(false);
                }
            });
            load();
        }
        return sharedWeb;
    }

    private static void load() {
        pageReady = false;
        loading.set(true);
        sharedWeb.getEngine().loadContent(HTML);
    }

    private static void onPageLoaded(WebEngine engine) {
        loading.set(false);
        boolean leafletLoaded;
        try {
            leafletLoaded = Boolean.TRUE.equals(engine.executeScript("ready"));
            ((JSObject) engine.executeScript("window")).setMember("bridge", BRIDGE);
        } catch (JSException e) {
            leafletLoaded = false;
        }
        pageReady = leafletLoaded;
        available.set(leafletLoaded);
        if (owner != null) owner.redraw();
    }

    /** False while the map library could not be loaded (usually no internet connection). */
    public static ReadOnlyBooleanProperty availableProperty() {
        return available.getReadOnlyProperty();
    }

    // ---- Instance ------------------------------------------------------------------------------

    private final StackPane root = new StackPane();
    private final List<Clinic> shown = new ArrayList<>();
    private String markersScript;
    private Consumer<Clinic> onClinicSelected;

    public ClinicMap(double width, double height) {
        root.setPrefSize(width, height);
        root.setMinHeight(height);
        root.setMaxHeight(height);
        root.getStyleClass().add("clinic-map");

        Rectangle clip = new Rectangle();
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        clip.widthProperty().bind(root.widthProperty());
        clip.heightProperty().bind(root.heightProperty());
        root.setClip(clip);

        root.getChildren().add(offlinePlaceholder());
        attach();

        // Take over the shared WebView whenever this map is (re)displayed, e.g. after Back.
        root.sceneProperty().addListener((obs, old, scene) -> {
            if (scene != null) attach();
        });
        // Leaflet measures its container once; re-measure whenever JavaFX resizes it.
        root.widthProperty().addListener((o, a, b) -> resized());
        root.heightProperty().addListener((o, a, b) -> resized());
    }

    private Node offlinePlaceholder() {
        Label title = new Label("Map unavailable");
        title.getStyleClass().add("clinic-map-offline-title");
        Label body = new Label("Check your internet connection. You can still pick a clinic from the list.");
        body.getStyleClass().add("clinic-map-offline-body");
        body.setWrapText(true);
        Button retry = new Button("Try again");
        retry.getStyleClass().add("clinic-map-retry");
        retry.disableProperty().bind(loading);
        retry.setOnAction(e -> load());

        VBox box = new VBox(6, title, body, retry);
        box.setAlignment(Pos.CENTER);
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        box.setMaxWidth(300);
        StackPane overlay = new StackPane(box);
        overlay.getStyleClass().add("clinic-map-offline");
        overlay.visibleProperty().bind(available.not());
        overlay.managedProperty().bind(overlay.visibleProperty());
        return overlay;
    }

    /** Moves the shared WebView into this map and draws this map's pins. */
    private void attach() {
        WebView web = web();
        if (web.getParent() != root) {
            if (web.getParent() instanceof StackPane previous) {
                previous.getChildren().remove(web);
            }
            root.getChildren().add(0, web);
        }
        boolean wasOwner = owner == this;
        owner = this;
        if (!available.get() && !loading.get()) {
            load(); // offline last time: retry now that the map is being shown again
        } else if (!wasOwner) {
            redraw();
        }
    }

    public Node node() {
        return root;
    }

    /**
     * Called with the clinic whose "Choose this clinic" link was clicked in a map popup.
     * Without a handler the popups show details only.
     */
    public void setOnClinicSelected(Consumer<Clinic> handler) {
        this.onClinicSelected = handler;
        if (markersScript != null) {
            show(new ArrayList<>(shown), lastUser);
        }
    }

    private Coordinate lastUser;

    /**
     * Shows the clinics and, if known, the user's location. With a user location, the clinics
     * should be sorted nearest first: the view zooms to the user and the nearest few.
     */
    public void show(List<Clinic> clinics, Coordinate user) {
        shown.clear();
        lastUser = user;
        StringBuilder js = new StringBuilder("setMarkers([");
        for (Clinic c : clinics) {
            Coordinate p = c.getCoords();
            if (p == null || !Double.isFinite(p.lat()) || !Double.isFinite(p.lon())) continue;
            int index = shown.size();
            shown.add(c);
            js.append(String.format(Locale.ROOT, "{i:%d,lat:%.6f,lon:%.6f,name:%s,address:%s},",
                    index, p.lat(), p.lon(), jsString(c.getName()), jsString(addressLine(c))));
        }
        js.append("],");
        js.append(user == null ? "null" : String.format(Locale.ROOT, "{lat:%.6f,lon:%.6f}", user.lat(), user.lon()));
        js.append(",").append(NEAREST_TO_FIT).append(",").append(onClinicSelected != null).append(");");
        markersScript = js.toString();
        redraw();
    }

    /** Clinics that currently have a pin (those with coordinates), in the order given to {@link #show}. */
    List<Clinic> pinnedClinics() {
        return List.copyOf(shown);
    }

    String markersScript() {
        return markersScript;
    }

    private void redraw() {
        if (owner != this || !pageReady || markersScript == null) return;
        run(markersScript);
    }

    private void resized() {
        if (owner != this || !pageReady) return;
        run("resized();");
    }

    private static void run(String script) {
        try {
            sharedWeb.getEngine().executeScript(script);
        } catch (JSException e) {
            System.err.println("[ClinicMap] Map script failed: " + e.getMessage());
        }
    }

    private static String addressLine(Clinic c) {
        String address = c.getAddress() == null ? "" : c.getAddress().strip();
        String postcode = c.getPostcode() == null ? "" : c.getPostcode().strip();
        if (address.isEmpty()) return postcode;
        if (postcode.isEmpty() || address.contains(postcode)) return address;
        return address + ", " + postcode;
    }

    /** A JavaScript string literal. HTML escaping happens in the page, so names are shown verbatim. */
    static String jsString(String s) {
        if (s == null) return "''";
        StringBuilder out = new StringBuilder(s.length() + 2).append('\'');
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '\\' -> out.append("\\\\");
                case '\'' -> out.append("\\'");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case ' ' -> out.append("\\u2028");
                case ' ' -> out.append("\\u2029");
                default -> {
                    if (ch < 0x20) out.append(String.format("\\u%04x", (int) ch));
                    else out.append(ch);
                }
            }
        }
        return out.append('\'').toString();
    }
}
