package com.example.parchapp.domain;

import com.example.parchapp.domain.model.PlannedActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

// the plans of one group, the groups list and group details use the same rules so they always say the same
public final class GroupPlans {
// a group with this many plans or more is the most active
    public static final int MOST_ACTIVE_PLANS = 3;

    public enum Activity { MOST_ACTIVE, ACTIVE, LOW }

    private GroupPlans() {
    }

// keeps only the plans of this group
    public static List<PlannedActivity> of(String groupId, List<PlannedActivity> plans) {
        List<PlannedActivity> result = new ArrayList<>();
        for (PlannedActivity plan : plans) {
            if (groupId.equals(plan.getGroupId())) {
                result.add(plan);
            }
        }
        return result;
    }

// the next plan that has not ended, and when nothing is coming the newest one, like the dashboard card
    public static PlannedActivity nextFor(String groupId, List<PlannedActivity> plans, Calendar now) {
        List<PlannedActivity> groupPlans = of(groupId, plans);
        int nowMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        PlannedActivity next = PlanContext.nextPlan(groupPlans, now, nowMinute);
        if (next == null && !groupPlans.isEmpty()) {
            next = groupPlans.get(0);
        }
        return next;
    }

// how active the group is, from how many plans it has
    public static Activity activityOf(String groupId, List<PlannedActivity> plans) {
        int count = of(groupId, plans).size();
        if (count >= MOST_ACTIVE_PLANS) {
            return Activity.MOST_ACTIVE;
        }
        return count > 0 ? Activity.ACTIVE : Activity.LOW;
    }
}
