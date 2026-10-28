package com.example.parchapp.domain.availability;

import static org.junit.Assert.assertEquals;

import com.example.parchapp.domain.model.TimeSlot;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SoonestRankingTest {

    @Test
    public void sortsByEarliestStartThenFreeMembersAndDurationWithoutMutatingInput() {
        TimeSlot later = new TimeSlot(600, 660, Arrays.asList("a", "b", "c", "d"), 4);
        TimeSlot earlyFew = new TimeSlot(540, 630, Arrays.asList("a", "b"), 4);
        TimeSlot earlyShort = new TimeSlot(540, 600, Arrays.asList("a", "b", "c"), 4);
        TimeSlot earlyLong = new TimeSlot(540, 630, Arrays.asList("a", "b", "c"), 4);
        List<TimeSlot> slots = Arrays.asList(later, earlyFew, earlyShort, earlyLong);

        List<TimeSlot> ranked = new SoonestRanking().rank(slots, Collections.emptyList());

        assertEquals(earlyLong, ranked.get(0));
        assertEquals(earlyShort, ranked.get(1));
        assertEquals(earlyFew, ranked.get(2));
        assertEquals(later, ranked.get(3));
        assertEquals(later, slots.get(0));
        assertEquals("soonest", new SoonestRanking().getName());
    }
}
