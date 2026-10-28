package com.example.parchapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.parchapp.analytics.AnalyticsTracker;
import com.example.parchapp.data.AuthRepository;
import com.example.parchapp.data.Repositories;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.GoogleSignInFlow;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

public class CreateAccountActivity extends AppCompatActivity {
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final AuthRepository auth = Repositories.auth();
    private GoogleSignInFlow googleSignIn;
    private EditText nameInput;
    private EditText emailInput;
    private EditText passwordInput;
    private EditText confirmInput;
    private Button createButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account);

        nameInput = findViewById(R.id.create_name_input);
        emailInput = findViewById(R.id.create_email_input);
        passwordInput = findViewById(R.id.create_password_input);
        confirmInput = findViewById(R.id.create_confirm_password_input);
        createButton = findViewById(R.id.create_account_button);
        googleSignIn = new GoogleSignInFlow(this, new OpenDashboardCallback());

        findViewById(R.id.create_back_button).setOnClickListener(v -> finish());
        // Reached from Welcome or from Sign In; either way this link must land on Sign In.
        findViewById(R.id.back_to_sign_in).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });
        findViewById(R.id.create_google_button).setOnClickListener(v -> googleSignIn.start());
        bindRememberedGoogleAccount();

        setupVisibilityToggle(findViewById(R.id.toggle_password), passwordInput);
        setupVisibilityToggle(findViewById(R.id.toggle_confirm_password), confirmInput);
        createButton.setOnClickListener(v -> createAccount());
    }

    private void bindRememberedGoogleAccount() {
        GoogleSignInAccount account = googleSignIn.lastAccount();
        if (account == null || account.getEmail() == null) {
            return;
        }
        String name = account.getDisplayName() != null ? account.getDisplayName() : account.getEmail();
        UserProfile preview = UserProfile.fromAccount(account.getEmail(), account.getEmail(), name);
        ((TextView) findViewById(R.id.google_account_initials)).setText(preview.getInitials());
        ((TextView) findViewById(R.id.google_account_name)).setText(preview.getFullName());
        ((TextView) findViewById(R.id.google_account_email)).setText(account.getEmail());
        findViewById(R.id.google_account_card).setVisibility(View.VISIBLE);
        findViewById(R.id.google_account_row).setOnClickListener(v -> googleSignIn.start());
        findViewById(R.id.use_another_google_account).setOnClickListener(v -> googleSignIn.startWithAnotherAccount());
    }

    private void setupVisibilityToggle(ImageButton toggle, EditText field) {
        toggle.setOnClickListener(v -> {
            boolean hidden = field.getTransformationMethod() instanceof PasswordTransformationMethod;
            field.setTransformationMethod(hidden
                    ? HideReturnsTransformationMethod.getInstance()
                    : PasswordTransformationMethod.getInstance());
            toggle.setImageResource(hidden ? R.drawable.ic_visibility_off : R.drawable.ic_visibility);
            field.setSelection(field.getText().length());
        });
    }

    private void createAccount() {
        String fullName = nameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        String confirmation = confirmInput.getText().toString();
        if (fullName.isEmpty()) {
            nameInput.setError("Enter your name");
            nameInput.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Enter a valid email");
            emailInput.requestFocus();
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            passwordInput.setError("At least " + MIN_PASSWORD_LENGTH + " characters");
            passwordInput.requestFocus();
            return;
        }
        if (!password.equals(confirmation)) {
            confirmInput.setError("Passwords do not match");
            confirmInput.requestFocus();
            return;
        }

        createButton.setEnabled(false);
        auth.register(fullName, email, password, new OpenDashboardCallback() {
            @Override
            public void onError(Exception error) {
                createButton.setEnabled(true);
                Toast.makeText(CreateAccountActivity.this,
                        "Could not create the account: " + error.getLocalizedMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private class OpenDashboardCallback implements Callback<UserProfile> {
        @Override
        public void onSuccess(UserProfile user) {
            UserSession.setCurrentUser(user);
            AnalyticsTracker.getInstance().setUserId(user.getId());
            startActivity(new Intent(CreateAccountActivity.this, DashboardActivity.class));
            finishAffinity();
        }

        @Override
        public void onError(Exception error) {
            Toast.makeText(CreateAccountActivity.this, "Google sign-in failed", Toast.LENGTH_SHORT).show();
        }
    }
}
