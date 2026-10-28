package com.example.parchapp.analytics;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.annotation.NonNull;

class ConnectivityMonitor {

    interface Listener {
        /*Listener is called on a background threaad when the device gets a connection back. */
        void onOnline();
    }

    private final ConnectivityManager connectivityManager;
    private volatile boolean online;

    ConnectivityMonitor(Context context, Listener listener) {
        connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        online = hasInternet(connectivityManager.getActiveNetwork());
        connectivityManager.registerDefaultNetworkCallback(new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                online = true;
                listener.onOnline();
            }

            @Override
            public void onLost(@NonNull Network network) {
                online = false;
            }
        });
    }

    boolean isOnline() {
        return online;
    }

    private boolean hasInternet(Network network) {
        if (network == null) {
            return false;
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        return capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
