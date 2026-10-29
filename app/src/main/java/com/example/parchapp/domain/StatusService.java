package com.example.parchapp.domain;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.ArrayList;
import java.util.List;

/* The scenario owns the availability the user broadcasts to their groups: the quick status shown on the dashboard and the status chosen in the availability sheet. Kept on the device so it survives a process restart.
 */
public class StatusService implements SensorEventListener {

    public interface Listener {
        void onStatusChanged(StatusService status);

        default void onCommuteSuggested(StatusService status) { }

    }

    private static final String PREFS = "parchapp_status";
    private static final String KEY_FREE_UNTIL = "free_until";
    private static final String KEY_NEXT_ACTIVITY = "next_activity";
    private static final String KEY_AVAILABILITY = "availability";
    private static final String KEY_DURATION = "availability_duration";
    private static final String KEY_AUTO_SYNC = "availability_auto_sync";
    // f1: true when the status came from the motion sensor suggestion, the sensor feature writes it when the user accepts
    static final String KEY_FROM_MOTION = "availability_from_motion";
    private static final long SUGGESTION_COOLDOWN_MS = 10 * 60 * 1000L;
    private static final String KEY_LAST_SUGGESTION = "motion_suggestion_at";

    private final SharedPreferences prefs;
    private final SensorManager sensorManager;
    private final Sensor linearAccelerationSensor;
    private final Sensor accelerometer;
    private final List<Listener> listeners = new ArrayList<>();
    private final SustainedMotionDetector motionDetector = new SustainedMotionDetector();
    private Sensor activeSensor;

    public StatusService(Context context) {
        Context appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sensorManager = (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);
        linearAccelerationSensor = sensorManager == null ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        accelerometer = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    public String getFreeUntil() {
        return prefs.getString(KEY_FREE_UNTIL, "");
    }

    public String getNextActivity() {
        return prefs.getString(KEY_NEXT_ACTIVITY, "");
    }

    public String getAvailability() {
        return prefs.getString(KEY_AVAILABILITY, "Available now");
    }

    public String getAvailabilityDuration() {
        return prefs.getString(KEY_DURATION, "For 1 hour");
    }

    public boolean isAutoSyncEnabled() {
        return prefs.getBoolean(KEY_AUTO_SYNC, true);
    }

    public void setAutoSyncEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply();
    }

    /** In the following code the empty values keep the current one, matching the quick status sheet. */
    public void updateQuickStatus(String freeUntil, String nextActivity) {
        SharedPreferences.Editor editor = prefs.edit();
        if (!freeUntil.isEmpty()) {
            editor.putString(KEY_FREE_UNTIL, freeUntil);
        }
        if (!nextActivity.isEmpty()) {
            editor.putString(KEY_NEXT_ACTIVITY, nextActivity);
        }
        editor.apply();
        notifyListeners();
    }

    public void setAvailability(String availability, String duration) {
        saveAvailability(availability, duration, false);
    }

    public void setAvailabilityFromMotion(String availability, String duration) {
        saveAvailability(availability, duration, true);
    }

    private void saveAvailability(String availability, String duration, boolean fromMotion) {
        prefs.edit()
                .putString(KEY_AVAILABILITY, availability)
                .putString(KEY_DURATION, duration)
                .putBoolean(KEY_FROM_MOTION, fromMotion)
                .apply();
        notifyListeners();
    }

    /* F1: the live status card shows "Motion sensor" instead of "Live Sync" when this is true. */
    public boolean isFromMotion() {
        return prefs.getBoolean(KEY_FROM_MOTION, false);
    }

    public boolean isMotionDetectionAvailable() {
        return linearAccelerationSensor != null || accelerometer != null;
    }

    /** Samples movement only while the availability sheet is visible. */
    public boolean startMotionDetection() {
        if (sensorManager == null || activeSensor != null) {
            return activeSensor != null;
        }
        activeSensor = linearAccelerationSensor != null ? linearAccelerationSensor : accelerometer;
        if (activeSensor == null) {
            return false;
        }
        motionDetector.reset();
        boolean registered = sensorManager.registerListener(this, activeSensor, SensorManager.SENSOR_DELAY_NORMAL);
        if (!registered) activeSensor = null;
        return registered;
    }

    public void stopMotionDetection() {
        if (sensorManager != null && activeSensor != null) {
            sensorManager.unregisterListener(this, activeSensor);
        }
        activeSensor = null;
        motionDetector.reset();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        boolean sustained = event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION
                ? motionDetector.addLinearAccelerationSample(event.values[0], event.values[1], event.values[2])
                : motionDetector.addAccelerometerSample(event.values[0], event.values[1], event.values[2]);
        if (sustained) notifyCommuteSuggestion();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private void notifyCommuteSuggestion() {
        if ("Commute / In Transit".equals(getAvailability())) return;
        long now = System.currentTimeMillis();
        if (now - prefs.getLong(KEY_LAST_SUGGESTION, 0L) < SUGGESTION_COOLDOWN_MS) return;
        prefs.edit().putLong(KEY_LAST_SUGGESTION, now).apply();
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onCommuteSuggested(this);
        }
    }

    public void addListener(Listener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onStatusChanged(this);
        }
    }
}
