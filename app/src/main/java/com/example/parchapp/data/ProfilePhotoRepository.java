package com.example.parchapp.data;

import com.example.parchapp.util.Callback;

/** Profile-photo data is synchronized through the authenticated mobile API. */
public interface ProfilePhotoRepository {
    void loadCurrentPhoto(Callback<byte[]> callback);

    void savePhoto(String userId, byte[] jpeg, Callback<Void> callback);
}
