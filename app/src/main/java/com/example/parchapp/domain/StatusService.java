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
    private static final String KEY_EXPIRES_AT = "availability_expires_at";
    private static final String KEY_REVISION = "availability_revision";
    private static final String KEY_SYNC_PENDING = "availability_sync_pending";
    private static final String KEY_SYNC_ERROR = "availability_sync_error";
    private static final String KEY_SYNC_EXPIRED = "availability_sync_expired";
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
    private String userId = "";
    private final boolean remoteSyncAvailable;

    public StatusService(Context context) {
        this(context, false);
    }

    public StatusService(Context context, boolean remoteSyncAvailable) {
        Context appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.remoteSyncAvailable = remoteSyncAvailable;
        sensorManager = (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);
        linearAccelerationSensor = sensorManager == null ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        accelerometer = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    public String getFreeUntil() {
        return prefs.getString(key(KEY_FREE_UNTIL), "");
    }

    public String getNextActivity() {
        return prefs.getString(key(KEY_NEXT_ACTIVITY), "");
    }

    public String getAvailability() {
        return prefs.getString(key(KEY_AVAILABILITY), "Available now");
    }

    public String getAvailabilityDuration() {
        return prefs.getString(key(KEY_DURATION), "For 1 hour");
    }

    public boolean isAutoSyncEnabled() {
        return prefs.getBoolean(key(KEY_AUTO_SYNC), true);
    }

    public void setAutoSyncEnabled(boolean enabled) {
        long revision = getStatusRevision() + 1;
        prefs.edit().putBoolean(key(KEY_AUTO_SYNC), enabled)
                .putLong(key(KEY_REVISION), revision)
                .putBoolean(key(KEY_SYNC_PENDING), enabled && remoteSyncAvailable
                        && prefs.contains(key(KEY_AVAILABILITY))
                        && AvailabilityStatusExpiry.isValid(getAvailabilityExpiresAtMillis(), System.currentTimeMillis()))
                .putString(key(KEY_SYNC_ERROR), "")
                .putBoolean(key(KEY_SYNC_EXPIRED), false)
                .apply();
        notifyListeners();
    }

    /** In the following code the empty values keep the current one, matching the quick status sheet. */
    public void updateQuickStatus(String freeUntil, String nextActivity) {
        SharedPreferences.Editor editor = prefs.edit();
        if (!freeUntil.isEmpty()) {
            editor.putString(key(KEY_FREE_UNTIL), freeUntil);
        }
        if (!nextActivity.isEmpty()) {
            editor.putString(key(KEY_NEXT_ACTIVITY), nextActivity);
        }
        editor.apply();
        notifyListeners();
    }

    public void setAvailability(String availability, String duration) {
        saveAvailability(availability, duration,
                AvailabilityStatusExpiry.expiryFor(duration, System.currentTimeMillis()), false);
    }

    public void setAvailability(String availability, String duration, long expiresAtMillis) {
        saveAvailability(availability, duration, expiresAtMillis, false);
    }

    public void setAvailabilityFromMotion(String availability, String duration) {
        saveAvailability(availability, duration,
                AvailabilityStatusExpiry.expiryFor(duration, System.currentTimeMillis()), true);
    }

    private void saveAvailability(String availability, String duration, long expiresAtMillis, boolean fromMotion) {
        long revision = getStatusRevision() + 1;
        boolean pending = isAutoSyncEnabled() && remoteSyncAvailable;
        prefs.edit()
                .putString(key(KEY_AVAILABILITY), availability)
                .putString(key(KEY_DURATION), duration)
                .putLong(key(KEY_EXPIRES_AT), expiresAtMillis)
                .putLong(key(KEY_REVISION), revision)
                .putBoolean(key(KEY_SYNC_PENDING), pending)
                .putString(key(KEY_SYNC_ERROR), "")
                .putBoolean(key(KEY_SYNC_EXPIRED), false)
                .putBoolean(key(KEY_FROM_MOTION), fromMotion)
                .apply();
        notifyListeners();
    }

    /* F1: the live status card shows "Motion sensor" instead of "Live Sync" when this is true. */
    public boolean isFromMotion() {
        return prefs.getBoolean(key(KEY_FROM_MOTION), false);
    }

    public String getUserId() { return userId; }

    public boolean hasSavedAvailability() { return prefs.contains(key(KEY_AVAILABILITY)); }

    public boolean isRemoteSyncAvailable() { return remoteSyncAvailable; }

    public boolean isStatusSyncPending() { return prefs.getBoolean(key(KEY_SYNC_PENDING), false); }

    public long getStatusRevision() { return prefs.getLong(key(KEY_REVISION), 0L); }

    public long getAvailabilityExpiresAtMillis() {
        long stored = prefs.getLong(key(KEY_EXPIRES_AT), 0L);
        return stored > 0 ? stored
                : AvailabilityStatusExpiry.expiryFor(getAvailabilityDuration(), System.currentTimeMillis());
    }

    public boolean isAvailabilityExpired() {
        return hasSavedAvailability() && getAvailabilityExpiresAtMillis() <= System.currentTimeMillis();
    }

    public String getStatusSyncError() { return prefs.getString(key(KEY_SYNC_ERROR), ""); }

    public boolean isStatusExpiredBeforeSync() { return prefs.getBoolean(key(KEY_SYNC_EXPIRED), false); }

    public void markStatusSynced(long revision) {
        if (getStatusRevision() != revision || !isStatusSyncPending()) return;
        prefs.edit().putBoolean(key(KEY_SYNC_PENDING), false)
                .putString(key(KEY_SYNC_ERROR), "").apply();
        notifyListeners();
    }

    public void markStatusSyncError(long revision, String message) {
        if (getStatusRevision() != revision || !isStatusSyncPending()) return;
        prefs.edit().putString(key(KEY_SYNC_ERROR), message == null ? "Could not sync status" : message).apply();
        notifyListeners();
    }

    public void markStatusExpiredBeforeSync(long revision) {
        if (getStatusRevision() != revision || !isStatusSyncPending()) return;
        prefs.edit().putBoolean(key(KEY_SYNC_PENDING), false)
                .putBoolean(key(KEY_SYNC_EXPIRED), true)
                .putString(key(KEY_SYNC_ERROR), "This status expired before it could be shared. Update it to share again.")
                .apply();
        notifyListeners();
    }

    /** Selects status preferences for the authenticated account and migrates the previous global keys once. */
    public void setUserId(String nextUserId) {
        String next = nextUserId == null ? "" : nextUserId.trim();
        if (userId.equals(next)) return;
        if (!next.isEmpty() && !prefs.contains(scopedKey(KEY_AVAILABILITY, next))
                && prefs.contains(KEY_AVAILABILITY)) {
            SharedPreferences.Editor editor = prefs.edit();
            copyLegacy(editor, KEY_AVAILABILITY, next);
            copyLegacy(editor, KEY_DURATION, next);
            copyLegacy(editor, KEY_AUTO_SYNC, next);
            copyLegacy(editor, KEY_FREE_UNTIL, next);
            copyLegacy(editor, KEY_NEXT_ACTIVITY, next);
            copyLegacy(editor, KEY_FROM_MOTION, next);
            copyLegacy(editor, KEY_LAST_SUGGESTION, next);
            long migratedExpiry = AvailabilityStatusExpiry.expiryFor(
                    prefs.getString(KEY_DURATION, "For 1 hour"), System.currentTimeMillis());
            editor.putLong(scopedKey(KEY_EXPIRES_AT, next), migratedExpiry)
                    .putLong(scopedKey(KEY_REVISION, next), 1L)
                    .putBoolean(scopedKey(KEY_SYNC_PENDING, next), remoteSyncAvailable
                            && prefs.getBoolean(KEY_AUTO_SYNC, true))
                    .putString(scopedKey(KEY_SYNC_ERROR, next), "")
                    .putBoolean(scopedKey(KEY_SYNC_EXPIRED, next), false);
            editor.remove(KEY_AVAILABILITY).remove(KEY_DURATION).remove(KEY_AUTO_SYNC)
                    .remove(KEY_FREE_UNTIL).remove(KEY_NEXT_ACTIVITY).remove(KEY_FROM_MOTION)
                    .remove(KEY_LAST_SUGGESTION).apply();
        }
        userId = next;
        notifyListeners();
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
        if (now - prefs.getLong(key(KEY_LAST_SUGGESTION), 0L) < SUGGESTION_COOLDOWN_MS) return;
        prefs.edit().putLong(key(KEY_LAST_SUGGESTION), now).apply();
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

    private String key(String base) { return scopedKey(base, userId); }

    private static String scopedKey(String base, String owner) {
        return owner == null || owner.isEmpty() ? base : base + "_" + owner;
    }

    private void copyLegacy(SharedPreferences.Editor editor, String legacyKey, String owner) {
        String scoped = scopedKey(legacyKey, owner);
        Object value = prefs.getAll().get(legacyKey);
        if (value instanceof String) editor.putString(scoped, (String) value);
        else if (value instanceof Boolean) editor.putBoolean(scoped, (Boolean) value);
        else if (value instanceof Long) editor.putLong(scoped, (Long) value);
        else if (value instanceof Integer) editor.putInt(scoped, (Integer) value);
    }
}
