package com.example.parchapp.data;

import com.example.parchapp.domain.model.AlertItem;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;

import java.util.List;

/** Reports unavailable alert actions when Firebase isn't configured. */
final class DisabledAlertsRepository implements AlertsRepository {
    private Exception unavailable() {
        return new UnsupportedOperationException("Alerts are unavailable without the backend.");
    }

    @Override
    public void getAlerts(Callback<List<AlertItem>> callback) {
        MainThread.post(() -> callback.onError(unavailable()));
    }

    @Override
    public void markAlertsRead(List<String> ids, Callback<Void> callback) {
        MainThread.post(() -> callback.onError(unavailable()));
    }

    @Override
    public void respondToActivityInvitation(String activityId, String response, Callback<Void> callback) {
        MainThread.post(() -> callback.onError(unavailable()));
    }

    @Override
    public void respondToGroupInvitation(String groupId, String response, Callback<Void> callback) {
        MainThread.post(() -> callback.onError(unavailable()));
    }
}
