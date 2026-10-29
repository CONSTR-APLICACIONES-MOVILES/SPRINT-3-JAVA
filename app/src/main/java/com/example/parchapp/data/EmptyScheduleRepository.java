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
    @Override public void validatePlanningSlot(List<String> participantIds, String startTime, String endTime,
                                               Callback<String> callback) {
        MainThread.post(() -> callback.onError(new UnsupportedOperationException("Validating a slot needs the backend")));
    }
    @Override public void importCalendarAvailability(String startDate, int days, List<String[]> intervals,
                                                     Callback<Integer> callback) {
        MainThread.post(() -> callback.onError(new UnsupportedOperationException("Importing a calendar needs the backend")));
    }
}
