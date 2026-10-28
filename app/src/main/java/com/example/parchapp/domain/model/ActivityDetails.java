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

    public ActivityDetails(String id, String groupId, String title, String category, String date,
                           String time, String location, String status, String startTime,
                           String endTime, Integer reminderMinutesBefore) {
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

    public boolean hasScheduledTime() { return startTime != null && !startTime.isEmpty(); }
}
