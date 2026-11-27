package com.example.parchapp.domain.model;

import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Locale;

/* A published university event of Andes Events (the same catalog as the Flutter app). Times are instants in
 * milliseconds; the screen shows them in Bogota. Only the backend decides what is published and valid. */
public final class AndesEvent {
    public static final String[] CATEGORIES = {"academic", "cultural", "sports", "other"};

    private final String id;
    private final String title;
    private final String description;
    private final String category;
    private final String location;
    private final long start;
    private final long end;
    @Nullable
    private final String sourceUrl;

    public AndesEvent(String id, String title, String description, String category, String location,
                      long start, long end, @Nullable String sourceUrl) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.category = normalizeCategory(category);
        this.location = location;
        this.start = start;
        this.end = end;
        this.sourceUrl = sourceUrl;
    }

    /* academic, cultural and sports stay, anything else is "other" (like the backend and Flutter) */
    public static String normalizeCategory(@Nullable String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.US);
        return Arrays.asList("academic", "cultural", "sports").contains(value) ? value : "other";
    }

    /* the complete data a card needs; anything missing means the event can not be shown or added */
    public boolean isValid() {
        return !id.isEmpty() && !title.trim().isEmpty() && !description.trim().isEmpty()
                && !location.trim().isEmpty() && end > start;
    }

    public boolean hasEnded(long now) {
        return end <= now;
    }

    /* "Academic" for "academic" */
    public String categoryLabel() {
        return category.substring(0, 1).toUpperCase(Locale.US) + category.substring(1);
    }

    public boolean matches(String query) {
        String q = query.trim().toLowerCase(Locale.getDefault());
        return q.isEmpty() || title.toLowerCase(Locale.getDefault()).contains(q)
                || description.toLowerCase(Locale.getDefault()).contains(q)
                || location.toLowerCase(Locale.getDefault()).contains(q);
    }

    /* the time or the place changed: an add sent later must not add something different from what was shown */
    public boolean sameTimeAndPlace(AndesEvent other) {
        return start == other.start && end == other.end && location.equals(other.location);
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getCategory() {
        return category;
    }

    public String getLocation() {
        return location;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }

    @Nullable
    public String getSourceUrl() {
        return sourceUrl;
    }
}
