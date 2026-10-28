package com.example.parchapp.util;

import android.os.Handler;
import android.os.Looper;

public final class MainThread {
    private static Handler handler;

    private MainThread() {
    }

    public static synchronized void post(Runnable runnable) {
        if (handler == null) {
            handler = new Handler(Looper.getMainLooper());
        }
        handler.post(runnable);
    }
}
