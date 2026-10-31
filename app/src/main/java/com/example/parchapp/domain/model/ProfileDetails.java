package com.example.parchapp.domain.model;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** Profile data returned by the authenticated getProfile callable. */
public final class ProfileDetails {
    private final String userId;
    private final String name;
    private final String username;
    private final String program;
    private final boolean verified;
    private final int semester;
    private final int year;
    private final String academicLevel;
    private final String campus;
    private final String university;
    private final String studentId;
    private final String status;
    private final String statusExpiresAt;
    private final String statusVisibility;
    private final int plansCompleted;
    private final int activeGroups;
    private final Integer onTimeRate;
    private final boolean fromCache;
    private final long savedAtMillis;

    public ProfileDetails(String userId, String name, String username, String program, boolean verified,
                          int semester, int year, String academicLevel, String campus, String university,
                          String studentId, String status, String statusExpiresAt, String statusVisibility,
                          int plansCompleted, int activeGroups, Integer onTimeRate,
                          boolean fromCache, long savedAtMillis) {
        this.userId = safe(userId);
        this.name = safe(name);
        this.username = safe(username);
        this.program = safe(program);
        this.verified = verified;
        this.semester = Math.max(0, semester);
        this.year = Math.max(0, year);
        this.academicLevel = safe(academicLevel);
        this.campus = safe(campus);
        this.university = safe(university);
        this.studentId = safe(studentId);
        this.status = safe(status);
        this.statusExpiresAt = safe(statusExpiresAt);
        this.statusVisibility = validVisibility(statusVisibility);
        this.plansCompleted = Math.max(0, plansCompleted);
        this.activeGroups = Math.max(0, activeGroups);
        this.onTimeRate = onTimeRate;
        this.fromCache = fromCache;
        this.savedAtMillis = savedAtMillis;
    }

    public static ProfileDetails fromMap(Map<?, ?> data, long savedAtMillis) {
        Map<?, ?> preferences = map(data.get("preferences"));
        Object rawRate = data.get("onTimeRate");
        Integer rate = rawRate instanceof Number ? ((Number) rawRate).intValue() : null;
        return new ProfileDetails(string(data.get("id")), string(data.get("name")),
                string(data.get("username")), string(data.get("program")),
                Boolean.TRUE.equals(data.get("verified")), number(data.get("semester")),
                number(data.get("year")), string(data.get("academicLevel")),
                string(data.get("campus")), string(data.get("university")),
                string(data.get("studentId")), string(data.get("status")),
                string(data.get("statusExpiresAt")), string(preferences.get("statusVisibility")),
                number(data.get("plansCompleted")), number(data.get("activeGroups")), rate,
                false, savedAtMillis);
    }

    public ProfileDetails asCached(long timestamp) {
        return new ProfileDetails(userId, name, username, program, verified, semester, year,
                academicLevel, campus, university, studentId, status, statusExpiresAt,
                statusVisibility, plansCompleted, activeGroups, onTimeRate, true, timestamp);
    }

    public ProfileDetails withEditableFields(String nextName, String nextProgram, int nextSemester,
                                              int nextYear, String nextAcademicLevel, String nextCampus,
                                              String nextUniversity, String nextStudentId) {
        return new ProfileDetails(userId, nextName, username, nextProgram, verified, nextSemester,
                nextYear, nextAcademicLevel, nextCampus, nextUniversity, nextStudentId, status,
                statusExpiresAt, statusVisibility, plansCompleted, activeGroups, onTimeRate,
                fromCache, savedAtMillis);
    }

    public ProfileDetails withPresence(String nextStatus, String nextExpiry, String nextVisibility) {
        return new ProfileDetails(userId, name, username, program, verified, semester, year,
                academicLevel, campus, university, studentId, nextStatus, nextExpiry, nextVisibility,
                plansCompleted, activeGroups, onTimeRate, fromCache, savedAtMillis);
    }

    public String getAcademicLevelLabel() {
        if (academicLevel.isEmpty()) return "Not set";
        switch (academicLevel) {
            case "undergraduate": return "Undergraduate";
            case "graduate": return "Graduate";
            case "master": return "Master";
            case "phd": return "PhD";
            default: return academicLevel;
        }
    }

    public String getStatusLabel() {
        switch (getEffectiveStatus()) {
            case "active": return "Available now";
            case "busy": return "Busy";
            case "inClass": return "In class";
            case "focused": return "Focused";
            case "inTransit": return "In transit";
            case "offline": return "Offline";
            default: return status.isEmpty() ? "Status unavailable" : status;
        }
    }

    public String getEffectiveStatus() {
        if (!statusExpiresAt.isEmpty()) {
            for (String pattern : new String[]{"yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX"}) {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setLenient(false);
                format.setTimeZone(TimeZone.getTimeZone("UTC"));
                ParsePosition position = new ParsePosition(0);
                Date expiry = format.parse(statusExpiresAt, position);
                if (expiry != null && position.getIndex() == statusExpiresAt.length()
                        && expiry.getTime() <= System.currentTimeMillis()) {
                    return "offline";
                }
            }
        }
        return status;
    }

    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getUsername() { return username; }
    public String getProgram() { return program; }
    public boolean isVerified() { return verified; }
    public int getSemester() { return semester; }
    public int getYear() { return year; }
    public String getAcademicLevel() { return academicLevel; }
    public String getCampus() { return campus; }
    public String getUniversity() { return university; }
    public String getStudentId() { return studentId; }
    public String getStatus() { return status; }
    public String getStatusExpiresAt() { return statusExpiresAt; }
    public String getStatusVisibility() { return statusVisibility; }
    public int getPlansCompleted() { return plansCompleted; }
    public int getActiveGroups() { return activeGroups; }
    public Integer getOnTimeRate() { return onTimeRate; }
    public boolean isFromCache() { return fromCache; }
    public long getSavedAtMillis() { return savedAtMillis; }

    private static String safe(String value) { return value == null ? "" : value; }
    private static String string(Object value) { return value == null ? "" : value.toString(); }
    private static int number(Object value) { return value instanceof Number ? ((Number) value).intValue() : 0; }
    private static Map<?, ?> map(Object value) { return value instanceof Map ? (Map<?, ?>) value : java.util.Collections.emptyMap(); }
    private static String validVisibility(String value) {
        return "closeFriends".equals(value) || "private".equals(value) ? value : "groups";
    }
}
