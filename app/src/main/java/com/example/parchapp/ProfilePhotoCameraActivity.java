package com.example.parchapp;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.parchapp.data.ProfilePhotoStore;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** CameraX capture screen, with the requested lens chosen before the camera opens. */
public final class ProfilePhotoCameraActivity extends AppCompatActivity {
    public static final String EXTRA_FRONT_CAMERA = "profile_photo_front_camera";
    public static final String EXTRA_CAPTURED_PATH = "profile_photo_captured_path";
    public static final String EXTRA_PERMISSION_DENIED = "profile_photo_permission_denied";

    private PreviewView previewView;
    private TextView errorText;
    private View captureButton;
    private ImageCapture imageCapture;
    private ProcessCameraProvider cameraProvider;
    private ExecutorService cameraExecutor;
    private File captureFile;
    private boolean captureDelivered;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(android.graphics.Color.BLACK);
        getWindow().setNavigationBarColor(android.graphics.Color.BLACK);
        setContentView(R.layout.activity_profile_photo_camera);

        previewView = findViewById(R.id.profile_camera_preview);
        errorText = findViewById(R.id.profile_camera_error);
        captureButton = findViewById(R.id.profile_camera_capture);
        cameraExecutor = Executors.newSingleThreadExecutor();

        findViewById(R.id.profile_camera_cancel).setOnClickListener(v -> finish());
        captureButton.setOnClickListener(v -> capturePhoto());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            setResult(RESULT_CANCELED, new Intent().putExtra(EXTRA_PERMISSION_DENIED, true));
            finish();
            return;
        }
        previewView.post(this::startCamera);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            try {
                cameraProvider = providerFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetRotation(previewView.getDisplay().getRotation())
                        .build();
                boolean front = getIntent().getBooleanExtra(EXTRA_FRONT_CAMERA, false);
                CameraSelector selector = new CameraSelector.Builder()
                        .requireLensFacing(front
                                ? CameraSelector.LENS_FACING_FRONT
                                : CameraSelector.LENS_FACING_BACK)
                        .build();
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, selector, preview, imageCapture);
                errorText.setVisibility(View.GONE);
                captureButton.setEnabled(true);
            } catch (Exception error) {
                showCameraError();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void capturePhoto() {
        if (imageCapture == null || !captureButton.isEnabled()) {
            return;
        }
        try {
            captureFile = ProfilePhotoStore.createCaptureFile(this);
        } catch (Exception error) {
            showCameraError();
            return;
        }

        captureButton.setEnabled(false);
        ImageCapture.OutputFileOptions output = new ImageCapture.OutputFileOptions.Builder(captureFile).build();
        imageCapture.takePicture(output, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults result) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        ProfilePhotoStore.deleteQuietly(captureFile);
                        return;
                    }
                    captureDelivered = true;
                    Intent data = new Intent().putExtra(EXTRA_CAPTURED_PATH, captureFile.getAbsolutePath());
                    setResult(RESULT_OK, data);
                    finish();
                });
            }

            @Override
            public void onError(@NonNull ImageCaptureException error) {
                ProfilePhotoStore.deleteQuietly(captureFile);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        captureButton.setEnabled(true);
                        showCameraError();
                    }
                });
            }
        });
    }

    private void showCameraError() {
        errorText.setText(R.string.profile_camera_error);
        errorText.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (!captureDelivered) {
            ProfilePhotoStore.deleteQuietly(captureFile);
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
        super.onDestroy();
    }
}
