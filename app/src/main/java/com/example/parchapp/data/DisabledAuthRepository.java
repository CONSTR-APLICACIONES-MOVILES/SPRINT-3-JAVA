package com.example.parchapp.data;

import androidx.annotation.Nullable;

import com.example.parchapp.UserProfile;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;

/** Authentication feature placeholder for the feature-free Sprint 3 base. */
class DisabledAuthRepository implements AuthRepository {
    private Exception disabled() { return new UnsupportedOperationException("Authentication is not enabled in this base branch"); }
    @Nullable @Override public UserProfile getSignedInUser() { return null; }
    @Override public void signIn(String email, String password, Callback<UserProfile> callback) { MainThread.post(() -> callback.onError(disabled())); }
    @Override public void signInWithGoogle(String idToken, Callback<UserProfile> callback) { MainThread.post(() -> callback.onError(disabled())); }
    @Override public void register(String name, String email, String password, Callback<UserProfile> callback) { MainThread.post(() -> callback.onError(disabled())); }
    @Override public void sendPasswordReset(String email, Callback<Void> callback) { MainThread.post(() -> callback.onError(disabled())); }
    @Override public void signOut() { }
}
