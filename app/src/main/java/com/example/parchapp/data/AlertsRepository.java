package com.example.parchapp.data;

import com.example.parchapp.domain.model.AlertItem;
import com.example.parchapp.util.Callback;

import java.util.List;

/** Backend access for the user's alert inbox and invitation actions. */
public interface AlertsRepository {
    void getAlerts(Callback<List<AlertItem>> callback);

    void markAlertsRead(List<String> ids, Callback<Void> callback);

    void respondToActivityInvitation(String activityId, String response, Callback<Void> callback);

    void respondToGroupInvitation(String groupId, String response, Callback<Void> callback);
}
