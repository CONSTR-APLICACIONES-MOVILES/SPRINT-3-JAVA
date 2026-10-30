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
 * as it is ready, so the screen shows the groups without waiting for the plans. The main thread only draws. */
public final class DashboardLoader {
    static final String TAG = "ParchPerf";
    // two threads, one per read; the pool lives with the app so opening the Dashboard does not create threads
    private static final ExecutorService POOL = BackgroundThreads.pool("parch-dashboard", 2);

    public interface Listener<T> {
        void onLoaded(List<T> items);

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

    private <T> void submit(String name, String userId, Load<T> load, Listener<T> listener, long started,
                            AtomicInteger pending, AtomicLong busyNanos) {
        POOL.execute(() -> {
            BackgroundThreads.beginSection("Dashboard.load " + name);
            long begin = System.nanoTime();
            try {
                List<T> items = load.run(userId, false);
                MainThread.post(() -> listener.onLoaded(items));
            } catch (Exception error) {
                MainThread.post(() -> listener.onFailed(error));
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
