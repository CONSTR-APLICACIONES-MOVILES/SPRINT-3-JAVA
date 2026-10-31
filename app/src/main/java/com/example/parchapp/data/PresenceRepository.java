package com.example.parchapp.data;

import com.example.parchapp.util.Callback;

/** Writes an authenticated user's shareable availability presence. */
public interface PresenceRepository {
    void updatePresence(String userId, String status, String visibility, String expiresAt,
                       Callback<Void> callback);
}
