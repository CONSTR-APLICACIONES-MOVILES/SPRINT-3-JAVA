package com.example.parchapp;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.example.parchapp.analytics.AnalyticsTracker;
import com.example.parchapp.data.PrivacySettingsRepository;
import com.example.parchapp.data.Repositories;
import com.example.parchapp.util.Callback;

public class PrivacySettingsActivity extends AppCompatActivity {
    private PrivacySettingsRepository settings;
    private SwitchCompat analyticsSwitch;
    private SwitchCompat availabilitySwitch;
    private TextView analyticsState;
    private TextView availabilityState;
    private View retryButton;
    private boolean bindingAvailability;
    private boolean availabilityHidden;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_settings);

        settings = Repositories.privacySettings();
        analyticsSwitch = findViewById(R.id.analytics_consent_switch);
        availabilitySwitch = findViewById(R.id.availability_hidden_switch);
        analyticsState = findViewById(R.id.analytics_consent_state);
        availabilityState = findViewById(R.id.availability_visibility_state);
        retryButton = findViewById(R.id.retry_privacy_settings_button);

        findViewById(R.id.privacy_back_button).setOnClickListener(v -> finish());
        retryButton.setOnClickListener(v -> loadAvailabilityVisibility());

        analyticsSwitch.setChecked(settings.isAnalyticsConsentAccepted());
        renderAnalyticsState(settings.isAnalyticsConsentAccepted());
        analyticsSwitch.setOnCheckedChangeListener((button, accepted) -> saveAnalyticsConsent(accepted));

        availabilitySwitch.setEnabled(false);
        availabilityState.setText(R.string.privacy_loading);
        loadAvailabilityVisibility();
    }

    private void saveAnalyticsConsent(boolean accepted) {
        analyticsSwitch.setEnabled(false);
        analyticsState.setText(R.string.privacy_saving);
        settings.saveAnalyticsConsent(accepted, new Callback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                if (isFinishing() || isDestroyed()) return;
                AnalyticsTracker.getInstance().setAnalyticsConsent(accepted);
                analyticsSwitch.setEnabled(true);
                renderAnalyticsState(accepted);
            }

            @Override
            public void onError(Exception error) {
                if (isFinishing() || isDestroyed()) return;
                analyticsSwitch.setEnabled(true);
                analyticsState.setText(R.string.privacy_analytics_save_error);
            }
        });
    }

    private void loadAvailabilityVisibility() {
        availabilitySwitch.setEnabled(false);
        availabilityState.setText(R.string.privacy_loading);
        availabilityState.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.GONE);
        settings.getAvailabilityHiddenFromGroups(new Callback<Boolean>() {
            @Override
            public void onSuccess(Boolean hidden) {
                if (isFinishing() || isDestroyed()) return;
                availabilityHidden = Boolean.TRUE.equals(hidden);
                bindingAvailability = true;
                availabilitySwitch.setChecked(availabilityHidden);
                bindingAvailability = false;
                availabilitySwitch.setEnabled(true);
                availabilityState.setText(availabilityHidden
                        ? R.string.privacy_availability_hidden
                        : R.string.privacy_availability_shared);
                availabilitySwitch.setOnCheckedChangeListener((button, checked) -> {
                    if (!bindingAvailability) saveAvailabilityVisibility(checked);
                });
            }

            @Override
            public void onError(Exception error) {
                if (isFinishing() || isDestroyed()) return;
                availabilityState.setText(R.string.privacy_availability_load_error);
                retryButton.setVisibility(View.VISIBLE);
            }
        });
    }

    private void saveAvailabilityVisibility(boolean hidden) {
        boolean previous = availabilityHidden;
        availabilitySwitch.setEnabled(false);
        availabilityState.setText(R.string.privacy_saving);
        availabilityState.setVisibility(View.VISIBLE);
        settings.setAvailabilityHiddenFromGroups(hidden, new Callback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                if (isFinishing() || isDestroyed()) return;
                availabilityHidden = hidden;
                availabilitySwitch.setEnabled(true);
                availabilityState.setText(hidden
                        ? R.string.privacy_availability_hidden
                        : R.string.privacy_availability_shared);
            }

            @Override
            public void onError(Exception error) {
                if (isFinishing() || isDestroyed()) return;
                bindingAvailability = true;
                availabilitySwitch.setChecked(previous);
                bindingAvailability = false;
                availabilitySwitch.setEnabled(true);
                availabilityState.setText(R.string.privacy_availability_save_error);
            }
        });
    }

    private void renderAnalyticsState(boolean accepted) {
        analyticsState.setText(accepted
                ? R.string.privacy_analytics_accepted
                : R.string.privacy_analytics_rejected);
    }
}
