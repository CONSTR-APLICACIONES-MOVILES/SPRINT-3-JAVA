package com.example.parchapp.data;

import androidx.annotation.Nullable;

import com.example.parchapp.UserProfile;
import com.example.parchapp.util.Callback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;

/* Firebase keeps the token and restores the session on the next launch. */
class FirebaseAuthRepository implements AuthRepository {
    private final FirebaseAuth auth;

    FirebaseAuthRepository(FirebaseAuth auth) {
        this.auth = auth;
    }

    @Nullable
    @Override
    public UserProfile getSignedInUser() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : toProfile(user);
    }

    @Override
    public void signIn(String email, String password, Callback<UserProfile> callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> callback.onSuccess(toProfile(result.getUser())))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void signInWithGoogle(String idToken, Callback<UserProfile> callback) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnSuccessListener(result -> callback.onSuccess(toProfile(result.getUser())))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void register(String fullName, String email, String password, Callback<UserProfile> callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (fullName.isEmpty()) {
                        callback.onSuccess(toProfile(user));
                        return;
                    }
                    // The account already exists, so a failed name update still signs the user in.
                    user.updateProfile(new UserProfileChangeRequest.Builder().setDisplayName(fullName).build())
                            .addOnCompleteListener(task -> callback.onSuccess(
                                    UserProfile.fromAccount(user.getUid(), user.getEmail(), fullName)));
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void sendPasswordReset(String email, Callback<Void> callback) {
        auth.sendPasswordResetEmail(email)
                .addOnSuccessListener(callback::onSuccess)
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void signOut() {
        auth.signOut();
    }

    private static UserProfile toProfile(FirebaseUser user) {
        return UserProfile.fromAccount(user.getUid(), user.getEmail(), user.getDisplayName());
    }
}
