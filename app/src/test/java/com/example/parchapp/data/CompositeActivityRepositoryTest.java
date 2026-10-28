package com.example.parchapp.data;

import static org.junit.Assert.assertEquals;

import com.example.parchapp.domain.model.ActivityDetails;
import com.example.parchapp.domain.model.PlannedActivity;
import com.example.parchapp.util.Callback;

import org.junit.Test;

import java.util.List;

public class CompositeActivityRepositoryTest {

    @Test
    public void routesPlanReadsAndTravelCacheToFirestoreAndMutationsToBackend() {
        RecordingRepository firestore = new RecordingRepository();
        RecordingRepository backend = new RecordingRepository();
        CompositeActivityRepository repository = new CompositeActivityRepository(firestore, backend);
        PlannedActivity activity = new PlannedActivity("activity", "group", "Study", "Study",
                "2026-10-28", "10:00", "Library", "CONFIRMED", 0L);
        Callback<PlannedActivity> activityCallback = callback();
        Callback<List<PlannedActivity>> listCallback = callback();
        Callback<ActivityDetails> detailsCallback = callback();
        Callback<Void> voidCallback = callback();

        repository.createActivity(activity, activityCallback);
        repository.getActivities("user", listCallback);
        repository.getActivityDetails("activity", detailsCallback);
        repository.setActivityReminder("activity", 15, voidCallback);
        repository.cacheTravelMinutes("activity", 12);
        assertEquals(Integer.valueOf(12), repository.getCachedTravelMinutes("activity"));

        assertEquals(1, backend.createCalls);
        assertEquals(activityCallback, backend.lastActivityCallback);
        assertEquals(1, backend.detailsCalls);
        assertEquals(detailsCallback, backend.lastDetailsCallback);
        assertEquals(1, backend.reminderCalls);
        assertEquals(voidCallback, backend.lastVoidCallback);
        assertEquals(0, backend.listCalls);
        assertEquals(0, backend.cacheReads);
        assertEquals(0, backend.cacheWrites);
        assertEquals(1, firestore.listCalls);
        assertEquals(listCallback, firestore.lastListCallback);
        assertEquals(1, firestore.cacheWrites);
        assertEquals(1, firestore.cacheReads);
        assertEquals(0, firestore.createCalls);
        assertEquals(0, firestore.detailsCalls);
        assertEquals(0, firestore.reminderCalls);
    }

    private static <T> Callback<T> callback() {
        return new Callback<T>() {
            @Override public void onSuccess(T result) { }
            @Override public void onError(Exception error) { }
        };
    }

    private static final class RecordingRepository implements ActivityPlanRepository, ActivityActionsRepository {
        int createCalls;
        int listCalls;
        int detailsCalls;
        int reminderCalls;
        int cacheReads;
        int cacheWrites;
        Callback<PlannedActivity> lastActivityCallback;
        Callback<List<PlannedActivity>> lastListCallback;
        Callback<ActivityDetails> lastDetailsCallback;
        Callback<Void> lastVoidCallback;
        Integer cachedMinutes;

        @Override public void createActivity(PlannedActivity activity, Callback<PlannedActivity> callback) {
            createCalls++;
            lastActivityCallback = callback;
        }

        @Override public void getActivities(String userId, Callback<List<PlannedActivity>> callback) {
            listCalls++;
            lastListCallback = callback;
        }

        @Override public void getActivityDetails(String activityId, Callback<ActivityDetails> callback) {
            detailsCalls++;
            lastDetailsCallback = callback;
        }

        @Override public void setActivityReminder(String activityId, int minutesBefore, Callback<Void> callback) {
            reminderCalls++;
            lastVoidCallback = callback;
        }

        @Override public Integer getCachedTravelMinutes(String activityId) {
            cacheReads++;
            return cachedMinutes;
        }

        @Override public void cacheTravelMinutes(String activityId, int minutes) {
            cacheWrites++;
            cachedMinutes = minutes;
        }
    }
}
