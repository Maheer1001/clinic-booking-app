package view;

import javafx.concurrent.Worker;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import model.Clinic;
import model.Coordinate;

import java.util.List;
import java.util.Locale;

/**
 * Interactive clinic map (Leaflet + OpenStreetMap tiles in a WebView). Needs no API key.
 * Shows a pin per clinic; after a postcode search it also pins the user and zooms to them
 * and their nearest clinics.
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
          .leaflet-tooltip { font: 600 12px 'Segoe UI', sans-serif; color: #3b1c5a; }
        </style></head>
        <body><div id="map"></div>
        <script>
          var map = L.map('map', { zoomControl: true, attributionControl: true,
            zoomAnimation: false, fadeAnimation: false, markerZoomAnimation: false, inertia: false });
          L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19, attribution: '&copy; OpenStreetMap contributors'
          }).addTo(map);
          map.setView([50.91, -1.40], 11);
          var layer = L.layerGroup().addTo(map);
          // clinics: [{lat, lon, name}] (nearest first when user is set); user: {lat, lon} or null
          function setMarkers(clinics, user, nearest) {
            map.invalidateSize();
            layer.clearLayers();
            var all = [];
            clinics.forEach(function (c) {
              L.circleMarker([c.lat, c.lon], { radius: 8, color: '#ffffff', weight: 2,
                fillColor: '#6b3fa0', fillOpacity: 1 })
                .bindTooltip(c.name, { direction: 'top', offset: [0, -6] }).addTo(layer);
              all.push([c.lat, c.lon]);
            });
            var focus = all;
            if (user) {
              L.circleMarker([user.lat, user.lon], { radius: 9, color: '#ffffff', weight: 3,
                fillColor: '#e0457b', fillOpacity: 1 })
                .bindTooltip('You', { permanent: true, direction: 'top', offset: [0, -8] }).addTo(layer);
              focus = [[user.lat, user.lon]].concat(all.slice(0, nearest));
            }
            if (focus.length > 1) { map.fitBounds(focus, { padding: [36, 36], maxZoom: 15, animate: false }); }
            else if (focus.length === 1) { map.setView(focus[0], 14, { animate: false }); }
          }
        </script></body></html>
        """;

    private final WebView web = new WebView();
    private final WebEngine engine = web.getEngine();
    private final StackPane root = new StackPane(web);
    private boolean loaded;
    private String lastMarkers;

    public ClinicMap(double width, double height) {
        web.setContextMenuEnabled(false);
        web.setPrefSize(width, height);
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

        // OpenStreetMap's tile policy asks clients to identify themselves.
        engine.setUserAgent(engine.getUserAgent()
                + " ClinicBookingApp/1.0 (+https://github.com/Maheer1001/clinic-booking-app)");
        engine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
            if (state == Worker.State.SUCCEEDED) {
                loaded = true;
                refit();
            }
        });
        engine.loadContent(HTML);

        // Leaflet measures its container once; re-measure whenever JavaFX resizes it.
        root.widthProperty().addListener((o, a, b) -> refit());
        root.heightProperty().addListener((o, a, b) -> refit());
    }

    public Node node() {
        return root;
    }

    /**
     * Shows the clinics and, if known, the user's location. With a user location, the clinics
     * should be sorted nearest first: the view zooms to the user and the nearest few.
     */
    public void show(List<Clinic> clinics, Coordinate user) {
        StringBuilder js = new StringBuilder("setMarkers([");
        for (Clinic c : clinics) {
            if (c.getCoords() == null) continue;
            js.append(String.format(Locale.ROOT, "{lat:%.6f,lon:%.6f,name:%s},",
                    c.getCoords().lat(), c.getCoords().lon(), jsString(c.getName())));
        }
        js.append("],");
        js.append(user == null ? "null" : String.format(Locale.ROOT, "{lat:%.6f,lon:%.6f}", user.lat(), user.lon()));
        js.append(",").append(NEAREST_TO_FIT).append(");");
        lastMarkers = js.toString();
        refit();
    }

    private void refit() {
        if (!loaded || lastMarkers == null) return;
        engine.executeScript(lastMarkers);
    }

    private static String jsString(String s) {
        if (s == null) return "''";
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("<", "&lt;") + "'";
    }
}
