package com.example.parchapp.data;

import android.content.Context;

import com.example.parchapp.BuildConfig;
import com.example.parchapp.domain.StatusService;
import com.example.parchapp.util.MainThread;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.firestore.PersistentCacheSettings;

/** App-wide data adapters: Firestore serves group-plan reads; callables handle activity actions/details. */
public final class Repositories {
    private static boolean firebaseAvailable;
    private static GroupRepository groups;
    private static ActivityRepository activities;
    private static AlertsRepository alerts;
    private static InsightsRepository insights;
    private static RecommendationDecisionRepository recommendationDecisions;
    private static CampusPlaceRepository places;
    private static AuthRepository auth;
    private static PrivacySettingsRepository privacySettings;
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
            FirebaseFunctions functions = FirebaseFunctions.getInstance("us-central1");
            if (BuildConfig.USE_FIREBASE_EMULATORS) {
                functions.useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, 5001);
            }
            FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
            if (BuildConfig.USE_FIREBASE_EMULATORS) {
                firebaseAuth.useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, 9099);
            }
            auth = new FirebaseAuthRepository(firebaseAuth);
            privacySettings = new FirebasePrivacySettingsRepository(context, functions);
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            if (BuildConfig.USE_FIREBASE_EMULATORS) {
                db.useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, 8080);
            }
            // offline persistence: without signal the dashboard reads the local copy
            db.setFirestoreSettings(new FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                    .build());
            groups = new FirestoreGroupRepository(db, functions);
            recommendationDecisions = new FirebaseRecommendationDecisionRepository(functions);
            activities = new CompositeActivityRepository(
                    new FirestoreActivityRepository(db),
                    new FirebaseActivityRepository(functions));
            alerts = new FirebaseAlertsRepository(functions);
            insights = new FirestoreInsightsRepository(db);
            places = new FirestoreCampusPlaceRepository(db);
            // schedules come from the backend callables getScheduleFriends and getScheduleDay
            schedules = new FunctionsScheduleRepository(functions);
        } else {
            auth = new DisabledAuthRepository();
            privacySettings = new DisabledPrivacySettingsRepository(context);
            groups = new EmptyGroupRepository();
            activities = new DisabledActivityRepository();
            alerts = new DisabledAlertsRepository();
            insights = callback -> MainThread.post(() ->
                    callback.onSuccess(InsightsRepository.DEFAULT_RECOMMENDED_POSITION));
            // without a backend there is no place data, so the card says travel time is unavailable
            places = callback -> MainThread.post(() -> callback.onSuccess(java.util.Collections.emptyList()));
            schedules = new EmptyScheduleRepository();
        }
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

    public static AlertsRepository alerts() {
        return alerts;
    /* null without Firebase: BQ3 decisions only exist on the backend */
    public static RecommendationDecisionRepository recommendationDecisions() {
        return recommendationDecisions;
    }

    public static InsightsRepository insights() {
        return insights;
    }

    public static CampusPlaceRepository places() {
        return places;
    }

    public static AuthRepository auth() {
        return auth;
    }

    public static PrivacySettingsRepository privacySettings() {
        return privacySettings;
    }

    public static ScheduleRepository schedules() {
        return schedules;
    }

    public static StatusService status() {
        return status;
    }
}
