package com.example.parchapp.util;

import android.content.Intent;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.parchapp.UserProfile;
import com.example.parchapp.data.AuthRepository;
import com.example.parchapp.data.Repositories;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;

/* F5: Google sign in shared by Sign In and Create Account. Create it in onCreate, before the activity starts, because it registers an activity result launcher. */
public final class GoogleSignInFlow {
    private final AppCompatActivity activity;
    private final AuthRepository auth = Repositories.auth();
    private final Callback<UserProfile> onSignedIn;
    private final ActivityResultLauncher<Intent> launcher;

    public GoogleSignInFlow(AppCompatActivity activity, Callback<UserProfile> onSignedIn) {
        this.activity = activity;
        this.onSignedIn = onSignedIn;
        launcher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> handleResult(result.getData()));
    }

    /* The Google account this phone last used with ParchApp, or null. Never a made-up account. */
    @Nullable
    public GoogleSignInAccount lastAccount() {
        return Repositories.isFirebaseAvailable() ? GoogleSignIn.getLastSignedInAccount(activity) : null;
    }

    public void start() {
        GoogleSignInClient client = client();
        if (client != null) {
            launcher.launch(client.getSignInIntent());
        }
    }

    /* Forgets the remembered account first, so Google shows its account chooser. */
    public void startWithAnotherAccount() {
        GoogleSignInClient client = client();
        if (client != null) {
            client.signOut().addOnCompleteListener(task -> launcher.launch(client.getSignInIntent()));
        }
    }

    @Nullable
    private GoogleSignInClient client() {
        if (!Repositories.isFirebaseAvailable()) {
            toast("Google sign-in needs the Firebase backend");
            return null;
        }
        int clientIdResource = activity.getResources().getIdentifier(
                "default_web_client_id", "string", activity.getPackageName());
        String clientId = clientIdResource == 0 ? "" : activity.getString(clientIdResource).trim();
        if (clientId.isEmpty()) {
            toast("Configure the Firebase OAuth client before using Google sign-in");
            return null;
        }
        GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(clientId)
                .requestEmail()
                .build();
        return GoogleSignIn.getClient(activity, options);
    }

    private void handleResult(@Nullable Intent data) {
        if (data == null) {
            toast("Google sign-in was cancelled");
            return;
        }
        try {
            GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException.class);
            if (account == null || account.getIdToken() == null) {
                toast("Google did not return an identity token");
                return;
            }
            auth.signInWithGoogle(account.getIdToken(), onSignedIn);
        } catch (ApiException error) {
            toast("Google sign-in failed: " + error.getStatusCode());
        }
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
    }
}
