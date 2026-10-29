package com.example.parchapp;

import android.app.Activity;
import android.content.Intent;
import android.widget.TextView;
import android.widget.Toast;


// the top bar of view_top_header.xml, every main screen calls setup so the bar looks and works the same everywhere
public final class TopHeader {

    private TopHeader() {
    }

// puts the initials of the signed in user and opens search, alerts and profile
    public static void setup(Activity activity) {
        UserProfile user = UserSession.getCurrentUser();
        ((TextView) activity.findViewById(R.id.header_user_initials)).setText(user == null ? "" : user.getInitials());
        activity.findViewById(R.id.header_search_btn).setOnClickListener(v ->
                Toast.makeText(activity, R.string.search_coming_soon, Toast.LENGTH_SHORT).show());
        // AlertsActivity logs bq8 feature_used when it opens, so the bell does not log it again
        activity.findViewById(R.id.header_notif_btn).setOnClickListener(v ->
                activity.startActivity(new Intent(activity, AlertsActivity.class)));
        activity.findViewById(R.id.header_user_initials).setOnClickListener(v ->
                activity.startActivity(new Intent(activity, ProfileActivity.class)));
    }
}
