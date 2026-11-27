package com.example.parchapp.domain.model;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

/** One group member as seen from an activity's participant list. */
public final class ActivityParticipant {
    private final String id;
    private final String name;
    private final boolean organizer;
    private final boolean invited;
    @Nullable
    private final String response;
    @Nullable
    private final String photoUrl;

    public ActivityParticipant(String id, String name, boolean organizer, boolean invited,
                               @Nullable String response) {
        this(id, name, organizer, invited, response, null);
    }

    public ActivityParticipant(String id, String name, boolean organizer, boolean invited,
                               @Nullable String response, @Nullable String photoUrl) {
        this.id = id;
        this.name = name;
        this.organizer = organizer;
        this.invited = invited;
        this.response = response == null || response.trim().isEmpty() ? null : response;
        this.photoUrl = photoUrl == null || photoUrl.trim().isEmpty() ? null : photoUrl;
    }

    public static ActivityParticipant from(Map<?, ?> data) {
        Object rawResponse = data.get("response");
        Object rawPhoto = data.get("photoUrl");
        return new ActivityParticipant(
                string(data.get("id")),
                string(data.get("name")),
                Boolean.TRUE.equals(data.get("organizer")),
                Boolean.TRUE.equals(data.get("invited")),
                rawResponse == null ? null : rawResponse.toString(),
                rawPhoto == null ? null : rawPhoto.toString());
    }

    public static ActivityParticipant fromJson(JSONObject json) {
        return new ActivityParticipant(
                json.optString("id", ""),
                json.optString("name", ""),
                json.optBoolean("organizer", false),
                json.optBoolean("invited", false),
                json.isNull("response") ? null : json.optString("response", null),
                json.isNull("photoUrl") ? null : json.optString("photoUrl", null));
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject()
                .put("id", id)
                .put("name", name)
                .put("organizer", organizer)
                .put("invited", invited)
                .put("response", response == null ? JSONObject.NULL : response)
                .put("photoUrl", photoUrl == null ? JSONObject.NULL : photoUrl);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public boolean isOrganizer() { return organizer; }
    public boolean isInvited() { return invited; }
    @Nullable public String getResponse() { return response; }
    @Nullable public String getPhotoUrl() { return photoUrl; }

    public String getResponseLabel() {
        if (response == null) return "Awaiting response";
        switch (response) {
            case "accepted": return "Accepted";
            case "maybe": return "Maybe";
            case "declined": return "Declined";
            default: return response;
        }
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }
}
