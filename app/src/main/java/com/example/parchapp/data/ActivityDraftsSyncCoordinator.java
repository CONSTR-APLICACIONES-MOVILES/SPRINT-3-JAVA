package com.example.parchapp.data;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;

import com.example.parchapp.util.Callback;

/** Option A for drafts: retry the local outbox when a network becomes available, without WorkManager. */
public final class ActivityDraftsSyncCoordinator {
    private static final Callback<Void> NO_OP = new Callback<Void>() {
        @Override public void onSuccess(Void ignored) { }
        @Override public void onError(Exception error) { }
    };
    private final ActivityDraftsRepository repository;
    private final ConnectivityManager connectivity;
    private boolean started;

    public ActivityDraftsSyncCoordinator(Context context, ActivityDraftsRepository repository) {
        this.repository = repository;
        this.connectivity = (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public void start() {
        if (started || connectivity == null) return;
        started = true;
        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        connectivity.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network network) {
                repository.sync(NO_OP);
            }
        });
        repository.sync(NO_OP);
    }
}
