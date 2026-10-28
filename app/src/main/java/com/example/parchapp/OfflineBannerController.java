package com.example.parchapp;

import android.view.View;

import com.example.parchapp.analytics.AnalyticsTracker;

/** Shows the shared offline banner while an offline-aware screen is visible. */
final class OfflineBannerController {
    private final AnalyticsTracker tracker = AnalyticsTracker.getInstance();
    private final AnalyticsTracker.ConnectivityListener listener = this::render;
    private View banner;

    void start(View banner) {
        this.banner = banner;
        tracker.addConnectivityListener(listener);
    }

    void stop() {
        tracker.removeConnectivityListener(listener);
        banner = null;
    }

    private void render(boolean online) {
        View currentBanner = banner;
        if (currentBanner == null) {
            return;
        }
        currentBanner.post(() -> {
            if (banner == currentBanner) {
                currentBanner.setVisibility(online ? View.GONE : View.VISIBLE);
            }
        });
    }
}
