package com.example.parchapp;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.Manifest;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.example.parchapp.data.ProfilePhotoRepository;
import com.example.parchapp.data.ProfilePhotoStore;
import com.example.parchapp.data.Repositories;
import com.example.parchapp.util.Callback;

import java.io.File;

public class ProfileActivity extends AppCompatActivity {
    private ActivityResultLauncher<String> cameraPermission;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private TextView initialsView;
    private TextView photoErrorView;
    private TextView photoSavingView;
    private ImageView photoView;
    private UserProfile user;
    private boolean frontCameraRequested;
    private boolean photoBusy;
    private int photoLoadVersion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        cameraPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) {
                openCamera();
            } else {
                showPhotoError(R.string.profile_photo_permission_denied);
            }
        });
        cameraLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            Intent data = result.getData();
            if (data != null && data.getBooleanExtra(ProfilePhotoCameraActivity.EXTRA_PERMISSION_DENIED, false)) {
                showPhotoError(R.string.profile_photo_permission_denied);
            } else if (result.getResultCode() == RESULT_OK && data != null) {
                String path = data.getStringExtra(ProfilePhotoCameraActivity.EXTRA_CAPTURED_PATH);
                if (path != null) {
                    saveCapturedPhoto(new File(path));
                } else {
                    showPhotoError(R.string.profile_photo_save_error);
                }
            }
            // A normal camera cancellation has no result payload and leaves the current avatar untouched.
        });

        initialsView = findViewById(R.id.profile_initials);
        photoView = findViewById(R.id.profile_photo);
        photoErrorView = findViewById(R.id.profile_photo_error);
        photoSavingView = findViewById(R.id.profile_photo_saving);
        findViewById(R.id.profile_back_button).setOnClickListener(v -> finish());
        findViewById(R.id.profile_edit_button).setOnClickListener(v -> showPhotoChoices());
        findViewById(R.id.profile_status_button).setEnabled(false);
        findViewById(R.id.profile_calendar_card).setOnClickListener(v ->
                startActivity(new Intent(this, PrivacySettingsActivity.class)));
        findViewById(R.id.profile_logout_button).setOnClickListener(v -> logout());
        renderViewOnlyProfile();
        loadProfilePhoto();
    }

    private void renderViewOnlyProfile() {
        user = UserSession.getCurrentUser();
        String name = user == null ? "" : user.getFullName();
        String initials = user == null ? "" : user.getInitials();
        initialsView.setText(initials);
        ((TextView) findViewById(R.id.profile_name)).setText(name);
        ((TextView) findViewById(R.id.profile_program)).setText("");
        ((TextView) findViewById(R.id.profile_status_button)).setText("Profile view only");
        ((TextView) findViewById(R.id.profile_plans_count)).setText("—\nPlans completed");
        ((TextView) findViewById(R.id.profile_groups_count)).setText("—\nActive groups");
        ((TextView) findViewById(R.id.profile_rate)).setText("—\nOn-time rate");
        ((TextView) findViewById(R.id.profile_academic_card)).setText("Academic information");
        ((TextView) findViewById(R.id.profile_groups_card)).setText("Groups");
        ((TextView) findViewById(R.id.profile_calendar_card)).setText("Privacy and settings  ›");
        findViewById(R.id.profile_edit_button).setEnabled(user != null);
    }

    private void loadProfilePhoto() {
        if (user == null) {
            return;
        }
        Bitmap cached = ProfilePhotoStore.loadCachedBitmap(this, user.getId());
        if (cached != null) {
            showAvatar(cached);
        }

        ProfilePhotoRepository repository = Repositories.profilePhotos();
        if (repository == null) {
            return;
        }
        int requestVersion = ++photoLoadVersion;
        repository.loadCurrentPhoto(new Callback<byte[]>() {
            @Override
            public void onSuccess(byte[] photo) {
                if (photo == null || requestVersion != photoLoadVersion || photoBusy) {
                    return;
                }
                ProfilePhotoStore.execute(() -> {
                    File staged = null;
                    try {
                        Bitmap bitmap = ProfilePhotoStore.decodeImage(photo);
                        if (bitmap == null) {
                            throw new IllegalArgumentException("The saved profile photo could not be decoded.");
                        }
                        staged = ProfilePhotoStore.stagePhoto(ProfileActivity.this, user.getId(), photo);
                        ProfilePhotoStore.commitPhoto(ProfileActivity.this, user.getId(), staged);
                        runOnUiThread(() -> {
                            if (isFinishing() || isDestroyed() || requestVersion != photoLoadVersion || photoBusy) {
                                return;
                            }
                            showAvatar(bitmap);
                        });
                    } catch (Exception error) {
                        ProfilePhotoStore.deleteQuietly(staged);
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                // Keep a usable local avatar when the network is unavailable.
            }
        });
    }

    private void showPhotoChoices() {
        if (photoBusy || user == null) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.profile_photo_edit_title)
                .setItems(new CharSequence[]{getString(R.string.profile_photo_selfie),
                        getString(R.string.profile_photo_rear_camera)}, (dialog, selected) -> {
                    frontCameraRequested = selected == 0;
                    requestCameraPermission();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void requestCameraPermission() {
        clearPhotoError();
        if (Repositories.profilePhotos() == null) {
            showPhotoError(R.string.profile_photo_backend_unavailable);
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void openCamera() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        Intent intent = new Intent(this, ProfilePhotoCameraActivity.class)
                .putExtra(ProfilePhotoCameraActivity.EXTRA_FRONT_CAMERA, frontCameraRequested);
        cameraLauncher.launch(intent);
    }

    private void saveCapturedPhoto(File capturedFile) {
        if (user == null || photoBusy) {
            ProfilePhotoStore.deleteQuietly(capturedFile);
            return;
        }
        ProfilePhotoRepository repository = Repositories.profilePhotos();
        if (repository == null) {
            ProfilePhotoStore.deleteQuietly(capturedFile);
            showPhotoError(R.string.profile_photo_backend_unavailable);
            return;
        }

        photoLoadVersion++;
        setPhotoBusy(true);
        clearPhotoError();
        String userId = user.getId();
        ProfilePhotoStore.execute(() -> {
            final byte[] jpeg;
            final File staged;
            try {
                jpeg = ProfilePhotoStore.compressForProfile(capturedFile);
                staged = ProfilePhotoStore.stagePhoto(this, userId, jpeg);
            } catch (Exception error) {
                ProfilePhotoStore.deleteQuietly(capturedFile);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        setPhotoBusy(false);
                        showPhotoError(R.string.profile_photo_save_error);
                    }
                });
                return;
            }
            ProfilePhotoStore.deleteQuietly(capturedFile);
            repository.savePhoto(userId, jpeg, new Callback<Void>() {
                @Override
                public void onSuccess(Void ignored) {
                    ProfilePhotoStore.execute(() -> finishPhotoSave(userId, jpeg, staged));
                }

                @Override
                public void onError(Exception error) {
                    ProfilePhotoStore.deleteQuietly(staged);
                    if (!isFinishing() && !isDestroyed()) {
                        setPhotoBusy(false);
                        showPhotoError(R.string.profile_photo_save_error);
                    }
                }
            });
        });
    }

    private void finishPhotoSave(String userId, byte[] jpeg, File staged) {
        Bitmap bitmap = ProfilePhotoStore.decodeImage(jpeg);
        boolean cached = false;
        try {
            ProfilePhotoStore.commitPhoto(this, userId, staged);
            cached = true;
        } catch (Exception error) {
            ProfilePhotoStore.deleteQuietly(staged);
        }
        Bitmap savedBitmap = bitmap;
        boolean savedLocally = cached;
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            setPhotoBusy(false);
            if (savedBitmap != null) {
                showAvatar(savedBitmap);
            }
            if (savedLocally) {
                Toast.makeText(this, R.string.profile_photo_updated, Toast.LENGTH_SHORT).show();
            } else {
                showPhotoError(R.string.profile_photo_cache_error);
            }
        });
    }

    private void showAvatar(Bitmap bitmap) {
        photoView.setImageBitmap(bitmap);
        photoView.setVisibility(View.VISIBLE);
        initialsView.setVisibility(View.GONE);
    }

    private void setPhotoBusy(boolean busy) {
        photoBusy = busy;
        findViewById(R.id.profile_edit_button).setEnabled(!busy && user != null);
        photoSavingView.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    private void clearPhotoError() {
        photoErrorView.setText("");
        photoErrorView.setVisibility(View.GONE);
    }

    private void showPhotoError(int message) {
        photoErrorView.setText(message);
        photoErrorView.setVisibility(View.VISIBLE);
    }

    private void logout() {
        Repositories.auth().signOut();
        Repositories.availabilityStatusSync().onUserSignedOut();
        UserSession.clear();
        Intent signIn = new Intent(this, MainActivity.class);
        signIn.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(signIn);
        finishAffinity();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

}
