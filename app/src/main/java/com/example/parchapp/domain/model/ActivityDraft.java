package com.example.parchapp.domain.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Fields of an incomplete activity creation that can be continued later. */
public final class ActivityDraft {
    private final String groupId;
    private final String title;
    private final String description;
    private final String category;
    private final String location;
    private final String status;
    private final String date;
    private final String time;
    private final String startsAt;
    private final String endsAt;
    private final String requestId;
    private final String comparisonId;
    private final List<String> comparedIds;
    private final String comparedDate;
    private final int slotPosition;

    public ActivityDraft(String groupId, String title, String description, String category,
                         String location, String status, String date, String time,
                         String startsAt, String endsAt, String requestId,
                         String comparisonId, List<String> comparedIds, String comparedDate,
                         int slotPosition) {
        this.groupId = safe(groupId);
        this.title = safe(title);
        this.description = safe(description);
        this.category = category == null || category.trim().isEmpty() ? "other" : category;
        this.location = safe(location);
        this.status = status == null || status.trim().isEmpty()
                ? PlannedActivity.STATUS_PROPOSED : status;
        this.date = safe(date);
        this.time = safe(time);
        this.startsAt = blankToNull(startsAt);
        this.endsAt = blankToNull(endsAt);
        this.requestId = blankToNull(requestId);
        this.comparisonId = blankToNull(comparisonId);
        this.comparedIds = comparedIds == null ? new ArrayList<>() : new ArrayList<>(comparedIds);
        this.comparedDate = safe(comparedDate);
        this.slotPosition = slotPosition;
    }

    public String getGroupId() { return groupId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public String getLocation() { return location; }
    public String getStatus() { return status; }
    public String getDate() { return date; }
    public String getTime() { return time; }
    public String getStartsAt() { return startsAt; }
    public String getEndsAt() { return endsAt; }
    public String getRequestId() { return requestId; }
    public String getComparisonId() { return comparisonId; }
    public List<String> getComparedIds() { return new ArrayList<>(comparedIds); }
    public String getComparedDate() { return comparedDate; }
    public int getSlotPosition() { return slotPosition; }

    public boolean hasContent() {
        return !title.trim().isEmpty() || !date.trim().isEmpty() || !time.trim().isEmpty()
                || !location.trim().isEmpty();
    }

    public Map<String, Object> toRemotePayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("groupId", groupId);
        payload.put("title", title);
        payload.put("description", description);
        payload.put("category", category);
        payload.put("location", location);
        payload.put("status", status);
        payload.put("date", date);
        payload.put("time", time);
        payload.put("measurementVersion", 1);
        if (requestId != null) payload.put("requestId", requestId);
        if (startsAt != null) payload.put("startsAt", startsAt);
        if (endsAt != null) payload.put("endsAt", endsAt);
        if (comparisonId != null) {
            Map<String, Object> comparison = new HashMap<>();
            comparison.put("comparisonId", comparisonId);
            comparison.put("participantIds", new ArrayList<>(comparedIds));
            comparison.put("date", comparedDate);
            payload.put("comparison", comparison);
        }
        return payload;
    }

    public String toJson() throws JSONException {
        return new JSONObject()
                .put("groupId", groupId)
                .put("title", title)
                .put("description", description)
                .put("category", category)
                .put("location", location)
                .put("status", status)
                .put("date", date)
                .put("time", time)
                .put("startsAt", startsAt == null ? JSONObject.NULL : startsAt)
                .put("endsAt", endsAt == null ? JSONObject.NULL : endsAt)
                .put("requestId", requestId == null ? JSONObject.NULL : requestId)
                .put("comparisonId", comparisonId == null ? JSONObject.NULL : comparisonId)
                .put("comparedIds", new JSONArray(comparedIds))
                .put("comparedDate", comparedDate)
                .put("slotPosition", slotPosition)
                .toString();
    }

    public static ActivityDraft fromJson(String text) {
        try {
            return fromJson(new JSONObject(text));
        } catch (JSONException error) {
            return null;
        }
    }

    public static ActivityDraft fromJson(JSONObject json) {
        List<String> ids = new ArrayList<>();
        JSONArray array = json.optJSONArray("comparedIds");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                String id = array.optString(i, null);
                if (id != null) ids.add(id);
            }
        }
        return new ActivityDraft(
                json.optString("groupId", ""),
                json.optString("title", ""),
                json.optString("description", ""),
                json.optString("category", "other"),
                json.optString("location", ""),
                json.optString("status", PlannedActivity.STATUS_PROPOSED),
                json.optString("date", ""),
                json.optString("time", ""),
                json.isNull("startsAt") ? null : json.optString("startsAt", null),
                json.isNull("endsAt") ? null : json.optString("endsAt", null),
                json.isNull("requestId") ? null : json.optString("requestId", null),
                json.isNull("comparisonId") ? null : json.optString("comparisonId", null),
                ids,
                json.optString("comparedDate", ""),
                json.optInt("slotPosition", -1));
    }

    public static ActivityDraft fromRemotePayload(Map<?, ?> payload) {
        Map<?, ?> comparison = payload.get("comparison") instanceof Map
                ? (Map<?, ?>) payload.get("comparison") : null;
        List<String> ids = new ArrayList<>();
        if (comparison != null && comparison.get("participantIds") instanceof List) {
            for (Object id : (List<?>) comparison.get("participantIds")) {
                if (id != null) ids.add(id.toString());
            }
        }
        return new ActivityDraft(
                string(payload.get("groupId")),
                string(payload.get("title")),
                string(payload.get("description")),
                string(payload.get("category")),
                string(payload.get("location")),
                string(payload.get("status")),
                string(payload.get("date")),
                string(payload.get("time")),
                nullable(payload.get("startsAt")),
                nullable(payload.get("endsAt")),
                nullable(payload.get("requestId")),
                comparison == null ? null : nullable(comparison.get("comparisonId")),
                ids,
                comparison == null ? "" : string(comparison.get("date")),
                -1);
    }

    public PlannedActivity toPlannedActivity(int groupSize) {
        PlannedActivity activity = new PlannedActivity(null, groupId, title, category, date, time,
                location, status, System.currentTimeMillis(), groupSize, false);
        return startsAt == null || endsAt == null ? activity : activity.withSchedule(startsAt, endsAt);
    }

    public ComparisonEvidence evidenceFor(String myId) {
        if (comparisonId != null) {
            return comparedIds.isEmpty() || myId == null
                    ? null : ComparisonEvidence.of(comparisonId, myId, comparedIds, comparedDate);
        }
        return slotPosition >= 0 ? null : ComparisonEvidence.direct();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String nullable(Object value) {
        return value == null ? null : value.toString();
    }
}
