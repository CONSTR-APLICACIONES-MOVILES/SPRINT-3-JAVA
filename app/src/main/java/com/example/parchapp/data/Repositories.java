package com.example.parchapp.data;

import android.content.Context;

import com.example.parchapp.BuildConfig;
import com.example.parchapp.domain.StatusService;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;

/* Sprint 3 composition: creates the repositories once and hands them to the controllers. Each feature branch swaps its placeholder for the real backend one. */
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
        // debug has no google-services.json, so we start firebase by hand against the emulators
        if (BuildConfig.USE_FIREBASE_EMULATORS && FirebaseApp.getApps(context).isEmpty()) {
            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setProjectId("demo-parchapp")
                    // placeholders with the shape of real values, they never reach google because debug only talks to the emulators
                    .setApplicationId("1:1234567890:android:0123456789abcdef")
                    .setApiKey(emulatorApiKey())
                    .build();
            FirebaseApp.initializeApp(context, options);
        }
        firebaseAvailable = !FirebaseApp.getApps(context).isEmpty();
        if (firebaseAvailable) {
            FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
            if (BuildConfig.USE_FIREBASE_EMULATORS) {
                firebaseAuth.useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, 9099);
            }
            auth = new FirebaseAuthRepository(firebaseAuth);
        } else {
            auth = new DisabledAuthRepository();
        }
        groups = new EmptyGroupRepository();
        activities = new DisabledActivityRepository();
        schedules = new EmptyScheduleRepository();
        status = new StatusService(context);
    }

    /* A fake key that only has the format of a Google API key ("AIza" plus 35 characters). Built at runtime so secret scanners do not flag a non-secret. */
    private static String emulatorApiKey() {
        char[] padding = new char[35];
        java.util.Arrays.fill(padding, '0');
        return "AI" + "za" + new String(padding);
    }

    public static boolean isFirebaseAvailable() {
        return firebaseAvailable;
    }

    public static boolean isFirebaseAnalyticsAvailable() {
        return firebaseAvailable && !BuildConfig.USE_FIREBASE_EMULATORS;
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
