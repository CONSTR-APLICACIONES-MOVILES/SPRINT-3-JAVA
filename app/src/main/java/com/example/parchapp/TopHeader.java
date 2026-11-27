package com.example.parchapp;

import android.app.Activity;
import android.content.Intent;
import android.widget.TextView;


// the top bar of view_top_header.xml, every main screen calls setup so the bar looks and works the same everywhere
public final class TopHeader {

    private TopHeader() {
    }

// puts the initials of the signed in user and opens alerts and profile
    public static void setup(Activity activity) {
        UserProfile user = UserSession.getCurrentUser();
        ((TextView) activity.findViewById(R.id.header_user_initials)).setText(user == null ? "" : user.getInitials());
        // AlertsActivity logs bq8 feature_used when it opens, so the bell does not log it again
        activity.findViewById(R.id.header_notif_btn).setOnClickListener(v ->
                activity.startActivity(new Intent(activity, AlertsActivity.class)));
        activity.findViewById(R.id.header_user_initials).setOnClickListener(v ->
                activity.startActivity(new Intent(activity, ProfileActivity.class)));
        // the ? button opens the help with the questions of this screen first
        activity.findViewById(R.id.header_help_btn).setOnClickListener(v ->
                HelpActivity.open(activity, screenName(activity)));
    }

    // the name the help uses for each main screen, see "screen" in help_faq.json
    private static String screenName(Activity activity) {
        String name = activity.getClass().getSimpleName();
        switch (name) {
            case "GroupsActivity":
            case "CreateGroupActivity":
                return "groups";
            case "ScheduleActivity":
                return "schedule";
            case "DashboardActivity":
                return "dashboard";
            default:
                return "general";
        }
    }
}
