package com.example.parchapp.domain.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Backend response for the activity participants screen. */
public final class ActivityParticipants {
    private final String activityId;
    private final String groupId;
    private final String version;
    private final boolean canManage;
    private final boolean canJoin;
    private final boolean joined;
    private final boolean canInviteFriends;
    private final boolean cancelled;
    private final List<ActivityParticipant> members;

    public ActivityParticipants(String activityId, String groupId, String version, boolean canManage,
                                boolean canJoin, boolean joined, boolean canInviteFriends,
                                boolean cancelled, List<ActivityParticipant> members) {
        this.activityId = activityId;
        this.groupId = groupId;
        this.version = version;
        this.canManage = canManage;
        this.canJoin = canJoin;
        this.joined = joined;
        this.canInviteFriends = canInviteFriends;
        this.cancelled = cancelled;
        this.members = Collections.unmodifiableList(new ArrayList<>(members));
    }

    public static ActivityParticipants from(Map<?, ?> data) {
        List<ActivityParticipant> members = new ArrayList<>();
        Object rawMembers = data.get("members");
        if (rawMembers instanceof List) {
            for (Object raw : (List<?>) rawMembers) {
                if (raw instanceof Map) members.add(ActivityParticipant.from((Map<?, ?>) raw));
            }
        }
        return new ActivityParticipants(
                string(data.get("activityId")),
                string(data.get("groupId")),
                string(data.get("version")),
                Boolean.TRUE.equals(data.get("canManage")),
                Boolean.TRUE.equals(data.get("canJoin")),
                Boolean.TRUE.equals(data.get("joined")),
                Boolean.TRUE.equals(data.get("canInviteFriends")),
                Boolean.TRUE.equals(data.get("cancelled")),
                members);
    }

    public String toJson() throws JSONException {
        JSONArray array = new JSONArray();
        for (ActivityParticipant member : members) array.put(member.toJson());
        return new JSONObject()
                .put("activityId", activityId)
                .put("groupId", groupId)
                .put("version", version)
                .put("canManage", canManage)
                .put("canJoin", canJoin)
                .put("joined", joined)
                .put("canInviteFriends", canInviteFriends)
                .put("cancelled", cancelled)
                .put("members", array)
                .toString();
    }

    public static ActivityParticipants fromJson(String text) {
        try {
            JSONObject json = new JSONObject(text);
            List<ActivityParticipant> members = new ArrayList<>();
            JSONArray array = json.optJSONArray("members");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject raw = array.optJSONObject(i);
                    if (raw != null) members.add(ActivityParticipant.fromJson(raw));
                }
            }
            return new ActivityParticipants(
                    json.optString("activityId", ""),
                    json.optString("groupId", ""),
                    json.optString("version", ""),
                    json.optBoolean("canManage", false),
                    json.optBoolean("canJoin", false),
                    json.optBoolean("joined", false),
                    json.optBoolean("canInviteFriends", false),
                    json.optBoolean("cancelled", false),
                    members);
        } catch (JSONException error) {
            return null;
        }
    }

    public String getActivityId() { return activityId; }
    public String getGroupId() { return groupId; }
    public String getVersion() { return version; }
    public boolean canManage() { return canManage; }
    public boolean canJoin() { return canJoin; }
    public boolean isJoined() { return joined; }
    public boolean canInviteFriends() { return canInviteFriends; }
    public boolean isCancelled() { return cancelled; }
    public List<ActivityParticipant> getMembers() { return members; }

    public boolean containsMember(String userId) {
        for (ActivityParticipant member : members) {
            if (member.getId().equals(userId)) return true;
        }
        return false;
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }
}
