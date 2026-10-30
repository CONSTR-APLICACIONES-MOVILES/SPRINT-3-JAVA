package com.example.parchapp;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.parchapp.analytics.AnalyticsTracker;
import com.example.parchapp.data.AuthRepository;
import com.example.parchapp.data.Repositories;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.GoogleSignInFlow;
import com.google.firebase.FirebaseNetworkException;

/* F5: the Sign In view. Email and password, or Google, both go through AuthRepository (Firebase Auth). */
public class MainActivity extends AppCompatActivity {

    private static final int MIN_PASSWORD_LENGTH = 6;

    private final AuthRepository auth = Repositories.auth();
    private EditText emailInput;
    private EditText passwordInput;
    private Button signInButton;
    private GoogleSignInFlow googleSignIn;
    private final OfflineBannerController offlineBanner = new OfflineBannerController();

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
        // the shared banner talks about saved groups; here it says what works and what does not without network
        ((TextView) findViewById(R.id.offline_banner_message)).setText(R.string.offline_banner_sign_in);

// f5: firebase keeps the session, so getCurrentUser gives us the user that signed in before
        UserProfile restored = auth.getSignedInUser();
        if (restored != null) {
            showRestoredSession(restored, continueButton);
        } else {
// f5: without a saved session the card stays hidden and the user uses the form
            findViewById(R.id.restored_session_card).setVisibility(View.GONE);
            continueButton.setEnabled(false);
        }

        findViewById(R.id.google_button).setOnClickListener(v -> {
            // eventual connectivity: google opens its own screen, which fails offline, so we check before leaving the app
            if (isOffline("sign in with Google")) {
                return;
            }
            googleSignIn.start();
        });
        signInButton.setOnClickListener(v -> signIn());
        // Next goes to the password and Done signs in, so the user does not have to close the keyboard to reach the button
        emailInput.setOnEditorActionListener((field, action, event) -> {
            if (action == EditorInfo.IME_ACTION_NEXT) {
                passwordInput.requestFocus();
                return true;
            }
            return false;
        });
        passwordInput.setOnEditorActionListener((field, action, event) -> {
            if (action == EditorInfo.IME_ACTION_DONE) {
                signIn();
                return true;
            }
            return false;
        });
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

        if (isOffline("sign in with your email")) {
            return;
        }
        setSigningIn(true);
        auth.signIn(email, password, new Callback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile user) {
                setSigningIn(false);
                passwordInput.setText("");
                openDashboard(user);
            }

            @Override
            public void onError(Exception error) {
                setSigningIn(false);
                // a lost connection is not a wrong password, so the message says what really happened
                if (error instanceof FirebaseNetworkException) {
                    showMessage("No connection. Check your internet and try again.");
                    return;
                }
                // we dont say which one failed so nobody can check which emails have an account
                showMessage("Email or password is incorrect");
            }
        });
    }

    // loading state: the button says what is happening and cannot be pressed twice
    private void setSigningIn(boolean signingIn) {
        signInButton.setEnabled(!signingIn);
        signInButton.setText(signingIn ? R.string.signing_in : R.string.sign_in);
    }

    private void sendPasswordReset() {
        String email = emailInput.getText().toString().trim();
        if (!isValidEmail(email) || isOffline("reset your password")) {
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

    @Override
    protected void onStart() {
        super.onStart();
        offlineBanner.start(findViewById(R.id.offline_banner));
    }

    @Override
    protected void onStop() {
        offlineBanner.stop();
        super.onStop();
    }

    /* eventual connectivity (#43): signing in needs Firebase, so offline we do not try and we say why.
     * The saved session does not need the network, so when there is one we point to Continue as. */
    private boolean isOffline(String action) {
        if (AnalyticsTracker.getInstance().isOnline()) {
            return false;
        }
        UserProfile restored = auth.getSignedInUser();
        showMessage(restored != null
                ? "You're offline. Connect to " + action + ", or use Continue as " + restored.getFirstName() + "."
                : "You're offline. Connect to the internet to " + action + ".");
        return true;
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

// f5: fills the continue as card with the data of the saved user and opens the dashboard with one tap
    private void showRestoredSession(UserProfile user, Button continueButton) {
        findViewById(R.id.restored_session_card).setVisibility(View.VISIBLE);
        ((TextView) findViewById(R.id.restored_user_initials)).setText(user.getInitials());
        ((TextView) findViewById(R.id.restored_user_name)).setText(user.getFirstName());
        ((TextView) findViewById(R.id.restored_user_email)).setText(user.getEmail());
// f5: we do not have the career of the user, so we hide that line
        findViewById(R.id.restored_user_detail).setVisibility(View.GONE);
        continueButton.setText("Continue as " + user.getFirstName());
        continueButton.setEnabled(true);
        continueButton.setOnClickListener(v -> openDashboard(user));
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
