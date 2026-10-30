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
    private final int groupSize;
    private final String reminderId;
    private final String reminderPurpose;
    private final String reminderStrategy;
    private final int reminderSequence;
    private final Integer reminderDelayMinutes;
    private final String reminderSentAt;

    public AlertItem(String id, String kind, String title, String description, String createdAt,
                     boolean read, String response, String activityId, String groupId) {
        this(id, kind, title, description, createdAt, read, response, activityId, groupId, 0);
    }

    public AlertItem(String id, String kind, String title, String description, String createdAt,
                     boolean read, String response, String activityId, String groupId, int groupSize) {
        this(id, kind, title, description, createdAt, read, response, activityId, groupId,
                groupSize, null, null, null, 0, null, null);
    }

    public AlertItem(String id, String kind, String title, String description, String createdAt,
                     boolean read, String response, String activityId, String groupId, int groupSize,
                     String reminderId, String reminderPurpose, String reminderStrategy,
                     int reminderSequence, Integer reminderDelayMinutes, String reminderSentAt) {
        this.id = id;
        this.kind = kind;
        this.title = title;
        this.description = description;
        this.createdAt = createdAt;
        this.read = read;
        this.response = response;
        this.activityId = activityId;
        this.groupId = groupId;
        this.groupSize = Math.max(0, groupSize);
        this.reminderId = reminderId;
        this.reminderPurpose = reminderPurpose;
        this.reminderStrategy = reminderStrategy;
        this.reminderSequence = Math.max(0, reminderSequence);
        this.reminderDelayMinutes = reminderDelayMinutes;
        this.reminderSentAt = reminderSentAt;
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
    public int getGroupSize() { return groupSize; }
    public String getReminderId() { return reminderId; }
    public String getReminderPurpose() { return reminderPurpose; }
    public String getReminderStrategy() { return reminderStrategy; }
    public int getReminderSequence() { return reminderSequence; }
    public Integer getReminderDelayMinutes() { return reminderDelayMinutes; }
    public String getReminderSentAt() { return reminderSentAt; }

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
    public boolean isDeliveredReminder() {
        return "reminder".equals(kind) && reminderId != null && !reminderId.trim().isEmpty()
                && reminderStrategy != null && !reminderStrategy.trim().isEmpty()
                && reminderSequence > 0 && reminderSentAt != null && !reminderSentAt.trim().isEmpty();
    }
    public boolean isRsvpReminder() {
        return isDeliveredReminder() && "rsvp".equals(reminderPurpose)
                && reminderDelayMinutes != null && reminderDelayMinutes > 0;
    }
}
