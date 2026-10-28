package com.example.parchapp.domain.model;

/*A busy block in a member's day, in minutes since midnight. Only the time range is stored, never the event title, place or attendees (data minimisation, Sprint 1 scenario 9.5).
 */
public class TimeBlock {
    private final int startMinute;
    private final int endMinute;

    public TimeBlock(int startMinute, int endMinute) {
        this.startMinute = startMinute;
        this.endMinute = endMinute;
    }

    public int getStartMinute() {
        return startMinute;
    }

    public int getEndMinute() {
        return endMinute;
    }

    public boolean overlaps(int start, int end) {
        return startMinute < end && start < endMinute;
    }
}
