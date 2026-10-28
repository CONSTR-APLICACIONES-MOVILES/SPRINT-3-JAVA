package com.example.parchapp.data;

import android.content.Context;

import com.example.parchapp.domain.StatusService;
/* Sprint 3 base composition: keeps the view contracts while Sprint 2 features are reintroduced on feature branches. */
public final class Repositories {
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
        groups = new EmptyGroupRepository();
        activities = new DisabledActivityRepository();
        auth = new DisabledAuthRepository();
        schedules = new EmptyScheduleRepository();
        status = new StatusService(context);
    }

    public static boolean isFirebaseAvailable() {
        return false;
    }

    public static boolean isFirebaseAnalyticsAvailable() {
        return false;
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
