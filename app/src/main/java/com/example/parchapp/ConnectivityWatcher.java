package com.example.parchapp;

import android.view.View;

import androidx.annotation.Nullable;

import com.example.parchapp.analytics.AnalyticsTracker;

/* Eventual connectivity for a screen: shows the shared offline banner while there is no network and, when the
 * network comes back, runs onReconnect (usually "load again"), so saved or empty data is replaced by fresh data
 * without the user reopening the screen. Start it in onStart and stop it in onStop. */
final class ConnectivityWatcher {
    private final OfflineBannerController banner = new OfflineBannerController();
    @Nullable
    private final Runnable onReconnect;
    private View anchor;
    private boolean wasOnline = true;
    private final AnalyticsTracker.ConnectivityListener listener = this::onChanged;

    ConnectivityWatcher(@Nullable Runnable onReconnect) {
        this.onReconnect = onReconnect;
    }

    void start(View offlineBanner) {
        anchor = offlineBanner;
        banner.start(offlineBanner);
        wasOnline = AnalyticsTracker.getInstance().isOnline();
        AnalyticsTracker.getInstance().addConnectivityListener(listener);
    }

    void stop() {
        AnalyticsTracker.getInstance().removeConnectivityListener(listener);
        banner.stop();
        anchor = null;
    }

    // the callback can come from a background thread, the screen is touched on the main one
    private void onChanged(boolean online) {
        View view = anchor;
        if (view == null) {
            return;
        }
        view.post(() -> {
            boolean cameBack = online && !wasOnline;
            wasOnline = online;
            if (cameBack && anchor != null && onReconnect != null) {
                onReconnect.run();
            }
        });
    }
}
