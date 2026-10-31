package com.example.parchapp.domain.model;

/** Activity detail response from the authenticated backend callable. */
public final class ActivityDetails {
    private final String id;
    private final String groupId;
    private final String title;
    private final String category;
    private final String date;
    private final String time;
    private final String location;
    private final String status;
    private final String startTime;
    private final String endTime;
    private final Integer reminderMinutesBefore;
    private final String createdBy;
    private final int totalParticipants;
    private final int acceptedParticipants;
    private final Integer durationMinutes;
    private final boolean cancelled;

    public ActivityDetails(String id, String groupId, String title, String category, String date,
                           String time, String location, String status, String startTime,
                           String endTime, Integer reminderMinutesBefore) {
        this(id, groupId, title, category, date, time, location, status, startTime, endTime,
                reminderMinutesBefore, "");
    }

    public ActivityDetails(String id, String groupId, String title, String category, String date,
                           String time, String location, String status, String startTime,
                           String endTime, Integer reminderMinutesBefore, String createdBy) {
        this(id, groupId, title, category, date, time, location, status, startTime, endTime,
                reminderMinutesBefore, createdBy, null, false, 0, 0);
    }

    public ActivityDetails(String id, String groupId, String title, String category, String date,
                           String time, String location, String status, String startTime,
                           String endTime, Integer reminderMinutesBefore, String createdBy,
                           Integer durationMinutes, boolean cancelled,
                           int totalParticipants, int acceptedParticipants) {
        this.id = id;
        this.groupId = groupId;
        this.title = title;
        this.category = category;
        this.date = date;
        this.time = time;
        this.location = location;
        this.status = status;
        this.startTime = startTime;
        this.endTime = endTime;
        this.reminderMinutesBefore = reminderMinutesBefore;
        this.createdBy = createdBy;
        this.durationMinutes = durationMinutes;
        this.cancelled = cancelled;
        this.totalParticipants = Math.max(0, totalParticipants);
        this.acceptedParticipants = Math.max(0, Math.min(acceptedParticipants, this.totalParticipants));
    }

    public String getId() { return id; }
    public String getGroupId() { return groupId; }
    public String getTitle() { return title; }
    public String getCategory() { return category; }
    public String getDate() { return date; }
    public String getTime() { return time; }
    public String getLocation() { return location; }
    public String getStatus() { return status; }
    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }
    public Integer getReminderMinutesBefore() { return reminderMinutesBefore; }
    public String getCreatedBy() { return createdBy; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public boolean isCancelled() { return cancelled; }
    public int getTotalParticipants() { return totalParticipants; }
    public int getAcceptedParticipants() { return acceptedParticipants; }

    public boolean hasScheduledTime() {
        return startTime != null && !startTime.isEmpty() && endTime != null && !endTime.isEmpty();
    }
}
