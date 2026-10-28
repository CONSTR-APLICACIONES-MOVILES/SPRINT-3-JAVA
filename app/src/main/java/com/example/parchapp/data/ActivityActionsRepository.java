package com.example.parchapp.data;

import com.example.parchapp.domain.model.ActivityDetails;
import com.example.parchapp.domain.model.PlannedActivity;
import com.example.parchapp.util.Callback;

/** Creates activities and configures their backend-owned details/reminders. */
public interface ActivityActionsRepository {
    void createActivity(PlannedActivity activity, Callback<PlannedActivity> callback);

    void getActivityDetails(String activityId, Callback<ActivityDetails> callback);

    void setActivityReminder(String activityId, int minutesBefore, Callback<Void> callback);
}
