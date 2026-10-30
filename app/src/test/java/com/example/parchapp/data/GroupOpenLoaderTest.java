package com.example.parchapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.parchapp.domain.model.PlannedActivity;
import com.example.parchapp.util.BackgroundThreads;

import org.junit.After;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

// tests for the tasks inside a task of group details
public class GroupOpenLoaderTest {

    private final ExecutorService children = BackgroundThreads.pool("parch-group-test", 2);

    @After
    public void stopPool() {
        children.shutdown();
    }

// each child waits for the other one: this only finishes if the two reads really run at the same time
    @Test
    public void childrenRunAtTheSameTime() {
        CountDownLatch bothStarted = new CountDownLatch(2);
        List<PlannedActivity> none = Collections.emptyList();
        GroupOpenLoader.Opened opened = GroupOpenLoader.open(children, () -> {
            bothStarted.countDown();
            assertTrue(bothStarted.await(5, TimeUnit.SECONDS));
            return 1234L;
        }, () -> {
            bothStarted.countDown();
            assertTrue(bothStarted.await(5, TimeUnit.SECONDS));
            return none;
        });
        assertEquals(1234L, opened.lastSynced);
        assertEquals(none, opened.plans);
    }

// without the plans the sync time still arrives
    @Test
    public void aFailedReadDoesNotLoseTheOther() {
        GroupOpenLoader.Opened opened = GroupOpenLoader.open(children, () -> 1234L, () -> {
            throw new IOException("offline");
        });
        assertEquals(1234L, opened.lastSynced);
        assertNull(opened.plans);
    }

// without the sync time the plans still arrive, and -1 means it never synced
    @Test
    public void failedSyncTimeMeansNeverSynced() {
        List<PlannedActivity> none = Collections.emptyList();
        GroupOpenLoader.Opened opened = GroupOpenLoader.open(children, () -> {
            throw new IOException("no disk");
        }, () -> none);
        assertEquals(-1L, opened.lastSynced);
        assertEquals(none, opened.plans);
    }
}
