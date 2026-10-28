package com.example.parchapp.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/* One person's busy blocks on one day. Unknown means no synced calendar for that day: it is not the same as free, so unknown schedules never count as availability. */
public class DaySchedule {
    private final String ownerId;
    private final boolean known;
    private final List<TimeBlock> busy;

    public DaySchedule(String ownerId, boolean known, List<TimeBlock> busy) {
        this.ownerId = ownerId;
        this.known = known;
        this.busy = new ArrayList<>(busy);
    }

    public String getOwnerId() {
        return ownerId;
    }

    public boolean isKnown() {
        return known;
    }

    public List<TimeBlock> getBusy() {
        return Collections.unmodifiableList(busy);
    }
}
