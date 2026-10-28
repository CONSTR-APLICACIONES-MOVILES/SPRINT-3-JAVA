package com.example.parchapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** Create-group view shell. Group creation and invitation behavior are restored on their feature branch. */
public class CreateGroupActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);

        findViewById(R.id.back_group_button).setOnClickListener(v -> finish());
        findViewById(R.id.cancel_create_group).setOnClickListener(v -> finish());
        findViewById(R.id.add_email_button).setEnabled(false);
        findViewById(R.id.create_group_button).setEnabled(false);
        findViewById(R.id.copy_group_link_button).setEnabled(false);
        findViewById(R.id.share_group_link_button).setEnabled(false);
        findViewById(R.id.show_qr_button).setEnabled(false);
        findViewById(R.id.header_notif_btn).setOnClickListener(v ->
                startActivity(new Intent(this, AlertsActivity.class)));
        findViewById(R.id.header_profile_btn).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));
        findViewById(R.id.nav_home_create).setOnClickListener(v ->
                startActivity(new Intent(this, DashboardActivity.class)));
        findViewById(R.id.nav_schedule_create).setOnClickListener(v ->
                startActivity(new Intent(this, ScheduleActivity.class)));

        View pending = findViewById(R.id.email_chips);
        if (pending != null) pending.setVisibility(View.GONE);
        Toast.makeText(this, "Group creation is not included in this base branch", Toast.LENGTH_SHORT).show();
    }
}
