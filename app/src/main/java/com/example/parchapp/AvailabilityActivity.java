package com.example.parchapp;

import android.os.Bundle;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class AvailabilityActivity extends AppCompatActivity {
    private String selectedStatus = "Commute / In Transit";
    private String selectedDuration = "For 1 hour";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_availability);

        RadioButton[] statuses = {
                findViewById(R.id.status_available),
                findViewById(R.id.status_class),
                findViewById(R.id.status_focus),
                findViewById(R.id.status_busy),
                findViewById(R.id.status_transit)
        };

        for (RadioButton status : statuses) {
            status.setOnClickListener(v -> selectedStatus = ((RadioButton) v).getText().toString());
        }

        Button[] durations = {
                findViewById(R.id.duration_one_hour),
                findViewById(R.id.duration_until_six),
                findViewById(R.id.duration_tomorrow),
                findViewById(R.id.duration_manual)
        };
        for (Button duration : durations) {
            duration.setOnClickListener(v -> selectedDuration = ((Button) v).getText().toString());
        }

        findViewById(R.id.close_availability_button).setOnClickListener(v -> finish());
        findViewById(R.id.save_status_button).setOnClickListener(v ->
                Toast.makeText(this, selectedStatus + " · " + selectedDuration, Toast.LENGTH_SHORT).show());
    }
}
