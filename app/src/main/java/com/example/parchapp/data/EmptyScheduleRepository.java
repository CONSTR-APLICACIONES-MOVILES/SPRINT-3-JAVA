package com.example.parchapp.data;

import com.example.parchapp.domain.model.DaySchedule;
import com.example.parchapp.domain.model.ScheduleFriend;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;

import java.util.Collections;
import java.util.List;

/** Empty schedule adapter; real schedule integration is restored from its feature branch. */
class EmptyScheduleRepository implements ScheduleRepository {
    @Override public void getFriends(Callback<List<ScheduleFriend>> callback) {
        MainThread.post(() -> callback.onSuccess(Collections.emptyList()));
    }
    @Override public void getDay(String date, List<String> friendIds, Callback<List<DaySchedule>> callback) {
        MainThread.post(() -> callback.onSuccess(Collections.emptyList()));
    }
}
