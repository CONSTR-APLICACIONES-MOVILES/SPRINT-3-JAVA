package com.example.parchapp.util;

import android.os.Process;
import android.os.Trace;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/* Multithreading: the worker threads of the app. Each pool has a name, so in the Android Studio profiler and in
 * Perfetto we see "parch-dashboard-1" or "parch-availability-1" instead of "pool-3-thread-1", and they run with
 * background priority, so the main thread (drawing the screen) always goes first. */
public final class BackgroundThreads {

    private BackgroundThreads() {
    }

    /* fixed pool: as many tasks at the same time as threads, the rest wait in the queue */
    public static ExecutorService pool(String name, int threads) {
        return Executors.newFixedThreadPool(threads, factory(name));
    }

    /* one thread: tasks run one after the other, in the order they were sent (a serial queue) */
    public static ExecutorService serial(String name) {
        return Executors.newSingleThreadExecutor(factory(name));
    }

    static ThreadFactory factory(String name) {
        AtomicInteger count = new AtomicInteger();
        return work -> {
            Thread thread = new Thread(() -> {
                lowerPriority();
                work.run();
            }, name + "-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static void lowerPriority() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
        } catch (RuntimeException notOnAndroid) {
            // unit tests run on the JVM, where android.os is not available
        }
    }

    /* marks a piece of work, so it shows with this name in the CPU profiler (System Trace) and in Perfetto */
    public static void beginSection(String name) {
        try {
            Trace.beginSection(name);
        } catch (RuntimeException notOnAndroid) {
            // unit tests run on the JVM
        }
    }

    public static void endSection() {
        try {
            Trace.endSection();
        } catch (RuntimeException notOnAndroid) {
            // unit tests run on the JVM
        }
    }
}
