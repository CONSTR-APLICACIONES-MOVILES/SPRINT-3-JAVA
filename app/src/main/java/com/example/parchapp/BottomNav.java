package com.example.parchapp;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;


// the bottom bar of view_bottom_nav.xml, every main screen calls setup with its own tab
// so the bar looks and works the same everywhere
public final class BottomNav {

    public enum Tab { HOME, GROUPS, SCHEDULE }

    private BottomNav() {
    }

// marks the active tab with the green pill and opens the screen of each tab
    public static void setup(Activity activity, Tab selected) {
        mark(activity, R.id.nav_home_icon, R.id.nav_home_label, selected == Tab.HOME);
        mark(activity, R.id.nav_groups_icon, R.id.nav_groups_label, selected == Tab.GROUPS);
        mark(activity, R.id.nav_schedule_icon, R.id.nav_schedule_label, selected == Tab.SCHEDULE);

        activity.findViewById(R.id.nav_home).setOnClickListener(v ->
                open(activity, DashboardActivity.class));
        activity.findViewById(R.id.nav_groups).setOnClickListener(v ->
                open(activity, GroupsActivity.class));
        activity.findViewById(R.id.nav_schedule).setOnClickListener(v ->
                open(activity, ScheduleActivity.class));
    }

    private static void mark(Activity activity, int iconId, int labelId, boolean active) {
        ImageView icon = activity.findViewById(iconId);
        TextView label = activity.findViewById(labelId);
        icon.setBackgroundResource(active ? R.drawable.bg_nav_selected : 0);
        icon.setImageTintList(ColorStateList.valueOf(activity.getColor(
                active ? R.color.brand_success_dark : R.color.brand_slate)));
        label.setTextColor(activity.getColor(active ? R.color.brand_primary : R.color.brand_slate));
        label.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
// the tab says if it is selected, so screen readers can tell it
        ((View) label.getParent()).setSelected(active);
    }

// if we are already on that screen we do nothing, from group details the groups tab goes back to the list
    // the opened screen logs its own bq8 feature_used, so it counts the same from any entry point
    private static void open(Activity activity, Class<? extends Activity> screen) {
        if (activity.getClass() == screen) {
            return;
        }
        activity.startActivity(new Intent(activity, screen)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }
}
