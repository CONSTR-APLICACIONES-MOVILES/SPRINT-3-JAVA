package com.example.parchapp.data;

import com.example.parchapp.domain.model.PlannedActivity;
import com.example.parchapp.util.Callback;

import java.util.List;

public interface ActivityRepository {
    /*Saves a new activity and returns it with its generated ID. Succeeds once the activity is stored on the device; it reaches the server in the background, even after being offline.
     */
    void createActivity(PlannedActivity activity, Callback<PlannedActivity> callback);

    void getActivities(String userId, Callback<List<PlannedActivity>> callback);
}
