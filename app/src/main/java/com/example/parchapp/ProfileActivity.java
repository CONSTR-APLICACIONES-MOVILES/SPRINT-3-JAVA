package com.example.parchapp;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.parchapp.data.Repositories;

import android.widget.TextView;

public class ProfileActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        findViewById(R.id.profile_back_button).setOnClickListener(v -> finish());
        findViewById(R.id.profile_edit_button).setEnabled(false);
        findViewById(R.id.profile_status_button).setEnabled(false);
        findViewById(R.id.profile_calendar_card).setOnClickListener(v ->
                startActivity(new Intent(this, PrivacySettingsActivity.class)));
        findViewById(R.id.profile_logout_button).setOnClickListener(v -> logout());
        renderViewOnlyProfile();
    }

    private void renderViewOnlyProfile() {
        UserProfile user = UserSession.getCurrentUser();
        String name = user == null ? "" : user.getFullName();
        String initials = user == null ? "" : user.getInitials();
        ((TextView) findViewById(R.id.profile_initials)).setText(initials);
        ((TextView) findViewById(R.id.profile_name)).setText(name);
        ((TextView) findViewById(R.id.profile_program)).setText("");
        ((TextView) findViewById(R.id.profile_status_button)).setText("Profile view only");
        ((TextView) findViewById(R.id.profile_plans_count)).setText("—\nPlans completed");
        ((TextView) findViewById(R.id.profile_groups_count)).setText("—\nActive groups");
        ((TextView) findViewById(R.id.profile_rate)).setText("—\nOn-time rate");
        ((TextView) findViewById(R.id.profile_academic_card)).setText("Academic information");
        ((TextView) findViewById(R.id.profile_groups_card)).setText("Groups");
        ((TextView) findViewById(R.id.profile_calendar_card)).setText("Privacy and settings  ›");
    }

    private void logout() {
        Repositories.auth().signOut();
        UserSession.clear();
        Intent signIn = new Intent(this, MainActivity.class);
        signIn.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(signIn);
        finishAffinity();
    }

}
