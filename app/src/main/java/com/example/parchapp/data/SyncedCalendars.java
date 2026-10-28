package com.example.parchapp.data;

import com.example.parchapp.domain.model.DaySchedule;
import com.example.parchapp.domain.model.Member;
import com.example.parchapp.util.Callback;

import java.util.ArrayList;
import java.util.List;

/* How many members of a group have their calendar for a day known to the backend (getScheduleDay). Shared by the Groups list and Group Detail so both show the same real number, not the group size. */
public final class SyncedCalendars {

    private SyncedCalendars() {
    }

    /* date is a Bogota day "yyyy-MM-dd"; me is the signed-in user, who is always part of the answer. */
    public static void count(ScheduleRepository schedules, String date, List<Member> members, String me,
                             Callback<Integer> callback) {
        List<String> others = new ArrayList<>();
        for (Member member : members) {
            if (!member.getId().equals(me)) {
                others.add(member.getId());
            }
        }
        // the backend compares at most 10 friends per call, so big groups are checked in batches
        List<List<String>> batches = new ArrayList<>();
        for (int start = 0; start < others.size(); start += ScheduleRepository.MAX_FRIENDS) {
            batches.add(new ArrayList<>(others.subList(start,
                    Math.min(others.size(), start + ScheduleRepository.MAX_FRIENDS))));
        }
        if (batches.isEmpty()) {
            batches.add(new ArrayList<>());
        }
        int[] known = {0};
        int[] pending = {batches.size()};
        Exception[] failure = {null};
        for (int b = 0; b < batches.size(); b++) {
            boolean countMe = b == 0;
            schedules.getDay(date, batches.get(b), new Callback<List<DaySchedule>>() {
                @Override
                public void onSuccess(List<DaySchedule> result) {
                    for (DaySchedule schedule : result) {
                        // every call also returns my own schedule, so i count it only once
                        if (schedule.isKnown() && (countMe || !schedule.getOwnerId().equals(me))) {
                            known[0]++;
                        }
                    }
                    done();
                }

                @Override
                public void onError(Exception error) {
                    failure[0] = error;
                    done();
                }

                private void done() {
                    if (--pending[0] > 0) {
                        return;
                    }
                    if (failure[0] != null) {
                        callback.onError(failure[0]);
                    } else {
                        callback.onSuccess(known[0]);
                    }
                }
            });
        }
    }
}
