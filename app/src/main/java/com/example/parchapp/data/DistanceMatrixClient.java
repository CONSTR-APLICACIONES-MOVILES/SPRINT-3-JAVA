package com.example.parchapp.data;

import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// f7: asks the google routes api (computeRouteMatrix) how many minutes it takes to go from one place to another
// the legacy distance matrix api can not be enabled on new projects, routes api is its replacement
// the call runs in another thread so the screen does not freeze
// if maps does not answer in 8 seconds, there is no internet or there is no key, we call onError
// and the screen uses the walking estimate as a backup, this is the timeout and fallback tactic
public class DistanceMatrixClient {
    static final int TIMEOUT_MS = 8000;
    private static final String BASE_URL = "https://routes.googleapis.com/distanceMatrix/v2:computeRouteMatrix";
    // f7: routes api only sends back the fields we ask for
    private static final String FIELD_MASK = "originIndex,destinationIndex,duration,condition,status";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final String apiKey;

    public DistanceMatrixClient(String apiKey) {
        this.apiKey = apiKey;
    }

// f7: true when there is a key, without it we do not call google
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }

    public void getTravelMinutes(double fromLat, double fromLng, double toLat, double toLng,
                                 Callback<Integer> callback) {
        if (!isConfigured()) {
            MainThread.post(() -> callback.onError(new IllegalStateException("Missing MAPS_API_KEY")));
            return;
        }
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                byte[] request = requestBody(fromLat, fromLng, toLat, toLng).getBytes(StandardCharsets.UTF_8);
                connection = (HttpURLConnection) new URL(BASE_URL).openConnection();
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("X-Goog-Api-Key", apiKey);
                connection.setRequestProperty("X-Goog-FieldMask", FIELD_MASK);
// f7: the same limit for connecting and for reading the answer
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(request);
                }

                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        body.append(line);
                    }
                }
                int minutes = minutesFrom(body.toString());
                MainThread.post(() -> callback.onSuccess(minutes));
            } catch (Exception e) {
                MainThread.post(() -> callback.onError(e));
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

// f7: one origin and one destination, walking, like before
    static String requestBody(double fromLat, double fromLng, double toLat, double toLng) throws JSONException {
        return new JSONObject()
                .put("origins", new JSONArray().put(waypoint(fromLat, fromLng)))
                .put("destinations", new JSONArray().put(waypoint(toLat, toLng)))
                .put("travelMode", "WALK")
                .toString();
    }

    private static JSONObject waypoint(double lat, double lng) throws JSONException {
        JSONObject latLng = new JSONObject().put("latitude", lat).put("longitude", lng);
        return new JSONObject().put("waypoint", new JSONObject().put("location", new JSONObject().put("latLng", latLng)));
    }

// f7: the answer is a list with one element, its duration is text like "750s", we give it back in minutes
// when google can not find a route the condition is not ROUTE_EXISTS, then we throw so the screen uses the backup
    static int minutesFrom(String json) throws JSONException {
        JSONObject element = new JSONArray(json).getJSONObject(0);
        String condition = element.optString("condition", "ROUTE_EXISTS");
        if (!"ROUTE_EXISTS".equals(condition) || !element.has("duration")) {
            throw new JSONException("Google Maps could not find a route: " + condition);
        }
        String duration = element.getString("duration");
        double seconds = Double.parseDouble(duration.endsWith("s") ? duration.substring(0, duration.length() - 1) : duration);
        return Math.max(1, (int) Math.round(seconds / 60d));
    }
}
