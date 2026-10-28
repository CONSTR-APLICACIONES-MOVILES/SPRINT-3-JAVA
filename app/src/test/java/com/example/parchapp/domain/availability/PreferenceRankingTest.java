package com.example.parchapp.domain.availability;

import static org.junit.Assert.assertEquals;

import com.example.parchapp.domain.model.Member;
import com.example.parchapp.domain.model.TimeBlock;
import com.example.parchapp.domain.model.TimeSlot;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// f4: tests for the preference ranking
public class PreferenceRankingTest {

    private static final int H = 60;

    private static Member member(String id, int preferredStart, int preferredEnd, TimeBlock... busy) {
        return new Member(id, id, id.substring(0, 1), Arrays.asList(busy), preferredStart, preferredEnd);
    }

// f4: everyone is free from 8 to 14 and from 20 to 23, overlap picks the longer morning
// but the members prefer the night, so preference must pick the night
    @Test
    public void preferredHoursBeatTheLongerSlot() {
        List<Member> members = Arrays.asList(
                member("a", 20 * H, 23 * H, new TimeBlock(14 * H, 20 * H)),
                member("b", 20 * H, 22 * H, new TimeBlock(14 * H, 20 * H)));
        AvailabilityEngine engine = new AvailabilityEngine();

        engine.setRanker(new OverlapRanking());
        assertEquals(8 * H, engine.findCommonSlots(members).get(0).getStartMinute());

        engine.setRanker(new PreferenceRanking());
        assertEquals(20 * H, engine.findCommonSlots(members).get(0).getStartMinute());
    }

// f4: the ranking gives a new list and does not change the list it gets
    @Test
    public void rankDoesNotChangeTheOriginalList() {
        List<Member> members = Collections.singletonList(
                member("a", 20 * H, 23 * H, new TimeBlock(14 * H, 20 * H)));
        List<TimeSlot> slots = new AvailabilityEngine().findCommonSlots(members);
        int firstStart = slots.get(0).getStartMinute();

        new PreferenceRanking().rank(slots, members);

        assertEquals(firstStart, slots.get(0).getStartMinute());
    }
}
