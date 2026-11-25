package com.example.parchapp.domain.model;

import androidx.annotation.Nullable;

import java.util.Map;

/** One group member as seen from an activity's participant list. */
public final class ActivityParticipant {
    private final String id;
    private final String name;
    private final boolean organizer;
    private final boolean invited;
    @Nullable
    private final String response;

    public ActivityParticipant(String id, String name, boolean organizer, boolean invited,
                               @Nullable String response) {
        this.id = id;
        this.name = name;
        this.organizer = organizer;
        this.invited = invited;
        this.response = response == null || response.trim().isEmpty() ? null : response;
    }

    public static ActivityParticipant from(Map<?, ?> data) {
        Object rawResponse = data.get("response");
        return new ActivityParticipant(
                string(data.get("id")),
                string(data.get("name")),
                Boolean.TRUE.equals(data.get("organizer")),
                Boolean.TRUE.equals(data.get("invited")),
                rawResponse == null ? null : rawResponse.toString());
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public boolean isOrganizer() { return organizer; }
    public boolean isInvited() { return invited; }
    @Nullable public String getResponse() { return response; }

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
