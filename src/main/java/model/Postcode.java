package model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class Postcode {

    private static final String API_URL = "https://api.postcodes.io/postcodes/";
    private static final double EARTH_RADIUS_KM = 6371.0;

    public static int score(String user, String clinic) {
        if (user == null || clinic == null) return 0;

        String u = user.toUpperCase().replaceAll("\\s", "");
        String c = clinic.toUpperCase().replaceAll("\\s", "");

        int len = Math.min(u.length(), c.length());
        int matched = 0;
        for (int i = 0; i < len; i++) {
            if (u.charAt(i) == c.charAt(i)) {
                matched++;
            } else {
                break;
            }
        }
        return matched;
    }

    /**
     * Uses longitude and latitude to find distance between postcodes as the crow flies
     * Reference: <a href="https://www.geeksforgeeks.org/dsa/haversine-formula-to-find-distance-between-two-points-on-a-sphere/">...</a>
     */
    public static double haversine(Coordinate c1, Coordinate c2) {
        double lat1 = c1.lat();
        double lat2 = c2.lat();
        double lon1 = c1.lon();
        double lon2 = c2.lon();

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);

        double a = Math.pow(Math.sin(dLat / 2), 2) +
                Math.pow(Math.sin(dLon / 2), 2) *
                        Math.cos(lat1) *
                        Math.cos(lat2);
        double c = 2 * Math.asin(Math.sqrt(a));
        return EARTH_RADIUS_KM * c;
    }

    public static Coordinate getCoord(String postcode) {
        postcode = postcode.toUpperCase().replaceAll("\\s", "");

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + postcode))
                .GET()
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.body());

            if (root.get("status").asInt() == 200) {
                return new Coordinate(root.get("result").get("latitude").asDouble(), root.get("result").get("longitude").asDouble());
            } else {
                throw new IllegalArgumentException("Invalid postcode");
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

    }
}
