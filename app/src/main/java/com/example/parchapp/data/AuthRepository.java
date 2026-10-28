package com.example.parchapp.data;

import androidx.annotation.Nullable;

import com.example.parchapp.UserProfile;
import com.example.parchapp.util.Callback;

/* Authentication in this case. Passwords well are handed to the provider and never stored by the app. */
public interface AuthRepository {
    /* The session restored from a previous launch, or null. */
    @Nullable
    UserProfile getSignedInUser();

    void signIn(String email, String password, Callback<UserProfile> callback);

    void signInWithGoogle(String idToken, Callback<UserProfile> callback);

    /* fullName may be empty; the profile then takes its name from the email. */
    void register(String fullName, String email, String password, Callback<UserProfile> callback);

    void sendPasswordReset(String email, Callback<Void> callback);

    void signOut();
}
