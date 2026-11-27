package com.example.parchapp;

import android.app.Activity;
import android.net.Uri;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.ObjectKey;
import com.example.parchapp.data.ProfilePhotoStore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;
import java.io.IOException;

/* Caching with glide: the profile photo of the top bar. The photo saved on the phone wins (the one taken in Profile),
 * then the photo of the Google account; without either, the initials under it stay visible.
 * Glide decodes off the main thread, ties the request to the screen (it stops in onStop and is cleared in onDestroy)
 * and keeps the photo in memory and on disk, so opening another screen does not decode or download it again,
 * and offline the Google photo still comes from the disk cache. */
final class AccountPhoto {
    private static final String TAG = "AccountPhoto";

    private AccountPhoto() {
    }

    static void load(Activity activity, ImageView view, @Nullable UserProfile user) {
        if (user == null) {
            Glide.with(activity).clear(view);
            return;
        }
        // the transparent view sits on the initials, so while loading or after an error the initials are what shows
        view.setVisibility(ImageView.VISIBLE);
        File saved = savedPhoto(activity, user.getId());
        if (saved != null) {
            Glide.with(activity)
                    .load(saved)
                    // the file keeps its name when a new photo is taken, the date tells glide it is another image
                    .signature(new ObjectKey(saved.lastModified()))
                    // the file is already on the phone, only the small round result is worth saving on disk
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .circleCrop()
                    .into(view);
            return;
        }
        Uri google = googlePhoto();
        if (google != null) {
            Glide.with(activity)
                    .load(google)
                    // the downloaded bytes go to disk, so offline or in another screen there is no download again
                    .diskCacheStrategy(DiskCacheStrategy.DATA)
                    .circleCrop()
                    .into(view);
            return;
        }
        Glide.with(activity).clear(view);
    }

    @Nullable
    private static File savedPhoto(Activity activity, String userId) {
        try {
            File file = ProfilePhotoStore.cachedPhoto(activity, userId);
            return file.isFile() ? file : null;
        } catch (IOException error) {
            return null;
        }
    }

    // email accounts have no photo url, Google accounts do
    @Nullable
    private static Uri googlePhoto() {
        try {
            FirebaseUser account = FirebaseAuth.getInstance().getCurrentUser();
            return account == null ? null : account.getPhotoUrl();
        } catch (IllegalStateException firebaseNotConfigured) {
            Log.d(TAG, "Firebase is not configured, the initials stay");
            return null;
        }
    }
}
