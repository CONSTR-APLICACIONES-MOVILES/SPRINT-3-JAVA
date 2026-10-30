package com.example.parchapp.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

// multithreading: the worker pools of the app
public class BackgroundThreadsTest {

// the threads have the name of their pool, so they are easy to find in the profiler
    @Test
    public void threadsAreNamedAfterTheirPool() throws Exception {
        ExecutorService pool = BackgroundThreads.pool("parch-test", 2);
        String name = pool.submit(() -> Thread.currentThread().getName()).get(5, TimeUnit.SECONDS);
        assertTrue(name.startsWith("parch-test-"));
        pool.shutdown();
    }

// a serial queue keeps the order, so an older answer never replaces a newer one
    @Test
    public void serialQueueRunsTasksInOrder() throws Exception {
        ExecutorService serial = BackgroundThreads.serial("parch-serial");
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch done = new CountDownLatch(50);
        for (int i = 0; i < 50; i++) {
            int task = i;
            serial.execute(() -> {
                order.add(task);
                done.countDown();
            });
        }
        assertTrue(done.await(5, TimeUnit.SECONDS));
        for (int i = 0; i < 50; i++) {
            assertEquals(Integer.valueOf(i), order.get(i));
        }
        serial.shutdown();
    }

// a pool of two runs two reads at the same time, on two different threads
    @Test
    public void poolOfTwoRunsBothReadsAtTheSameTime() throws Exception {
        ExecutorService pool = BackgroundThreads.pool("parch-dashboard-test", 2);
        CountDownLatch bothStarted = new CountDownLatch(2);
        Set<String> threads = ConcurrentHashMap.newKeySet();
        Runnable read = () -> {
            threads.add(Thread.currentThread().getName());
            bothStarted.countDown();
            try {
                // each read waits for the other: this only finishes if they really run in parallel
                bothStarted.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        };
        pool.execute(read);
        pool.execute(read);
        assertTrue(bothStarted.await(5, TimeUnit.SECONDS));
        assertEquals(2, threads.size());
        assertNotEquals(Thread.currentThread().getName(), threads.iterator().next());
        pool.shutdown();
    }
}
