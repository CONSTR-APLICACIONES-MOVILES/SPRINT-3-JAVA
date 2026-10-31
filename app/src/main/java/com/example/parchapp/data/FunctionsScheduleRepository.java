package com.example.parchapp.data;

import com.example.parchapp.domain.model.DaySchedule;
import com.example.parchapp.domain.model.ScheduleFriend;
import com.example.parchapp.domain.model.TimeBlock;
import com.example.parchapp.util.Callback;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* Calls getScheduleFriends and getScheduleDay (Backend/docs/mobile-api.md). The backend checks group membership and each person's sharing preference, which security rules alone cannot. */
class FunctionsScheduleRepository implements ScheduleRepository {
    private final FirebaseFunctions functions;

    FunctionsScheduleRepository(FirebaseFunctions functions) {
        this.functions = functions;
    }

    @Override
    public void getFriends(Callback<List<ScheduleFriend>> callback) {
        functions.getHttpsCallable("getScheduleFriends").call(new HashMap<String, Object>())
                .addOnSuccessListener(result -> {
                    List<ScheduleFriend> friends = new ArrayList<>();
                    for (Map<String, Object> raw : listOfMaps(asMap(result.getData()).get("friends"))) {
                        friends.add(new ScheduleFriend(asString(raw.get("id")), asString(raw.get("name")),
                                asString(raw.get("initials"))));
                    }
                    callback.onSuccess(friends);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void getDay(String date, List<String> friendIds, Callback<List<DaySchedule>> callback) {
        Map<String, Object> request = new HashMap<>();
        request.put("date", date);
        request.put("friendIds", new ArrayList<>(friendIds));
        functions.getHttpsCallable("getScheduleDay").call(request)
                .addOnSuccessListener(result -> {
                    List<DaySchedule> schedules = new ArrayList<>();
                    for (Map<String, Object> raw : listOfMaps(asMap(result.getData()).get("schedules"))) {
                        List<TimeBlock> busy = new ArrayList<>();
                        for (Map<String, Object> block : listOfMaps(raw.get("busy"))) {
                            busy.add(new TimeBlock(asInt(block.get("start")), asInt(block.get("end"))));
                        }
                        schedules.add(new DaySchedule(asString(raw.get("ownerId")),
                                Boolean.TRUE.equals(raw.get("known")), busy));
                    }
                    callback.onSuccess(schedules);
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void validatePlanningSlot(List<String> participantIds, String startTime, String endTime,
                                     Callback<String> callback) {
        Map<String, Object> request = new HashMap<>();
        request.put("participantIds", new ArrayList<>(participantIds));
        request.put("startTime", startTime);
        request.put("endTime", endTime);
        request.put("timeZone", "America/Bogota");
        functions.getHttpsCallable("validatePlanningSlot").call(request)
                .addOnSuccessListener(result -> callback.onSuccess(asString(asMap(result.getData()).get("comparisonId"))))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void prepareProposalComparison(String date, List<String> friendIds, Callback<String> callback) {
        Map<String, Object> request = new HashMap<>();
        request.put("date", date);
        request.put("friendIds", new ArrayList<>(friendIds));
        functions.getHttpsCallable("prepareProposalComparison").call(request)
                .addOnSuccessListener(result -> callback.onSuccess(asString(asMap(result.getData()).get("comparisonEvidenceId"))))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void importCalendarAvailability(String startDate, int days, List<String[]> intervals,
                                           Callback<Integer> callback) {
        List<Map<String, Object>> raw = new ArrayList<>();
        for (String[] interval : intervals) {
            Map<String, Object> block = new HashMap<>();
            block.put("start", interval[0]);
            block.put("end", interval[1]);
            raw.add(block);
        }
        Map<String, Object> request = new HashMap<>();
        request.put("startDate", startDate);
        request.put("days", days);
        request.put("intervals", raw);
        functions.getHttpsCallable("importCalendarAvailability").call(request)
                .addOnSuccessListener(result -> callback.onSuccess(intervals.size()))
                .addOnFailureListener(callback::onError);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object value) {
        List<Map<String, Object>> maps = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<Object>) value) {
                if (item instanceof Map) {
                    maps.add((Map<String, Object>) item);
                }
            }
        }
        return maps;
    }

    private static int asInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static String asString(Object value) {
        return value == null ? "" : value.toString();
    }
}
