package com.example.parchapp.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/* A window of the day where the same set of members is free. */
public class TimeSlot {
    private final int startMinute;
    private final int endMinute;
    private final List<String> freeMemberIds;
    private final int groupSize;

    public TimeSlot(int startMinute, int endMinute, List<String> freeMemberIds, int groupSize) {
        this.startMinute = startMinute;
        this.endMinute = endMinute;
        this.freeMemberIds = new ArrayList<>(freeMemberIds);
        this.groupSize = groupSize;
    }

    public int getStartMinute() {
        return startMinute;
    }

    public int getEndMinute() {
        return endMinute;
    }

    public int getDurationMinutes() {
        return endMinute - startMinute;
    }

    public List<String> getFreeMemberIds() {
        return Collections.unmodifiableList(freeMemberIds);
    }

    public int getFreeCount() {
        return freeMemberIds.size();
    }

    public int getGroupSize() {
        return groupSize;
    }

    public boolean isEveryoneFree() {
        return freeMemberIds.size() == groupSize;
    }

    public String formatRange() {
        return formatMinute(startMinute) + " – " + formatMinute(endMinute);
    }

    public static String formatMinute(int minuteOfDay) {
        int hour = (minuteOfDay / 60) % 24;
        int minute = minuteOfDay % 60;
        int displayHour = hour % 12 == 0 ? 12 : hour % 12;
        return String.format(Locale.US, "%d:%02d %s", displayHour, minute, hour < 12 ? "AM" : "PM");
    }
}
