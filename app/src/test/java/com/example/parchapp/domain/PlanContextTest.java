package com.example.parchapp.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;

public class PlanContextTest {

    private static Calendar day(int year, int month, int dayOfMonth) {
        Calendar calendar = Calendar.getInstance(PlanContext.bogota());
        calendar.clear();
        calendar.set(year, month, dayOfMonth, 12, 0);
        return calendar;
    }

    @Test
    public void readsStartAndEndOfATimeRange() {
        assertEquals(17 * 60, PlanContext.startMinute("5:00 PM – 6:00 PM"));
        assertEquals(18 * 60, PlanContext.endMinute("5:00 PM – 6:00 PM"));
        assertEquals(12 * 60 + 30, PlanContext.startMinute("12:30 PM"));
        assertEquals(30, PlanContext.startMinute("12:30 AM"));
        assertEquals(17 * 60 + 30, PlanContext.startMinute("17:30"));
        assertEquals(-1, PlanContext.startMinute("Friday"));
        assertEquals(-1, PlanContext.endMinute("7:30 PM"));
    }

    @Test
    public void recognisesTodayInTheFormatsTheAppWrites() {
        Calendar now = day(2026, Calendar.OCTOBER, 27);
        assertTrue(PlanContext.isToday("Tue, Oct 27, 2026", now));
        assertTrue(PlanContext.isToday("2026-10-27", now));
        assertTrue(PlanContext.isToday("Tonight", now));
        assertFalse(PlanContext.isToday("Wed, Oct 28, 2026", now));
        assertFalse(PlanContext.isToday("2025-10-27", now));
        assertFalse(PlanContext.isToday("Friday", now));
    }

    private static com.example.parchapp.domain.model.PlannedActivity plan(String title, String date, String time) {
        return new com.example.parchapp.domain.model.PlannedActivity("id-" + title, "g", title, "other", date, time,
                "", "PROPOSED", 0);
    }

    @Test
    public void picksTheNextPlanAndCountsToday() {
        Calendar now = day(2026, Calendar.OCTOBER, 27);
        java.util.List<com.example.parchapp.domain.model.PlannedActivity> plans = java.util.Arrays.asList(
                plan("over", "Tue, Oct 27, 2026", "8:00 AM - 9:00 AM"),
                plan("later", "Tue, Oct 27, 2026", "5:00 PM - 6:00 PM"),
                plan("soon", "Tue, Oct 27, 2026", "1:30 PM - 2:30 PM"),
                plan("tomorrow", "2026-10-28", "9:00 AM"),
                plan("yesterday", "2026-10-26", "9:00 AM"),
                plan("undated", "", "9:00 AM"));
        assertEquals(3, PlanContext.countToday(plans, now));
        assertEquals("soon", PlanContext.nextPlan(plans, now, 13 * 60).getTitle());
        assertEquals("tomorrow", PlanContext.nextPlan(plans, now, 19 * 60).getTitle());
        assertEquals(Integer.valueOf(1), PlanContext.dayOffset("2026-10-28", now));
        assertEquals(null, PlanContext.dayOffset("Friday", now));
    }
}
