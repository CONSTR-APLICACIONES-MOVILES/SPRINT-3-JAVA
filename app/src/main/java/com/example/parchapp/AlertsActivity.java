package com.example.parchapp;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class AlertsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alerts);
        findViewById(R.id.alerts_back_button).setOnClickListener(v -> finish());
        findViewById(R.id.alerts_unread_count).setVisibility(View.GONE);
        findViewById(R.id.alerts_search_input).setEnabled(false);
        findViewById(R.id.alerts_filter_all).setEnabled(false);
        findViewById(R.id.alerts_filter_invitations).setEnabled(false);
        findViewById(R.id.alerts_filter_schedule).setEnabled(false);
        findViewById(R.id.alerts_filter_groups).setEnabled(false);
        findViewById(R.id.alerts_filter_unread).setEnabled(false);
        TextView state = findViewById(R.id.alerts_state_text);
        state.setText("Alerts view — feature not connected in this branch.");
        state.setVisibility(View.VISIBLE);
    }
}
