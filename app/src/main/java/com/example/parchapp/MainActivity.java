package com.example.parchapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.parchapp.analytics.AnalyticsTracker;
import com.example.parchapp.data.AuthRepository;
import com.example.parchapp.data.Repositories;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.GoogleSignInFlow;

/* F5: the Sign In view. Email and password, or Google, both go through AuthRepository (Firebase Auth). */
public class MainActivity extends AppCompatActivity {

    private static final int MIN_PASSWORD_LENGTH = 6;

    private final AuthRepository auth = Repositories.auth();
    private EditText emailInput;
    private EditText passwordInput;
    private Button signInButton;
    private GoogleSignInFlow googleSignIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button continueButton = findViewById(R.id.continue_button);
        signInButton = findViewById(R.id.sign_in_button);
        emailInput = findViewById(R.id.email_input);
        passwordInput = findViewById(R.id.password_input);
        // the google flow registers an activity result launcher, so it has to be created here in onCreate
        googleSignIn = new GoogleSignInFlow(this, new OpenDashboardCallback());

        // the "Continue as" card is its own feature, here it stays hidden
        findViewById(R.id.restored_session_card).setVisibility(View.GONE);
        continueButton.setEnabled(false);

        findViewById(R.id.google_button).setOnClickListener(v -> googleSignIn.start());
        signInButton.setOnClickListener(v -> signIn());
        findViewById(R.id.forgot_password).setOnClickListener(v -> sendPasswordReset());
        findViewById(R.id.create_account).setOnClickListener(v ->
                startActivity(new Intent(this, CreateAccountActivity.class)));
    }

    private void signIn() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (!isValidEmail(email)) {
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            passwordInput.setError("At least " + MIN_PASSWORD_LENGTH + " characters");
            return;
        }

        signInButton.setEnabled(false);
        auth.signIn(email, password, new Callback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile user) {
                signInButton.setEnabled(true);
                passwordInput.setText("");
                openDashboard(user);
            }

            @Override
            public void onError(Exception error) {
                signInButton.setEnabled(true);
                // we dont say which one failed so nobody can check which emails have an account
                showMessage("Email or password is incorrect");
            }
        });
    }

    private void sendPasswordReset() {
        String email = emailInput.getText().toString().trim();
        if (!isValidEmail(email)) {
            return;
        }
        auth.sendPasswordReset(email, new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                showMessage("Password reset email sent to " + email);
            }

            @Override
            public void onError(Exception error) {
                showMessage("Could not send the reset email");
            }
        });
    }

    private final class OpenDashboardCallback implements Callback<UserProfile> {
        @Override
        public void onSuccess(UserProfile user) {
            openDashboard(user);
        }

        @Override
        public void onError(Exception error) {
            showMessage("Google sign-in failed");
        }
    }

    private boolean isValidEmail(String email) {
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("Enter your university email");
            emailInput.requestFocus();
            return false;
        }
        return true;
    }

    private void openDashboard(UserProfile user) {
        UserSession.setCurrentUser(user);
        AnalyticsTracker.getInstance().setUserId(user.getId());
        startActivity(new Intent(this, DashboardActivity.class));
    }

    private void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
