package com.example.parchapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

// local storage: the Schedule days in room, on a real sqlite of the device but in memory, so nothing stays after
@RunWith(AndroidJUnit4.class)
public class ScheduleDayDaoTest {
    private ParchDatabase db;
    private ScheduleDayDao dao;

    @Before
    public void open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().getTargetContext(),
                ParchDatabase.class).build();
        dao = db.scheduleDays();
    }

    @After
    public void close() {
        db.close();
    }

// with more days than the limit, only the newest ones stay
    @Test
    public void keepsOnlyTheNewestDays() {
        for (int day = 1; day <= 4; day++) {
            dao.save(ScheduleDayEntity.of("ana", "d" + day, "[]", day), 3);
        }
        assertEquals(3, dao.count("ana"));
        assertNull(dao.day("ana", "d1"));
        assertNotNull(dao.day("ana", "d4"));
    }

// saving a day again replaces its row and makes it the newest, so it is not the one deleted
    @Test
    public void savingAgainReplacesAndRefreshes() {
        dao.save(ScheduleDayEntity.of("ana", "d1", "old", 1), 2);
        dao.save(ScheduleDayEntity.of("ana", "d2", "[]", 2), 2);
        dao.save(ScheduleDayEntity.of("ana", "d1", "new", 3), 2);
        dao.save(ScheduleDayEntity.of("ana", "d3", "[]", 4), 2);
        assertEquals("new", dao.day("ana", "d1").json);
        assertNull(dao.day("ana", "d2"));
    }

// the limit and the reads are per account, another user on the same phone never sees or deletes these days
    @Test
    public void eachAccountHasItsOwnDays() {
        dao.save(ScheduleDayEntity.of("ana", "d1", "[]", 1), 1);
        dao.save(ScheduleDayEntity.of("luis", "d1", "[]", 2), 1);
        dao.save(ScheduleDayEntity.of("luis", "d2", "[]", 3), 1);
        assertNotNull(dao.day("ana", "d1"));
        assertEquals(1, dao.count("luis"));
    }

// offline Schedule asks only for my day: the newest copy of that date is found whoever was compared in it
    @Test
    public void findsTheNewestCopyOfADate() {
        dao.save(ScheduleDayEntity.of("ana", "2026-11-25.luis,maria", "older", 1), 10);
        dao.save(ScheduleDayEntity.of("ana", "2026-11-25.luis", "newer", 2), 10);
        dao.save(ScheduleDayEntity.of("ana", "2026-11-26.", "other day", 3), 10);
        dao.save(ScheduleDayEntity.of("luis", "2026-11-25.", "other account", 4), 10);
        assertEquals("newer", dao.newestOfDate("ana", "2026-11-25").json);
        assertNull(dao.newestOfDate("ana", "2026-11-27"));
    }
}
