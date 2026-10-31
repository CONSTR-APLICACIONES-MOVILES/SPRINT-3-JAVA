package com.example.parchapp.domain.availability;

import com.example.parchapp.domain.model.DaySchedule;
import com.example.parchapp.domain.model.TimeBlock;
import com.example.parchapp.domain.model.TimeSlot;

import java.util.ArrayList;
import java.util.List;

/* Common slots of the Schedule screen, the same rule as the Flutter app (AvailabilityService): a candidate starts every 30 minutes, lasts the chosen duration, and is kept when nobody with a known schedule is busy. A schedule that is not synced is never counted as free, so the slot says how many of the people compared are really free. */
public final class CommonSlotFinder {
    public static final int DAY_START_MINUTE = 8 * 60;
    public static final int DAY_END_MINUTE = 23 * 60;
    static final int STEP_MINUTES = 30;

    private CommonSlotFinder() {
    }

    /* earliestStart: candidates starting before it are skipped, for example now when the day is today */
    public static List<TimeSlot> find(List<DaySchedule> schedules, int durationMinutes, int earliestStart) {
        List<TimeSlot> slots = new ArrayList<>();
        List<String> knownIds = new ArrayList<>();
        List<TimeBlock> busy = new ArrayList<>();
        for (DaySchedule schedule : schedules) {
            if (schedule.isKnown()) {
                knownIds.add(schedule.getOwnerId());
                busy.addAll(schedule.getBusy());
            }
        }
        if (knownIds.isEmpty() || durationMinutes <= 0) {
            return slots;
        }
        for (int start = DAY_START_MINUTE; start + durationMinutes <= DAY_END_MINUTE; start += STEP_MINUTES) {
            if (start < earliestStart) {
                continue;
            }
            int end = start + durationMinutes;
            if (isFree(busy, start, end)) {
                slots.add(new TimeSlot(start, end, knownIds, schedules.size()));
            }
        }
        return slots;
    }

    private static boolean isFree(List<TimeBlock> busy, int start, int end) {
        for (TimeBlock block : busy) {
            if (block.overlaps(start, end)) {
                return false;
            }
        }
        return true;
    }
}
