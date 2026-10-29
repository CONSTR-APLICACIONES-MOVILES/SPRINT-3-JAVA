package com.example.parchapp.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable recommendations returned for one activity and one server-ranked batch. */
public final class SlotRecommendationBatch {
    private final String id;
    private final String activityId;
    private final String date;
    private final String timeZone;
    private final List<RecommendedSlot> slots;

    public SlotRecommendationBatch(String id, String activityId, String date,
                                   String timeZone, List<RecommendedSlot> slots) {
        if (id == null || id.trim().isEmpty() || activityId == null || activityId.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid recommendation batch.");
        }
        this.id = id;
        this.activityId = activityId;
        this.date = date == null ? "" : date;
        this.timeZone = timeZone == null ? "America/Bogota" : timeZone;
        this.slots = Collections.unmodifiableList(new ArrayList<>(slots == null
                ? Collections.emptyList() : slots));
    }

    public String getId() { return id; }
    public String getActivityId() { return activityId; }
    public String getDate() { return date; }
    public String getTimeZone() { return timeZone; }
    public List<RecommendedSlot> getSlots() { return slots; }
}
