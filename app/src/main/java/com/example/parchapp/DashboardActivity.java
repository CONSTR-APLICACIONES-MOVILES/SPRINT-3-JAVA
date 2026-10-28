package com.example.parchapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.parchapp.data.Repositories;
import com.example.parchapp.domain.StatusService;

public class DashboardActivity extends AppCompatActivity implements StatusService.Listener {

    private TextView displayFreeUntil;
    private TextView displayNextActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        bindUser(UserSession.getCurrentUser());

        displayFreeUntil = findViewById(R.id.display_free_until);
        displayNextActivity = findViewById(R.id.display_next_activity);

        findViewById(R.id.header_search_btn).setOnClickListener(v -> toast("Search coming soon"));
        findViewById(R.id.header_notif_btn).setOnClickListener(v -> {
            startActivity(new Intent(this, AlertsActivity.class));
        });
        findViewById(R.id.header_user_initials).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        findViewById(R.id.copy_link_button).setEnabled(false);
        findViewById(R.id.btn_edit_free_until).setOnClickListener(v -> openQuickStatusSheet());
        findViewById(R.id.btn_edit_next_activity).setOnClickListener(v -> openQuickStatusSheet());
        findViewById(R.id.compare_button).setEnabled(false);
        findViewById(R.id.calendar_button).setOnClickListener(v -> {
            startActivity(new Intent(this, ScheduleActivity.class));
        });
        findViewById(R.id.new_activity_button).setEnabled(false);

        findViewById(R.id.plan_card_study).setOnClickListener(v -> openPlanDetails());
        findViewById(R.id.plan_details_button).setOnClickListener(v -> openPlanDetails());

        findViewById(R.id.going_button).setEnabled(false);
        findViewById(R.id.maybe_button).setEnabled(false);
        findViewById(R.id.group_card_eng).setEnabled(false);
        findViewById(R.id.roomies_group_card).setEnabled(false);
        findViewById(R.id.view_all_groups_button).setEnabled(false);
        findViewById(R.id.nav_groups).setEnabled(false);
        findViewById(R.id.nav_schedule).setOnClickListener(v -> {
            startActivity(new Intent(this, ScheduleActivity.class));
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        hideFeatureDataViews();
        StatusService status = Repositories.status();
        status.addListener(this);
        onStatusChanged(status);
    }

    @Override
    protected void onStop() {
        Repositories.status().removeListener(this);
        super.onStop();
    }

    @Override
    public void onStatusChanged(StatusService status) {
        displayFreeUntil.setText(status.getFreeUntil());
        displayNextActivity.setText(status.getNextActivity());
    }

    private void openQuickStatusSheet() {
        QuickStatusSheet.newInstance((freeUntil, nextActivity) -> {
            Repositories.status().updateQuickStatus(freeUntil, nextActivity);
            toast("Status updated");
        }).show(getSupportFragmentManager(), "quick_status");
    }

    private void openPlanDetails() { }

    private void hideFeatureDataViews() {
        findViewById(R.id.plan_card_study).setVisibility(View.GONE);
        findViewById(R.id.plan_card_rsvp).setVisibility(View.GONE);
        findViewById(R.id.group_card_eng).setVisibility(View.GONE);
        findViewById(R.id.roomies_group_card).setVisibility(View.GONE);
        findViewById(R.id.view_all_groups_button).setEnabled(false);
        findViewById(R.id.view_all_groups_button).setVisibility(View.GONE);
        findViewById(R.id.group_card_eng).setOnClickListener(null);
        findViewById(R.id.roomies_group_card).setOnClickListener(null);
        findViewById(R.id.plan_details_button).setEnabled(false);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void bindUser(UserProfile user) {
        String initials = user == null ? "" : user.getInitials();
        String name = user == null ? "" : user.getFirstName();
        ((TextView) findViewById(R.id.header_user_initials)).setText(initials);
        ((TextView) findViewById(R.id.greeting_text)).setText(name.isEmpty() ? "Home" : "Hello, " + name + "!");
        ((TextView) findViewById(R.id.active_plans_text)).setText("");
        ((TextView) findViewById(R.id.invite_link_text)).setText("");
    }
}
