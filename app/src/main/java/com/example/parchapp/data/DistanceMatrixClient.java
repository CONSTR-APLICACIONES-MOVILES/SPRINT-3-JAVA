package com.example.parchapp.data;

import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// f7: asks google maps distance matrix api how many minutes it takes to go from one place to another
// the call runs in another thread so the screen does not freeze
// if maps does not answer in 8 seconds, there is no internet or there is no key, we call onError
// and the screen uses the walking estimate as a backup, this is the timeout and fallback tactic
public class DistanceMatrixClient {
    static final int TIMEOUT_MS = 8000;
    private static final String BASE_URL = "https://maps.googleapis.com/maps/api/distancematrix/json";

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
                String url = BASE_URL + "?origins=" + fromLat + "," + fromLng
                        + "&destinations=" + toLat + "," + toLng + "&mode=walking&key=" + apiKey;
                connection = (HttpURLConnection) new URL(url).openConnection();
// f7: the same limit for connecting and for reading the answer
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);

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

// f7: the answer has rows[0].elements[0].duration.value in seconds, we give it back in minutes
// when google can not find a route the element status is not OK, then we throw so the screen uses the backup
    static int minutesFrom(String json) throws JSONException {
        JSONObject element = new JSONObject(json)
                .getJSONArray("rows").getJSONObject(0)
                .getJSONArray("elements").getJSONObject(0);
        String status = element.optString("status", "OK");
        if (!"OK".equals(status)) {
            throw new JSONException("Google Maps could not find a route: " + status);
        }
        int seconds = element.getJSONObject("duration").getInt("value");
        return Math.max(1, Math.round(seconds / 60f));
    }
}
