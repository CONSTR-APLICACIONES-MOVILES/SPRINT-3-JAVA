package com.example.parchapp.domain.model;

public class PlannedActivity {
    public static final String STATUS_PROPOSED = "PROPOSED";
    public static final String STATUS_CONFIRMED = "CONFIRMED";

    private final String id;
    private final String groupId;
    private final String title;
    private final String category;
    private final String date;
    private final String time;
    private final String location;
    private final String status;
    private final long createdAt;
    private final int groupSizeAtCreation;
    private final boolean comparisonUsed;

    public PlannedActivity(String id, String groupId, String title, String category, String date,
                           String time, String location, String status, long createdAt) {
        this(id, groupId, title, category, date, time, location, status, createdAt, 0, false);
    }

    public PlannedActivity(String id, String groupId, String title, String category, String date,
                           String time, String location, String status, long createdAt,
                           int groupSizeAtCreation, boolean comparisonUsed) {
        this.id = id;
        this.groupId = groupId;
        this.title = title;
        this.category = category;
        this.date = date;
        this.time = time;
        this.location = location;
        this.status = status;
        this.createdAt = createdAt;
        this.groupSizeAtCreation = groupSizeAtCreation;
        this.comparisonUsed = comparisonUsed;
    }

    public PlannedActivity withId(String newId) {
        return new PlannedActivity(newId, groupId, title, category, date, time, location, status,
                createdAt, groupSizeAtCreation, comparisonUsed);
    }

    public PlannedActivity withPlanningContext(int groupSize, boolean usedComparison) {
        return new PlannedActivity(id, groupId, title, category, date, time, location, status,
                createdAt, groupSize, usedComparison);
    }

    public String getId() {
        return id;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    public String getDate() {
        return date;
    }

    public String getTime() {
        return time;
    }

    public String getLocation() {
        return location;
    }

    public String getStatus() {
        return status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public int getGroupSizeAtCreation() {
        return groupSizeAtCreation;
    }

    public boolean isComparisonUsed() {
        return comparisonUsed;
    }
}
