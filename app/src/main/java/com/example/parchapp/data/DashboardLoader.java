package com.example.parchapp.data;

import android.util.Log;

import com.example.parchapp.domain.model.Group;
import com.example.parchapp.domain.model.PlannedActivity;
import com.example.parchapp.util.BackgroundThreads;
import com.example.parchapp.util.MainThread;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/* Multithreading (issue #34): the Dashboard needs my groups and today's plans, two reads that do not depend on
 * each other. They run at the same time on two worker threads and each one answers on the main thread as soon
 * as it is ready, so the screen shows the groups without waiting for the plans. The main thread only draws.
 * Eventual connectivity (issue #43), cache then network: each read first gives what Firestore saved on the phone,
 * so the screen is never empty, and then the answer of the network, which replaces it. */
public final class DashboardLoader {
    static final String TAG = "ParchPerf";
    // two threads, one per read; the pool lives with the app so opening the Dashboard does not create threads
    private static final ExecutorService POOL = BackgroundThreads.pool("parch-dashboard", 2);

    public interface Listener<T> {
        /* fromCache: the items come from the phone, the network answer may still replace them */
        void onLoaded(List<T> items, boolean fromCache);

        void onFailed(Exception error);
    }

    interface Load<T> {
        List<T> run(String userId, boolean fromCache) throws Exception;
    }

    public void load(String userId, Listener<Group> groups, Listener<PlannedActivity> activities) {
        long started = System.nanoTime();
        AtomicInteger pending = new AtomicInteger(2);
        AtomicLong busyNanos = new AtomicLong();
        submit("groups", userId, Repositories.groups()::loadGroups, groups, started, pending, busyNanos);
        submit("activities", userId, Repositories.activities()::loadActivities, activities, started, pending, busyNanos);
    }

    /* only the plans, cache then network, for screens that already have their groups (Groups list) */
    public void loadPlans(String userId, Listener<PlannedActivity> activities) {
        submit("plans", userId, Repositories.activities()::loadActivities, activities, System.nanoTime(),
                new AtomicInteger(1), new AtomicLong());
    }

    private <T> void submit(String name, String userId, Load<T> load, Listener<T> listener, long started,
                            AtomicInteger pending, AtomicLong busyNanos) {
        POOL.execute(() -> {
            BackgroundThreads.beginSection("Dashboard.load " + name);
            long begin = System.nanoTime();
            boolean shownFromCache = false;
            try {
                // 1. cache: instant and works offline; an empty cache (first time) shows nothing instead of "no groups"
                try {
                    List<T> saved = load.run(userId, true);
                    if (!saved.isEmpty()) {
                        shownFromCache = true;
                        MainThread.post(() -> listener.onLoaded(saved, true));
                    }
                    Log.d(TAG, String.format(Locale.US, "Dashboard %s from cache: %d in %.1f ms",
                            name, saved.size(), (System.nanoTime() - begin) / 1e6));
                } catch (Exception noCache) {
                    Log.d(TAG, "Dashboard " + name + ": nothing saved on the phone yet");
                }
                // 2. network: the fresh data replaces the saved one; offline, Firestore answers again with its cache
                List<T> items = load.run(userId, false);
                MainThread.post(() -> listener.onLoaded(items, false));
            } catch (Exception error) {
                // if the saved data is already on the screen we keep it; the offline banner explains why it is not fresh
                if (!shownFromCache) {
                    MainThread.post(() -> listener.onFailed(error));
                }
            } finally {
                long took = System.nanoTime() - begin;
                BackgroundThreads.endSection();
                Log.d(TAG, String.format(Locale.US, "Dashboard %s: %.1f ms on %s",
                        name, took / 1e6, Thread.currentThread().getName()));
                busyNanos.addAndGet(took);
                // when both reads are done: the wall time is what the user waits, the sum is what it would take one after the other
                if (pending.decrementAndGet() == 0) {
                    Log.d(TAG, String.format(Locale.US, "Dashboard ready in %.1f ms (one after the other: %.1f ms)",
                            (System.nanoTime() - started) / 1e6, busyNanos.get() / 1e6));
                }
            }
        });
    }
}
