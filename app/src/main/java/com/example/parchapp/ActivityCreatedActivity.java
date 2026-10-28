package com.example.parchapp;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import android.view.View;

public class ActivityCreatedActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE = "extra_activity_title";
    public static final String EXTRA_DATE = "extra_activity_date";
    public static final String EXTRA_TIME = "extra_activity_time";
    public static final String EXTRA_LOCATION = "extra_activity_location";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_created);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String date = getIntent().getStringExtra(EXTRA_DATE);
        String time = getIntent().getStringExtra(EXTRA_TIME);
        String location = getIntent().getStringExtra(EXTRA_LOCATION);
        if (title == null || title.trim().isEmpty()) {
            findViewById(R.id.activity_summary_card).setVisibility(View.GONE);
        } else {
            ((android.widget.TextView) findViewById(R.id.created_activity_title)).setText(title);
            ((android.widget.TextView) findViewById(R.id.created_activity_time)).setText(
                    joinNonEmpty(date, time));
            ((android.widget.TextView) findViewById(R.id.created_activity_duration)).setText(
                    "Duration not specified");
            ((android.widget.TextView) findViewById(R.id.created_activity_location)).setText(
                    location == null || location.trim().isEmpty() ? "Location not specified" : location);
            ((android.widget.TextView) findViewById(R.id.created_activity_location_detail)).setText("");
        }
        // Participant records are not part of the Java activity read contract yet.
        findViewById(R.id.attendees_card).setVisibility(View.GONE);

        findViewById(R.id.close_created_button).setOnClickListener(v -> finish());
        findViewById(R.id.copy_invite_button).setEnabled(false);
        findViewById(R.id.whatsapp_button).setEnabled(false);
        findViewById(R.id.calendar_toggle).setEnabled(false);
        findViewById(R.id.reminder_button).setEnabled(false);
        findViewById(R.id.view_schedule_button).setEnabled(false);
        findViewById(R.id.back_home_button).setOnClickListener(v -> finishAffinity());
    }

    private String joinNonEmpty(String first, String second) {
        String value = ((first == null ? "" : first) + " " + (second == null ? "" : second)).trim();
        return value.isEmpty() ? "Schedule not specified" : value;
    }
}
