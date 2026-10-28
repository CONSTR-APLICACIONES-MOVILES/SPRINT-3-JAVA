package com.example.parchapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/** Group screen shell. Group membership and Firestore behavior are restored on its feature branch. */
public class GroupDetailActivity extends AppCompatActivity {
    public static final String EXTRA_GROUP_ID = "extra_group_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_detail);
        findViewById(R.id.back_button).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.group_category_text)).setText("GROUP");
        ((TextView) findViewById(R.id.top_group_name_text)).setText("Group details");
        ((TextView) findViewById(R.id.top_group_subtitle_text)).setText("");
        ((TextView) findViewById(R.id.group_name_text)).setText("Group feature not included in this base branch");
        ((TextView) findViewById(R.id.group_description_text)).setText("");
        ((TextView) findViewById(R.id.group_synced_badge)).setText("Not connected");
        ((TextView) findViewById(R.id.common_free_text)).setText("Availability feature not included in this base branch");
        findViewById(R.id.common_free_badge).setVisibility(View.GONE);
        findViewById(R.id.group_plan_section).setVisibility(View.GONE);
        findViewById(R.id.group_members_header).setVisibility(View.GONE);
        findViewById(R.id.member_card_0).setVisibility(View.GONE);
        findViewById(R.id.member_card_1).setVisibility(View.GONE);
        findViewById(R.id.member_card_2).setVisibility(View.GONE);
        findViewById(R.id.member_card_3).setVisibility(View.GONE);
        findViewById(R.id.group_recent_activity).setVisibility(View.GONE);

        findViewById(R.id.share_button).setEnabled(false);
        findViewById(R.id.more_options_button).setEnabled(false);
        findViewById(R.id.compare_roommates_button).setEnabled(false);
        findViewById(R.id.plan_details_button).setEnabled(false);
        findViewById(R.id.add_calendar_button).setEnabled(false);
        findViewById(R.id.refresh_roommates_button).setEnabled(false);
        findViewById(R.id.propose_plan_button).setEnabled(false);
        findViewById(R.id.sync_schedules_button).setEnabled(false);
        findViewById(R.id.house_chat_button).setEnabled(false);
        findViewById(R.id.view_all_activities).setEnabled(false);
        findViewById(R.id.create_plan_button).setEnabled(false);

        findViewById(R.id.nav_home_group).setOnClickListener(v ->
                startActivity(new Intent(this, DashboardActivity.class)));
        findViewById(R.id.nav_schedule_group).setOnClickListener(v ->
                startActivity(new Intent(this, ScheduleActivity.class)));
    }
}
