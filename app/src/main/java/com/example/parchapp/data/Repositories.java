package com.example.parchapp.data;

import android.content.Context;

import com.example.parchapp.domain.StatusService;
import com.example.parchapp.BuildConfig;
import com.google.firebase.FirebaseApp;
import com.google.firebase.functions.FirebaseFunctions;
/* Sprint 3 base composition: keeps the view contracts while Sprint 2 features are reintroduced on feature branches. */
public final class Repositories {
    private static boolean firebaseAvailable;
    private static GroupRepository groups;
    private static ActivityRepository activities;
    private static AuthRepository auth;
    private static StatusService status;
    private static ScheduleRepository schedules;

    private Repositories() {
    }

    public static void init(Context context) {
        if (groups != null) {
            return;
        }
        firebaseAvailable = !FirebaseApp.getApps(context).isEmpty();
        groups = new EmptyGroupRepository();
        if (firebaseAvailable) {
            FirebaseFunctions functions = FirebaseFunctions.getInstance("us-central1");
            if (BuildConfig.USE_FIREBASE_EMULATORS) {
                functions.useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, 5001);
            }
            activities = new FirebaseActivityRepository(functions);
        } else {
            activities = new DisabledActivityRepository();
        }
        auth = new DisabledAuthRepository();
        schedules = new EmptyScheduleRepository();
        status = new StatusService(context);
    }

    public static boolean isFirebaseAvailable() {
        return firebaseAvailable;
    }

    public static boolean isFirebaseAnalyticsAvailable() {
        return firebaseAvailable;
    }

    public static GroupRepository groups() {
        return groups;
    }

    public static ActivityRepository activities() {
        return activities;
    }

    public static AuthRepository auth() {
        return auth;
    }

    public static ScheduleRepository schedules() {
        return schedules;
    }

    public static StatusService status() {
        return status;
    }
}
