package com.afrochow.security.Utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
@Component
public class GeocodingService {

    private static final String NOMINATIM_URL =
            "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Geocode a full address string to lat/lng.
     * Returns null if geocoding fails or no result found.
     */
    public double[] geocode(String formattedAddress) {
        try {
            String encoded = java.net.URLEncoder.encode(formattedAddress,
                    java.nio.charset.StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(NOMINATIM_URL + encoded))
                    .header("User-Agent", "Afrochow/1.0")
                    .header("Accept-Language", "en")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            JsonNode results = objectMapper.readTree(response.body());
            if (results.isArray() && results.size() > 0) {
                JsonNode first = results.get(0);
                double lat = first.get("lat").asDouble();
                double lon = first.get("lon").asDouble();
                return new double[]{ lat, lon };
            }
        } catch (Exception e) {
            log.warn("Geocoding failed for address: {}", formattedAddress, e);
        }
        return null;
    }
}