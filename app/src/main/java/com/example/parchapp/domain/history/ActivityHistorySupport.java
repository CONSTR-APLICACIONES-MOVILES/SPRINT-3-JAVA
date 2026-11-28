package com.example.parchapp.domain.history;

import com.example.parchapp.domain.model.PlannedActivity;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ActivityHistorySupport {
    private static final String[] INSTANT_PATTERNS = {
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss"
    };
    private static final String[] LEGACY_PATTERNS = {
            "yyyy-MM-dd h:mm a",
            "yyyy-MM-dd HH:mm",
            "MM/dd/yyyy h:mm a",
            "dd/MM/yyyy HH:mm",
            "dd/MM/yyyy h:mm a"
    };
    private static final double MAX_TILE_LATITUDE = 85.05112878;
    private static final int TILE_ZOOM = 16;

    private ActivityHistorySupport() {
    }

    public static long startsAtMillis(PlannedActivity activity) {
        long startTime = parse(activity.getStartTime(), INSTANT_PATTERNS);
        return startTime > 0L ? startTime
                : parse(safe(activity.getDate()) + " " + safe(activity.getTime()), LEGACY_PATTERNS);
    }

    public static List<PlannedActivity> pastActivities(List<PlannedActivity> activities, long now) {
        List<PlannedActivity> past = new ArrayList<>();
        for (PlannedActivity activity : activities) {
            long startsAt = startsAtMillis(activity);
            if (!activity.isCancelled() && startsAt > 0L && startsAt < now) {
                past.add(activity);
            }
        }
        past.sort((left, right) -> Long.compare(startsAtMillis(right), startsAtMillis(left)));
        return Collections.unmodifiableList(past);
    }

    public static Summary summarize(List<PlannedActivity> activities, long now) {
        Calendar current = Calendar.getInstance();
        current.setTimeInMillis(now);
        Map<String, Integer> places = new HashMap<>();
        Map<String, Integer> times = new HashMap<>();
        int confirmed = 0;
        int thisMonth = 0;

        for (PlannedActivity activity : activities) {
            if (PlannedActivity.STATUS_CONFIRMED.equalsIgnoreCase(activity.getStatus())) {
                confirmed++;
            }
            Calendar startsAt = Calendar.getInstance();
            startsAt.setTimeInMillis(startsAtMillis(activity));
            if (startsAt.get(Calendar.YEAR) == current.get(Calendar.YEAR)
                    && startsAt.get(Calendar.MONTH) == current.get(Calendar.MONTH)) {
                thisMonth++;
            }
            String place = safe(activity.getLocation()).trim();
            if (!place.isEmpty()) {
                places.put(place, places.getOrDefault(place, 0) + 1);
            }
            int hour = startsAt.get(Calendar.HOUR_OF_DAY);
            String time = hour < 12 ? "Morning" : hour < 18 ? "Afternoon" : "Evening";
            times.put(time, times.getOrDefault(time, 0) + 1);
        }
        return new Summary(activities.size(), confirmed, thisMonth,
                mostCommon(places), mostCommon(times));
    }

    public static String mapTileUrl(PlannedActivity activity) {
        Double latitude = activity.getLatitude();
        Double longitude = activity.getLongitude();
        if (latitude == null || longitude == null
                || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90.0 || Math.abs(longitude) > 180.0) {
            return null;
        }
        double lat = Math.max(-MAX_TILE_LATITUDE, Math.min(MAX_TILE_LATITUDE, latitude));
        int scale = 1 << TILE_ZOOM;
        int x = (int) Math.floor((longitude + 180.0) / 360.0 * scale);
        double radians = Math.toRadians(lat);
        int y = (int) Math.floor((1.0 - Math.log(Math.tan(radians)
                + 1.0 / Math.cos(radians)) / Math.PI) / 2.0 * scale);
        x = Math.max(0, Math.min(scale - 1, x));
        y = Math.max(0, Math.min(scale - 1, y));
        return "https://tile.openstreetmap.org/" + TILE_ZOOM + "/" + x + "/" + y + ".png";
    }

    private static long parse(String value, String[] patterns) {
        if (value == null || value.trim().isEmpty()) return -1L;
        String text = value.trim();
        for (String pattern : patterns) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date parsed = format.parse(text, position);
            if (parsed != null && position.getIndex() == text.length()) {
                return parsed.getTime();
            }
        }
        return -1L;
    }

    private static String mostCommon(Map<String, Integer> counts) {
        String best = "";
        int max = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > max) {
                best = entry.getKey();
                max = entry.getValue();
            }
        }
        return best;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    /* The search of the history: a local filter over the activities that are already in memory, so it needs no
     * network. Every word typed must appear in the title or in the place, in any case. An empty search keeps all. */
    public static List<PlannedActivity> filter(List<PlannedActivity> activities, String query) {
        String clean = safe(query).trim().toLowerCase(Locale.ROOT);
        if (clean.isEmpty()) return activities;
        String[] words = clean.split("\\s+");
        List<PlannedActivity> found = new ArrayList<>();
        for (PlannedActivity activity : activities) {
            String text = (safe(activity.getTitle()) + " " + safe(activity.getLocation())).toLowerCase(Locale.ROOT);
            boolean all = true;
            for (String word : words) {
                if (!text.contains(word)) {
                    all = false;
                    break;
                }
            }
            if (all) found.add(activity);
        }
        return found;
    }

    /* The time the offline banner shows. Online, the history was just read, so it is now. Without a network the list
     * comes from the copy on the phone, so the banner must show when that copy was really saved, never now.
     * 0 means the copy has no known date. */
    public static long shownSavedAt(boolean offline, long copySavedAt, long now) {
        if (!offline) return now;
        return Math.max(0L, copySavedAt);
    }

    /* A short date can end with a dot ("p.m."), and the sentence that holds it ends with its own dot. */
    public static String withoutTrailingDot(String text) {
        String clean = safe(text).trim();
        return clean.endsWith(".") ? clean.substring(0, clean.length() - 1).trim() : clean;
    }

    public static final class Summary {
        public final int total;
        public final int confirmed;
        public final int thisMonth;
        public final String favoritePlace;
        public final String favoriteTime;

        private Summary(int total, int confirmed, int thisMonth, String favoritePlace,
                        String favoriteTime) {
            this.total = total;
            this.confirmed = confirmed;
            this.thisMonth = thisMonth;
            this.favoritePlace = favoritePlace;
            this.favoriteTime = favoriteTime;
        }
    }
}
