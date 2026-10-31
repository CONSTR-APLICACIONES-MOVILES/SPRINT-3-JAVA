package com.example.parchapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.parchapp.domain.model.DaySchedule;
import com.example.parchapp.domain.model.Member;
import com.example.parchapp.domain.model.ScheduleFriend;
import com.example.parchapp.util.Callback;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class SyncedCalendarsTest {

    /* Answers like getScheduleDay: my schedule first, then one per asked friend. */
    private static final class FakeSchedules implements ScheduleRepository {
        final Set<String> synced;
        final List<Integer> batchSizes = new ArrayList<>();

        FakeSchedules(Set<String> synced) {
            this.synced = synced;
        }

        @Override
        public void getFriends(Callback<List<ScheduleFriend>> callback) {
            callback.onSuccess(Collections.emptyList());
        }

        @Override
        public void getDay(String date, List<String> friendIds, Callback<List<DaySchedule>> callback) {
            batchSizes.add(friendIds.size());
            List<DaySchedule> result = new ArrayList<>();
            result.add(new DaySchedule("me", synced.contains("me"), Collections.emptyList()));
            for (String id : friendIds) {
                result.add(new DaySchedule(id, synced.contains(id), Collections.emptyList()));
            }
            callback.onSuccess(result);
        }

        @Override
        public void validatePlanningSlot(List<String> participantIds, String startTime, String endTime,
                                         Callback<String> callback) {
            callback.onError(new UnsupportedOperationException());
        }

        @Override
        public void importCalendarAvailability(String startDate, int days, List<String[]> intervals,
                                               Callback<Integer> callback) {
            callback.onError(new UnsupportedOperationException());
        }
    }

    private static List<Member> group(int size) {
        List<Member> members = new ArrayList<>();
        members.add(new Member("me", "Me", "M", Collections.emptyList(), 0, 0));
        for (int i = 1; i < size; i++) {
            members.add(new Member("m" + i, "Member " + i, "M", Collections.emptyList(), 0, 0));
        }
        return members;
    }

    private static int count(FakeSchedules schedules, List<Member> members) {
        int[] result = {-1};
        SyncedCalendars.count(schedules, "2026-10-28", members, "me", new Callback<Integer>() {
            @Override
            public void onSuccess(Integer known) {
                result[0] = known;
            }

            @Override
            public void onError(Exception error) {
                throw new AssertionError(error);
            }
        });
        return result[0];
    }

    @Test
    public void bigGroupsAreCheckedInBatchesAndMyCalendarCountsOnce() {
        Set<String> everyone = new java.util.HashSet<>();
        for (Member member : group(14)) {
            everyone.add(member.getId());
        }
        FakeSchedules schedules = new FakeSchedules(everyone);

        assertEquals(14, count(schedules, group(14)));
        assertEquals(java.util.Arrays.asList(10, 3), schedules.batchSizes);
    }

    @Test
    public void membersWithoutACalendarAreNotCounted() {
        // a new account has no calendar yet: 13 of 14
        Set<String> synced = new java.util.HashSet<>();
        for (Member member : group(14)) {
            synced.add(member.getId());
        }
        synced.remove("me");

        assertEquals(13, count(new FakeSchedules(synced), group(14)));
    }

    @Test
    public void aGroupWithOnlyMeStillAsksTheBackend() {
        FakeSchedules schedules = new FakeSchedules(Collections.singleton("me"));

        assertEquals(1, count(schedules, group(1)));
        assertTrue(schedules.batchSizes.size() == 1 && schedules.batchSizes.get(0) == 0);
    }
}
