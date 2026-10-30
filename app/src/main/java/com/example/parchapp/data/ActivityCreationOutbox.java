package com.example.parchapp.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.parchapp.domain.model.ComparisonEvidence;
import com.example.parchapp.domain.model.PlannedActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Persistent, user-scoped outbox for activity publishes waiting on the backend. */
public final class ActivityCreationOutbox {
    static final int MAX_PENDING = 20;
    private static final String PREFS = "parchapp_activity_creation_outbox";
    private static final String KEY_RECORDS = "records_v1";

    interface Store {
        String read();
        boolean write(String serialized);
    }

    private final Store store;

    public ActivityCreationOutbox(Context context) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        store = new Store() {
            @Override public String read() { return preferences.getString(KEY_RECORDS, "[]"); }
            @Override public boolean write(String value) {
                return preferences.edit().putString(KEY_RECORDS, value).commit();
            }
        };
    }

    ActivityCreationOutbox(Store store) { this.store = store; }

    public synchronized boolean save(PendingActivityCreation record) {
        if (record == null || blank(record.getUserId()) || blank(record.getRequestId())
                || record.getActivity() == null) return false;
        List<PendingActivityCreation> records = readAll();
        boolean replacing = records.removeIf(existing -> existing.getUserId().equals(record.getUserId())
                && existing.getRequestId().equals(record.getRequestId()));
        long userRecordCount = records.stream().filter(existing -> existing.getUserId().equals(record.getUserId())).count();
        if (!replacing && userRecordCount >= MAX_PENDING) return false;
        records.add(record);
        return store.write(serialize(records));
    }

    public synchronized PendingActivityCreation get(String userId, String requestId) {
        for (PendingActivityCreation record : readAll()) {
            if (record.getUserId().equals(userId) && record.getRequestId().equals(requestId)) return record;
        }
        return null;
    }

    public synchronized List<PendingActivityCreation> forUser(String userId) {
        List<PendingActivityCreation> matching = new ArrayList<>();
        for (PendingActivityCreation record : readAll()) {
            if (record.getUserId().equals(userId)) matching.add(record);
        }
        return Collections.unmodifiableList(matching);
    }

    public synchronized boolean update(PendingActivityCreation updated) {
        if (updated == null) return false;
        List<PendingActivityCreation> records = readAll();
        for (int i = 0; i < records.size(); i++) {
            PendingActivityCreation existing = records.get(i);
            if (existing.getUserId().equals(updated.getUserId())
                    && existing.getRequestId().equals(updated.getRequestId())) {
                records.set(i, updated);
                return store.write(serialize(records));
            }
        }
        return false;
    }

    public synchronized boolean remove(String userId, String requestId) {
        List<PendingActivityCreation> records = readAll();
        boolean removed = records.removeIf(record -> record.getUserId().equals(userId)
                && record.getRequestId().equals(requestId));
        return !removed || store.write(serialize(records));
    }

    public synchronized void recoverInterrupted(String userId) {
        List<PendingActivityCreation> records = readAll();
        boolean changed = false;
        for (int i = 0; i < records.size(); i++) {
            PendingActivityCreation record = records.get(i);
            if (record.getUserId().equals(userId)
                    && record.getState() == PendingActivityCreation.State.SYNCING) {
                records.set(i, record.withState(PendingActivityCreation.State.PENDING,
                        "Retrying the saved activity…", null, record.getRecommendationOutcome()));
                changed = true;
            }
        }
        if (changed) store.write(serialize(records));
    }

    private List<PendingActivityCreation> readAll() {
        List<PendingActivityCreation> records = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(store.read());
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                PendingActivityCreation record = decode(item);
                if (record != null) records.add(record);
            }
        } catch (JSONException ignored) {
            // A corrupt queue must not prevent app startup or unrelated features.
        }
        return records;
    }

    private String serialize(List<PendingActivityCreation> records) {
        JSONArray array = new JSONArray();
        for (PendingActivityCreation record : records) {
            JSONObject row = encode(record);
            if (row != null) array.put(row);
        }
        return array.toString();
    }

    private JSONObject encode(PendingActivityCreation record) {
        try {
            PlannedActivity activity = record.getActivity();
            JSONObject row = new JSONObject();
            row.put("userId", record.getUserId());
            row.put("requestId", record.getRequestId());
            row.put("creationSessionId", record.getCreationSessionId());
            row.put("attemptStartedAtMillis", record.getAttemptStartedAtMillis());
            row.put("slotPosition", record.getSlotPosition());
            row.put("durationMinutes", record.getDurationMinutes());
            row.put("selectedStartMillis", record.getSelectedStartMillis());
            row.put("selectedEndMillis", record.getSelectedEndMillis());
            int[] shownStarts = record.getShownStartMinutes();
            if (shownStarts != null) {
                JSONArray starts = new JSONArray();
                for (int start : shownStarts) starts.put(start);
                row.put("shownStartMinutes", starts);
            }
            row.put("flowId", record.getFlowId());
            row.put("flowStartedAtMillis", record.getFlowStartedAtMillis());
            row.put("flowSteps", new JSONArray(record.getFlowSteps()));
            row.put("state", record.getState().name());
            row.put("message", record.getMessage());
            row.put("createdActivityId", record.getCreatedActivityId());
            row.put("recommendationOutcome", record.getRecommendationOutcome());
            JSONObject plan = new JSONObject();
            plan.put("groupId", activity.getGroupId());
            plan.put("title", activity.getTitle());
            plan.put("category", activity.getCategory());
            plan.put("date", activity.getDate());
            plan.put("time", activity.getTime());
            plan.put("location", activity.getLocation());
            plan.put("status", activity.getStatus());
            plan.put("createdAt", activity.getCreatedAt());
            plan.put("groupSizeAtCreation", activity.getGroupSizeAtCreation());
            plan.put("comparisonUsed", activity.isComparisonUsed());
            plan.put("startTime", activity.getStartTime());
            plan.put("endTime", activity.getEndTime());
            plan.put("locationAreaId", activity.getLocationAreaId());
            plan.put("createdBy", activity.getCreatedBy());
            plan.put("reminderMinutesBefore", activity.getReminderMinutesBefore());
            row.put("activity", plan);
            ComparisonEvidence evidence = record.getComparisonEvidence();
            if (evidence != null && evidence.isComparison()) {
                row.put("comparisonId", evidence.getId());
                row.put("comparisonDate", evidence.getDate());
                row.put("comparisonParticipants", new JSONArray(evidence.getParticipantIds()));
            }
            return row;
        } catch (JSONException error) {
            return null;
        }
    }

    private PendingActivityCreation decode(JSONObject row) {
        String userId = row.optString("userId", "");
        String requestId = row.optString("requestId", "");
        JSONObject rawPlan = row.optJSONObject("activity");
        if (blank(userId) || blank(requestId) || rawPlan == null) return null;
        try {
            Integer reminder = rawPlan.isNull("reminderMinutesBefore") ? null
                    : rawPlan.optInt("reminderMinutesBefore");
            PlannedActivity activity = new PlannedActivity(null,
                    rawPlan.optString("groupId", ""), rawPlan.optString("title", ""),
                    rawPlan.optString("category", "other"), rawPlan.optString("date", ""),
                    rawPlan.optString("time", ""), rawPlan.optString("location", ""),
                    rawPlan.optString("status", PlannedActivity.STATUS_PROPOSED),
                    rawPlan.optLong("createdAt"), rawPlan.optInt("groupSizeAtCreation"),
                    rawPlan.optBoolean("comparisonUsed"), rawPlan.optString("startTime", ""),
                    rawPlan.optString("endTime", ""), rawPlan.optString("locationAreaId", "unknown"),
                    rawPlan.optString("createdBy", ""), reminder);
            ComparisonEvidence evidence = decodeEvidence(row, userId);
            PendingActivityCreation.State state;
            try {
                state = PendingActivityCreation.State.valueOf(row.optString("state", "PENDING"));
            } catch (IllegalArgumentException error) {
                state = PendingActivityCreation.State.PENDING;
            }
            return new PendingActivityCreation(userId, requestId,
                    row.optString("creationSessionId", ""), row.optLong("attemptStartedAtMillis"),
                    activity, evidence, row.optInt("slotPosition", -1), row.optInt("durationMinutes"),
                    row.optLong("selectedStartMillis"), row.optLong("selectedEndMillis"),
                    integers(row.optJSONArray("shownStartMinutes")),
                    row.optString("flowId", ""), row.optLong("flowStartedAtMillis"),
                    strings(row.optJSONArray("flowSteps")), state, row.optString("message", ""),
                    row.isNull("createdActivityId") ? null : row.optString("createdActivityId"),
                    row.optString("recommendationOutcome", ""));
        } catch (RuntimeException | JSONException error) {
            return null;
        }
    }

    private ComparisonEvidence decodeEvidence(JSONObject row, String userId) throws JSONException {
        String comparisonId = row.optString("comparisonId", "");
        if (comparisonId.isEmpty()) return null;
        List<String> friendIds = new ArrayList<>();
        JSONArray rawParticipants = row.optJSONArray("comparisonParticipants");
        if (rawParticipants != null) {
            for (int i = 0; i < rawParticipants.length(); i++) {
                String participantId = rawParticipants.optString(i, "");
                if (!participantId.isEmpty() && !userId.equals(participantId)) friendIds.add(participantId);
            }
        }
        return ComparisonEvidence.of(comparisonId, userId, friendIds, row.optString("comparisonDate", null));
    }

    private static List<String> strings(JSONArray array) {
        if (array == null) return Collections.emptyList();
        List<String> values = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "");
            if (!value.isEmpty()) values.add(value);
        }
        return values;
    }

    private static int[] integers(JSONArray array) {
        if (array == null) return null;
        int[] values = new int[array.length()];
        for (int i = 0; i < array.length(); i++) values[i] = array.optInt(i);
        return values;
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
