package com.example.parchapp.domain.availability;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.parchapp.domain.model.Member;
import com.example.parchapp.domain.model.TimeBlock;
import com.example.parchapp.domain.model.TimeSlot;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AvailabilityEngineTest {

    private static final int H = 60;

    private static Member member(String id, TimeBlock... busy) {
        return new Member(id, id, id.substring(0, 1), Arrays.asList(busy), 0, 0);
    }

    @Test
    public void bestSlotIsTheOneWhereEveryoneIsFree() {
        // a and b are free all evening, c only from 19:30
        List<Member> members = Arrays.asList(
                member("a", new TimeBlock(8 * H, 12 * H)),
                member("b", new TimeBlock(9 * H, 14 * H)),
                member("c", new TimeBlock(8 * H, 19 * H + 30)));

        TimeSlot best = new AvailabilityEngine().findCommonSlots(members).get(0);

        assertEquals(19 * H + 30, best.getStartMinute());
        assertEquals(AvailabilityEngine.DAY_END_MINUTE, best.getEndMinute());
        assertTrue(best.isEveryoneFree());
    }

    @Test
    public void slotsShorterThanOneHourAreDropped() {
        List<Member> members = Arrays.asList(
                member("a", new TimeBlock(8 * H, 12 * H), new TimeBlock(12 * H + 30, 23 * H)),
                member("b"));

        List<TimeSlot> slots = new AvailabilityEngine().findCommonSlots(members);

        for (TimeSlot slot : slots) {
            assertTrue(slot.getDurationMinutes() >= AvailabilityEngine.MIN_SLOT_MINUTES);
            assertTrue(!(slot.getStartMinute() == 12 * H && slot.getFreeCount() == 2));
        }
    }

    @Test
    public void emptyGroupHasNoSlots() {
        assertTrue(new AvailabilityEngine().findCommonSlots(Collections.emptyList()).isEmpty());
    }

    @Test
    public void overlapRankingPutsMoreFreeMembersFirstThenTheLongest() {
        // 9-11 everyone is free, 20-23 everyone is free and it is longer, 11-20 only a and b
        List<Member> members = Arrays.asList(
                member("a", new TimeBlock(8 * H, 9 * H)),
                member("b", new TimeBlock(8 * H, 9 * H)),
                member("c", new TimeBlock(8 * H, 9 * H), new TimeBlock(11 * H, 20 * H)));

        List<TimeSlot> slots = new AvailabilityEngine().findCommonSlots(members);

        assertEquals(20 * H, slots.get(0).getStartMinute());
        assertEquals(9 * H, slots.get(1).getStartMinute());
        assertEquals(2, slots.get(2).getFreeCount());
    }

    @Test
    public void formatsMinutesAsTwelveHourClock() {
        assertEquals("7:30 PM", TimeSlot.formatMinute(19 * H + 30));
        assertEquals("12:00 PM", TimeSlot.formatMinute(12 * H));
        assertEquals("8:05 AM", TimeSlot.formatMinute(8 * H + 5));
    }
}
