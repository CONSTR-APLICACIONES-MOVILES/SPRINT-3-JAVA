package com.example.parchapp.domain.availability;

import com.example.parchapp.analytics.AnalyticsEvents;
import com.example.parchapp.analytics.AnalyticsTracker;
import com.example.parchapp.domain.model.Member;
import com.example.parchapp.domain.model.TimeSlot;
import com.example.parchapp.util.BackgroundThreads;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/* F4: the best-slot recommender. It intersects the busy blocks of every member of the group, keeps the windows of at least one hour where two or more are free, and orders them with the current {@link SlotRanker} (Strategy). The default ranking is by number of free members. */
public class AvailabilityEngine {
    public static final int DAY_START_MINUTE = 8 * 60;
    public static final int DAY_END_MINUTE = 23 * 60;
    static final int STEP_MINUTES = 30;
    static final int MIN_SLOT_MINUTES = 60;

    // Background execution tactic (Sprint 1 scenarios 9.1 and 9.12): the UI thread never computes slots.
    // one thread works as a serial queue: if the user changes the ranking twice, the answers arrive in that order
    private static final ExecutorService EXECUTOR = BackgroundThreads.serial("parch-availability");

    private SlotRanker ranker = new OverlapRanking();

    public void setRanker(SlotRanker ranker) {
        this.ranker = ranker;
    }

    public SlotRanker getRanker() {
        return ranker;
    }

    public List<TimeSlot> findCommonSlots(List<Member> members) {
        return findCommonSlots(members, ranker);
    }

    /*It runs the calculation on a background thread, logs its response time (BQ1) and delivers the ranked slots on the main thread.
        @param source screen that asked for the calculation, logged with the event
     */
    public void findCommonSlotsAsync(List<Member> members, String source, Callback<List<TimeSlot>> callback) {
        SlotRanker activeRanker = ranker;
        List<Member> snapshot = new ArrayList<>(members);
        EXECUTOR.execute(() -> {
            long startNanos = System.nanoTime();
            List<TimeSlot> slots;
            BackgroundThreads.beginSection("AvailabilityEngine.findCommonSlots");
            try {
                slots = findCommonSlots(snapshot, activeRanker);
            } catch (RuntimeException e) {
                MainThread.post(() -> callback.onError(e));
                return;
            } finally {
                BackgroundThreads.endSection();
            }
            double durationMs = (System.nanoTime() - startNanos) / 1_000_000.0;

            Map<String, Object> params = new HashMap<>();
            params.put(AnalyticsEvents.PARAM_GROUP_SIZE, snapshot.size());
            params.put(AnalyticsEvents.PARAM_DURATION_MS, durationMs);
            params.put(AnalyticsEvents.PARAM_SLOT_COUNT, slots.size());
            params.put(AnalyticsEvents.PARAM_RANKER, activeRanker.getName());
            params.put(AnalyticsEvents.PARAM_SOURCE, source);
            AnalyticsTracker.getInstance().track(AnalyticsEvents.AVAILABILITY_CALCULATED, params);

            List<TimeSlot> result = slots;
            MainThread.post(() -> callback.onSuccess(result));
        });
    }

    private List<TimeSlot> findCommonSlots(List<Member> members, SlotRanker activeRanker) {
        List<TimeSlot> slots = new ArrayList<>();
        if (members.isEmpty()) {
            return slots;
        }
        // A common per say slot needs at least two people, unless the group only has one member.
        int minFree = Math.min(2, members.size());

        int runStart = DAY_START_MINUTE;
        List<String> runFreeIds = null;
        for (int minute = DAY_START_MINUTE; minute < DAY_END_MINUTE; minute += STEP_MINUTES) {
            List<String> freeIds = freeMemberIds(members, minute, minute + STEP_MINUTES);
            if (freeIds.equals(runFreeIds)) {
                continue;
            }
            addSlotIfUseful(slots, runStart, minute, runFreeIds, members.size(), minFree);
            runStart = minute;
            runFreeIds = freeIds;
        }
        addSlotIfUseful(slots, runStart, DAY_END_MINUTE, runFreeIds, members.size(), minFree);

        return activeRanker.rank(slots, members);
    }

    private static List<String> freeMemberIds(List<Member> members, int start, int end) {
        List<String> ids = new ArrayList<>();
        for (Member member : members) {
            if (member.isFree(start, end)) {
                ids.add(member.getId());
            }
        }
        return ids;
    }

    private static void addSlotIfUseful(List<TimeSlot> slots, int start, int end, List<String> freeIds,
                                        int groupSize, int minFree) {
        if (freeIds == null || freeIds.size() < minFree || end - start < MIN_SLOT_MINUTES) {
            return;
        }
        slots.add(new TimeSlot(start, end, freeIds, groupSize));
    }
}
