package com.example.parchapp.domain.model;

/** Alert payload rendered by the Alerts screen. */
public final class AlertItem {
    private final String id;
    private final String kind;
    private final String title;
    private final String description;
    private final String createdAt;
    private final boolean read;
    private final String response;
    private final String activityId;
    private final String groupId;

    public AlertItem(String id, String kind, String title, String description, String createdAt,
                     boolean read, String response, String activityId, String groupId) {
        this.id = id;
        this.kind = kind;
        this.title = title;
        this.description = description;
        this.createdAt = createdAt;
        this.read = read;
        this.response = response;
        this.activityId = activityId;
        this.groupId = groupId;
    }

    public String getId() { return id; }
    public String getKind() { return kind; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCreatedAt() { return createdAt; }
    public boolean isRead() { return read; }
    public String getResponse() { return response; }
    public String getActivityId() { return activityId; }
    public String getGroupId() { return groupId; }

    public String getCategoryKey() {
        switch (kind) {
            case "invitation": return "invitations";
            case "reminder":
            case "timeChanged": return "schedule";
            case "friendRequest": return "friends";
            case "groupInvite": return "groups";
            default: return "groups";
        }
    }

    public String getPriorityKey() {
        switch (kind) {
            case "invitation":
            case "reminder": return "high";
            case "groupInvite":
            case "timeChanged": return "medium";
            default: return "low";
        }
    }

    public boolean isActivityInvitation() { return "invitation".equals(kind); }
    public boolean isGroupInvitation() { return "groupInvite".equals(kind); }
}
