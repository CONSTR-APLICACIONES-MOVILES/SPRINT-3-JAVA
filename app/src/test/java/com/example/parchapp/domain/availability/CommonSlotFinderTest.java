package com.example.parchapp.domain.availability;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.parchapp.domain.model.DaySchedule;
import com.example.parchapp.domain.model.TimeBlock;
import com.example.parchapp.domain.model.TimeSlot;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CommonSlotFinderTest {

    private static final int H = 60;

    private static DaySchedule known(String id, TimeBlock... busy) {
        return new DaySchedule(id, true, Arrays.asList(busy));
    }

    @Test
    public void aCandidateEveryHalfHourWhereNobodyIsBusyLikeFlutter() {
        // me busy 8-12, friend busy 13-22 -> free 12-13 and 22-23
        List<TimeSlot> slots = CommonSlotFinder.find(Arrays.asList(
                known("me", new TimeBlock(8 * H, 12 * H)),
                known("ana", new TimeBlock(13 * H, 22 * H))), 30, 0);

        assertEquals(4, slots.size());
        assertEquals(12 * H, slots.get(0).getStartMinute());
        assertEquals(12 * H + 30, slots.get(1).getStartMinute());
        assertEquals(22 * H, slots.get(2).getStartMinute());
        assertEquals(CommonSlotFinder.DAY_END_MINUTE, slots.get(3).getEndMinute());
    }

    @Test
    public void candidatesOverlapBecauseTheyStartEvery30Minutes() {
        List<TimeSlot> slots = CommonSlotFinder.find(Collections.singletonList(
                known("me", new TimeBlock(10 * H, 23 * H))), 60, 0);

        // 8:00-9:00, 8:30-9:30, 9:00-10:00
        assertEquals(3, slots.size());
        assertEquals(8 * H + 30, slots.get(1).getStartMinute());
    }

    @Test
    public void unknownSchedulesAreNotCountedAsFree() {
        List<TimeSlot> slots = CommonSlotFinder.find(Arrays.asList(
                known("me", new TimeBlock(8 * H, 22 * H)),
                new DaySchedule("ana", false, Collections.emptyList())), 60, 0);

        assertEquals(1, slots.size());
        assertEquals(1, slots.get(0).getFreeCount());
        assertEquals(2, slots.get(0).getGroupSize());
        assertTrue(!slots.get(0).isEveryoneFree());
    }

    @Test
    public void pastCandidatesAreSkippedToday() {
        List<TimeSlot> slots = CommonSlotFinder.find(Collections.singletonList(known("me")), 60, 21 * H + 10);

        // 21:30-22:30 and 22:00-23:00
        assertEquals(2, slots.size());
        assertEquals(21 * H + 30, slots.get(0).getStartMinute());
    }

    @Test
    public void nobodyKnownMeansNoSlots() {
        assertTrue(CommonSlotFinder.find(Collections.singletonList(
                new DaySchedule("me", false, Collections.emptyList())), 60, 0).isEmpty());
    }
}
