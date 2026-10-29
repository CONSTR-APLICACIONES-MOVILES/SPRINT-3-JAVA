package com.example.parchapp.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/* The Google Calendar freeBusy call of the Flutter app (GoogleFreeBusyDataSource), in Java. It reads only busy intervals of the primary calendar: no titles, places or attendees. Blocking, so run it off the main thread. */
public final class GoogleFreeBusyClient {
    private static final String URL_FREE_BUSY = "https://www.googleapis.com/calendar/v3/freeBusy";
    private static final int TIMEOUT_MS = 20_000;

    private GoogleFreeBusyClient() {
    }

    /* timeMin/timeMax are UTC ISO strings; returns {start, end} pairs as Google sends them (RFC 3339). */
    public static List<String[]> busyIntervals(String accessToken, String timeMin, String timeMax) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(URL_FREE_BUSY).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        connection.setRequestProperty("Content-Type", "application/json");
        try {
            String body = new JSONObject()
                    .put("timeMin", timeMin)
                    .put("timeMax", timeMax)
                    .put("timeZone", "America/Bogota")
                    .put("items", new JSONArray().put(new JSONObject().put("id", "primary")))
                    .toString();
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            if (connection.getResponseCode() != 200) {
                throw new IOException("Google Calendar import failed (" + connection.getResponseCode() + ")");
            }
            return parse(read(connection.getInputStream()));
        } catch (JSONException error) {
            throw new IOException("Google Calendar did not return complete availability", error);
        } finally {
            connection.disconnect();
        }
    }

    static List<String[]> parse(String response) throws JSONException, IOException {
        JSONObject calendars = new JSONObject(response).getJSONObject("calendars");
        JSONObject calendar = calendars.optJSONObject("primary");
        if (calendar == null && calendars.length() == 1) {
            calendar = calendars.getJSONObject(calendars.keys().next());
        }
        if (calendar == null || calendar.optJSONArray("errors") != null && calendar.getJSONArray("errors").length() > 0
                || calendar.optJSONArray("busy") == null) {
            throw new IOException("Google Calendar did not return complete availability");
        }
        JSONArray busy = calendar.getJSONArray("busy");
        List<String[]> intervals = new ArrayList<>();
        for (int i = 0; i < busy.length(); i++) {
            JSONObject block = busy.getJSONObject(i);
            intervals.add(new String[]{block.getString("start"), block.getString("end")});
        }
        return intervals;
    }

    private static String read(InputStream in) throws IOException {
        try (InputStream stream = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString("UTF-8");
        }
    }
}
