package com.example.parchapp;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/* The app targets Android 15+, where every screen is drawn behind the status bar and the navigation bar
 * (edge-to-edge can not be turned off anymore). On phones with the 3-button navigation bar it covered the
 * buttons at the bottom of the screens. This pads the content of every screen by the size of the system bars
 * and of the keyboard, once, when the screen starts (its layout is ready by then). */
final class SystemBarsInsets implements Application.ActivityLifecycleCallbacks {

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (content == null || content.getTag(R.id.system_bars_insets_applied) != null) {
            return;
        }
        content.setTag(R.id.system_bars_insets_applied, Boolean.TRUE);
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            Insets keyboard = windowInsets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, keyboard.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(content);
    }

    @Override public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle state) { }
    @Override public void onActivityResumed(@NonNull Activity activity) { }
    @Override public void onActivityPaused(@NonNull Activity activity) { }
    @Override public void onActivityStopped(@NonNull Activity activity) { }
    @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle state) { }
    @Override public void onActivityDestroyed(@NonNull Activity activity) { }
}
