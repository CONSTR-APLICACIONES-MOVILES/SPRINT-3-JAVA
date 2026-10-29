package com.example.parchapp.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.parchapp.domain.model.PlannedActivity;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

// tests for the rules the groups list and group details share
public class GroupPlansTest {

    private static PlannedActivity plan(String id, String groupId) {
        return new PlannedActivity(id, groupId, "Plan " + id, "study", "", "", "Library", "PROPOSED", 0L);
    }

    private final List<PlannedActivity> plans = Arrays.asList(
            plan("a", "study"), plan("b", "study"), plan("c", "study"), plan("d", "social"));

// only the plans of the group are counted
    @Test
    public void keepsOnlyThePlansOfTheGroup() {
        assertEquals(3, GroupPlans.of("study", plans).size());
        assertEquals(1, GroupPlans.of("social", plans).size());
        assertEquals(0, GroupPlans.of("empty", plans).size());
    }

// 3 plans or more is most active, 1 or 2 is active, 0 is low
    @Test
    public void activityComesFromTheNumberOfPlans() {
        assertEquals(GroupPlans.Activity.MOST_ACTIVE, GroupPlans.activityOf("study", plans));
        assertEquals(GroupPlans.Activity.ACTIVE, GroupPlans.activityOf("social", plans));
        assertEquals(GroupPlans.Activity.LOW, GroupPlans.activityOf("empty", plans));
    }

// a group with plans always has a plan to show, a group without plans has none
    @Test
    public void nextPlanBelongsToTheGroup() {
        Calendar now = Calendar.getInstance(PlanContext.bogota());
        assertEquals("social", GroupPlans.nextFor("social", plans, now).getGroupId());
        assertEquals("study", GroupPlans.nextFor("study", plans, now).getGroupId());
        assertNull(GroupPlans.nextFor("empty", plans, now));
        assertNull(GroupPlans.nextFor("study", Collections.emptyList(), now));
    }
}
