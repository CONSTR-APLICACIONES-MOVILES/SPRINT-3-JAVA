package com.example.parchapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.parchapp.domain.model.DaySchedule;
import com.example.parchapp.domain.model.TimeBlock;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// offline Schedule: a day saved together with friends only gives back my own schedule
public class OfflineScheduleOwnerTest {

    private static DaySchedule day(String owner) {
        return new DaySchedule(owner, true, Collections.singletonList(new TimeBlock(600, 660)));
    }

// the friends of the saved copy are dropped, my blocks stay as they were saved
    @Test
    public void keepsOnlyTheOwner() {
        List<DaySchedule> saved = Arrays.asList(day("ana"), day("alex"), day("luis"));
        List<DaySchedule> mine = OfflineScheduleRepository.onlyOwner(saved, "ana");
        assertEquals(1, mine.size());
        assertEquals("ana", mine.get(0).getOwnerId());
        assertEquals(600, mine.get(0).getBusy().get(0).getStartMinute());
    }

// a copy without my schedule gives nothing, so the screen shows my day as unknown, never a friend's day
    @Test
    public void neverReturnsAFriend() {
        List<DaySchedule> saved = new ArrayList<>(Arrays.asList(day("alex"), day("luis")));
        assertTrue(OfflineScheduleRepository.onlyOwner(saved, "ana").isEmpty());
    }
}
