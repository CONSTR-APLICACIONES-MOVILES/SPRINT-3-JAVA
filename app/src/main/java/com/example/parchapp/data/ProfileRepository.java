package com.example.parchapp.data;

import com.example.parchapp.domain.model.ProfileDetails;
import com.example.parchapp.util.Callback;

/** Reads and updates the signed-in user's profile through the existing mobile callables. */
public interface ProfileRepository {
    void load(String userId, Callback<ProfileDetails> callback);

    void updateProfile(ProfileDetails profile, Callback<ProfileDetails> callback);

    void updatePresence(String userId, String status, String expiresAt, String visibility,
                        Callback<ProfileDetails> callback);
}
