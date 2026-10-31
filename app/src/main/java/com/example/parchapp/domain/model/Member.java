package com.example.parchapp.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Member {
    private final String id;
    private final String name;
    private final String initials;
    private final List<TimeBlock> busyBlocks;
    private final int preferredStartMinute;
    private final int preferredEndMinute;

    public Member(String id, String name, String initials, List<TimeBlock> busyBlocks,
                  int preferredStartMinute, int preferredEndMinute) {
        this.id = id;
        this.name = name;
        this.initials = initials;
        this.busyBlocks = new ArrayList<>(busyBlocks);
        this.preferredStartMinute = preferredStartMinute;
        this.preferredEndMinute = preferredEndMinute;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getInitials() {
        return initials;
    }

// f6: groups made by the backend can have empty initials, then we use the first letter of the name
    public String getDisplayInitials() {
        if (initials != null && !initials.isEmpty()) {
            return initials;
        }
        String shown = name == null ? "" : name.trim();
        return shown.isEmpty() ? "?" : shown.substring(0, 1).toUpperCase();
    }

    public List<TimeBlock> getBusyBlocks() {
        return Collections.unmodifiableList(busyBlocks);
    }

    public int getPreferredStartMinute() {
        return preferredStartMinute;
    }

    public int getPreferredEndMinute() {
        return preferredEndMinute;
    }

    public boolean isFree(int start, int end) {
        for (TimeBlock block : busyBlocks) {
            if (block.overlaps(start, end)) {
                return false;
            }
        }
        return true;
    }
}
