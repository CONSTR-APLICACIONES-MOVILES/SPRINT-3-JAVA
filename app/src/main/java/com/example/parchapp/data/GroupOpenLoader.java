package com.example.parchapp.data;

import androidx.annotation.Nullable;

import com.example.parchapp.domain.model.PlannedActivity;
import com.example.parchapp.util.BackgroundThreads;
import com.example.parchapp.util.MainThread;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

// strategy 6, multithreading: what group details needs when it opens, with tasks inside a task
// the parent task starts one child for each read, waits for both and sends the result to the main thread
// the reads do not depend on each other, so together they take as long as the slower one
public final class GroupOpenLoader {
// one thread: two openings answer in order, so an old answer never replaces a new one
    private static final ExecutorService PARENT = BackgroundThreads.serial("parch-group-open");
// another pool for the children, a parent that waits in its own pool could stop it with nobody left to run them
    private static final ExecutorService CHILDREN = BackgroundThreads.pool("parch-group-read", 2);

    public interface Listener {
// always on the main thread, plans is null when they could not be read
        void onLoaded(long lastSynced, @Nullable List<PlannedActivity> plans);
    }

    static final class Opened {
        final long lastSynced;
        @Nullable
        final List<PlannedActivity> plans;

        Opened(long lastSynced, @Nullable List<PlannedActivity> plans) {
            this.lastSynced = lastSynced;
            this.plans = plans;
        }
    }

    private GroupOpenLoader() {
    }

    public static void load(GroupSyncStore sync, ActivityPlanRepository activities, String groupId, String userId,
                            Listener listener) {
        PARENT.execute(() -> {
            BackgroundThreads.beginSection("GroupDetail.open");
            try {
                Opened opened = open(CHILDREN, () -> sync.lastSynced(groupId),
                        () -> activities.loadActivities(userId, false));
                MainThread.post(() -> listener.onLoaded(opened.lastSynced, opened.plans));
            } finally {
                BackgroundThreads.endSection();
            }
        });
    }

// the job of the parent: one read that fails does not lose the other one
    static Opened open(ExecutorService children, Callable<Long> lastSynced, Callable<List<PlannedActivity>> plans) {
        Future<Long> synced = children.submit(lastSynced);
        Future<List<PlannedActivity>> planned = children.submit(plans);
        Long time = result(synced);
        return new Opened(time == null ? -1L : time, result(planned));
    }

// a read that failed gives null, the screen already knows what to show without it
    @Nullable
    private static <T> T result(Future<T> child) {
        try {
            return child.get();
        } catch (ExecutionException error) {
            return null;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
