package com.example.parchapp.data;

import android.util.Base64;

import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Reads and writes the profile's bounded photoData using the existing Backend callables. */
final class FirebaseProfilePhotoRepository implements ProfilePhotoRepository {
    private static final int MAX_PHOTO_BYTES = 130_000;
    private static final String DATA_PREFIX = "data:image/jpeg;base64,";

    private final FirebaseFunctions functions;
    private final FirebaseAuth auth;

    FirebaseProfilePhotoRepository(FirebaseFunctions functions, FirebaseAuth auth) {
        this.functions = functions;
        this.auth = auth;
    }

    @Override
    public void loadCurrentPhoto(Callback<byte[]> callback) {
        functions.getHttpsCallable("getProfile").call(Collections.emptyMap())
                .addOnSuccessListener(result -> {
                    Object raw = asMap(result.getData()).get("photoData");
                    if (raw == null) {
                        MainThread.post(() -> callback.onSuccess(null));
                        return;
                    }
                    try {
                        byte[] photo = decodeDataUri(raw.toString());
                        MainThread.post(() -> callback.onSuccess(photo));
                    } catch (IllegalArgumentException error) {
                        MainThread.post(() -> callback.onError(error));
                    }
                })
                .addOnFailureListener(error -> MainThread.post(() -> callback.onError(error)));
    }

    @Override
    public void savePhoto(String userId, byte[] jpeg, Callback<Void> callback) {
        if (auth.getCurrentUser() == null || userId == null || userId.trim().isEmpty()
                || !userId.equals(auth.getCurrentUser().getUid())) {
            MainThread.post(() -> callback.onError(new IllegalStateException(
                    "Sign in again before updating your profile photo.")));
            return;
        }
        if (jpeg == null || jpeg.length == 0 || jpeg.length > MAX_PHOTO_BYTES) {
            MainThread.post(() -> callback.onError(new IllegalArgumentException(
                    "Choose a JPEG photo smaller than 130 KB.")));
            return;
        }

        Map<String, Object> fields = new HashMap<>();
        fields.put("photoData", DATA_PREFIX + Base64.encodeToString(jpeg, Base64.NO_WRAP));
        Map<String, Object> input = new HashMap<>();
        input.put("clientUserId", userId);
        input.put("fields", fields);
        functions.getHttpsCallable("updateProfile").call(input)
                .addOnSuccessListener(result -> MainThread.post(() -> callback.onSuccess(null)))
                .addOnFailureListener(error -> MainThread.post(() -> callback.onError(error)));
    }

    private static byte[] decodeDataUri(String value) {
        int comma = value.indexOf(',');
        if (!value.startsWith("data:image/") || comma < 0) {
            throw new IllegalArgumentException("The saved profile photo is invalid.");
        }
        String mime = value.substring(0, comma);
        if (!mime.equals("data:image/jpeg;base64") && !mime.equals("data:image/png;base64")
                && !mime.equals("data:image/webp;base64")) {
            throw new IllegalArgumentException("The saved profile photo has an unsupported format.");
        }
        byte[] bytes = Base64.decode(value.substring(comma + 1), Base64.DEFAULT);
        if (bytes.length == 0 || bytes.length > MAX_PHOTO_BYTES) {
            throw new IllegalArgumentException("The saved profile photo has an invalid size.");
        }
        return bytes;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.emptyMap();
    }
}
