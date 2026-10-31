package com.example.parchapp.data;

import com.example.parchapp.domain.model.DaySchedule;
import com.example.parchapp.domain.model.ScheduleFriend;
import com.example.parchapp.util.Callback;

import java.util.List;

/* Shared schedules for the Schedule screen. Only busy intervals travel: never titles, places or attendees. The Firebase version calls the same callables as the Flutter app (reciclamos para que). */
public interface ScheduleRepository {
    int MAX_FRIENDS = 10;

    /* People the current user shares a group with. */
    void getFriends(Callback<List<ScheduleFriend>> callback);

    /* The current user's schedule first, then one per friend, for a Bogotá date "yyyy-MM-dd". And it works */
    void getDay(String date, List<String> friendIds, Callback<List<DaySchedule>> callback);

    /* Before planning on a common slot the backend checks it is still free for everyone compared (validatePlanningSlot, like the Flutter app). The callback gets the comparison id, valid for 5 minutes. Times are UTC ISO strings at minute precision. */
    void validatePlanningSlot(List<String> participantIds, String startTime, String endTime, Callback<String> callback);

    /* Flutter Schedule: evidence that I compared these friends on this day, to plan without picking a slot. Gives the comparison id. */
    default void prepareProposalComparison(String date, List<String> friendIds, Callback<String> callback) {
        callback.onError(new UnsupportedOperationException("Comparing needs the backend"));
    }

    /* Google Calendar import: replaces my busy intervals from startDate (today in Bogota) for the next days. Each interval is {startIso, endIso}; only free/busy travels, never titles. The callback gets how many intervals were sent. */
    void importCalendarAvailability(String startDate, int days, List<String[]> intervals, Callback<Integer> callback);
}
