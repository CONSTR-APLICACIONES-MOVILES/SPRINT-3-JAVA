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
}
