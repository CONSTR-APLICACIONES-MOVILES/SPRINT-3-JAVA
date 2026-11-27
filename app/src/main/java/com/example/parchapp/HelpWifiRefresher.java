package com.example.parchapp;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;

import androidx.annotation.NonNull;

import com.example.parchapp.data.HelpRepository;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// eventual connectivity: the help is downloaded only on Wi-Fi, at most once a day, and never with low battery
// (Conectivity 2.0: refresh only on Wi-Fi + low battery scenarios). With mobile data nothing is downloaded,
// because the questions change little and are not worth the data plan of the user.
// for a demo, lower EXPIRATION to one minute (and RETRY_WAIT in HelpRepository), and put it back after
final class HelpWifiRefresher extends ConnectivityManager.NetworkCallback {
    private static final long EXPIRATION = TimeUnit.HOURS.toMillis(24);
    private static final float LOW_BATTERY_PERCENT = 15f;
    private static final AtomicBoolean registered = new AtomicBoolean(false);

    private final Context context;

    private HelpWifiRefresher(Context context) {
        this.context = context.getApplicationContext();
    }

    // TopHeader calls this on every main screen; the callback is registered only once
    static void registerOnce(Context context) {
        if (!registered.compareAndSet(false, true)) return;
        ConnectivityManager manager = context.getSystemService(ConnectivityManager.class);
        if (manager == null) return;
        manager.registerDefaultNetworkCallback(new HelpWifiRefresher(context));
    }

    // runs on a system thread: it only decides, the download itself runs on the help pool
    @Override
    public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities caps) {
        boolean wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        boolean internet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        if (!wifi || !internet) return;
        HelpRepository help = HelpRepository.get(context);
        boolean expired = System.currentTimeMillis() - help.lastSync() > EXPIRATION;
        if (expired && !batteryLow()) {
            help.refreshFromNetwork();
        }
    }

    // the same check as the class slide: battery level and charging state from ACTION_BATTERY_CHANGED
    private boolean batteryLow() {
        Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return false;
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
        if (level < 0 || scale <= 0) return false; // unknown level: do not block the download
        return !charging && level * 100f / scale < LOW_BATTERY_PERCENT;
    }
}
