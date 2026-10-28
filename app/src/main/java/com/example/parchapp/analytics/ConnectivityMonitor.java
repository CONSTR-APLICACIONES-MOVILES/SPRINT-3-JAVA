package com.example.parchapp.analytics;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

class ConnectivityMonitor {

    interface Listener {
        void onConnectivityChanged(boolean online);
    }

    private final ConnectivityManager connectivityManager;
    private final Set<Listener> listeners = new CopyOnWriteArraySet<>();
    private volatile boolean online;

    ConnectivityMonitor(Context context) {
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        online = isActiveNetworkOnline();
        if (connectivityManager == null) {
            return;
        }
        connectivityManager.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                updateOnline(hasInternet(network));
            }

            @Override
            public void onCapabilitiesChanged(@NonNull Network network,
                                              @NonNull NetworkCapabilities capabilities) {
                updateOnline(hasInternet(capabilities));
            }

            @Override
            public void onLost(@NonNull Network network) {
                updateOnline(isActiveNetworkOnline());
            }
        });
    }

    boolean isOnline() {
        return online;
    }

    void addListener(Listener listener) {
        if (listeners.add(listener)) {
            listener.onConnectivityChanged(online);
        }
    }

    void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private boolean isActiveNetworkOnline() {
        return connectivityManager != null && hasInternet(connectivityManager.getActiveNetwork());
    }

    private boolean hasInternet(Network network) {
        if (network == null) {
            return false;
        }
        return connectivityManager != null && hasInternet(connectivityManager.getNetworkCapabilities(network));
    }

    private boolean hasInternet(NetworkCapabilities capabilities) {
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private void updateOnline(boolean newOnline) {
        if (online == newOnline) {
            return;
        }
        online = newOnline;
        for (Listener listener : listeners) {
            listener.onConnectivityChanged(newOnline);
        }
    }
}
