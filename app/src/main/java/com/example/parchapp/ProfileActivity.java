package com.example.parchapp;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.Manifest;
import android.content.res.ColorStateList;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.example.parchapp.data.ProfilePhotoRepository;
import com.example.parchapp.data.ProfilePhotoStore;
import com.example.parchapp.data.ProfileRepository;
import com.example.parchapp.data.Repositories;
import com.example.parchapp.domain.model.ProfileDetails;
import com.example.parchapp.util.Callback;
import com.example.parchapp.domain.StatusService;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class ProfileActivity extends AppCompatActivity {
    private ActivityResultLauncher<String> cameraPermission;
    private ActivityResultLauncher<Intent> cameraLauncher;
    private TextView initialsView;
    private TextView photoErrorView;
    private TextView photoSavingView;
    private ImageView photoView;
    private UserProfile user;
    private ProfileDetails profile;
    private TextView profileError;
    private TextView profileFreshness;
    private View profileLoading;
    private View retryProfile;
    private boolean frontCameraRequested;
    private boolean photoBusy;
    private boolean profileBusy;
    private int photoLoadVersion;
    private final ConnectivityWatcher connectivity = new ConnectivityWatcher(this::loadProfileDetails);

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
        profileError = findViewById(R.id.profile_error);
        profileFreshness = findViewById(R.id.profile_freshness);
        profileLoading = findViewById(R.id.profile_loading);
        retryProfile = findViewById(R.id.profile_retry_button);
        findViewById(R.id.profile_back_button).setOnClickListener(v -> finish());
        findViewById(R.id.profile_edit_button).setOnClickListener(v -> showEditProfile());
        findViewById(R.id.profile_photo_edit_button).setOnClickListener(v -> showPhotoChoices());
        findViewById(R.id.profile_status_button).setOnClickListener(v -> showPresenceEditor());
        findViewById(R.id.profile_calendar_card).setOnClickListener(v ->
                startActivity(new Intent(this, ScheduleActivity.class)));
        findViewById(R.id.profile_privacy_card).setOnClickListener(v ->
                startActivity(new Intent(this, PrivacySettingsActivity.class)));
        findViewById(R.id.profile_groups_card).setOnClickListener(v ->
                startActivity(new Intent(this, GroupsActivity.class)));
        retryProfile.setOnClickListener(v -> loadProfileDetails());
        findViewById(R.id.profile_logout_button).setOnClickListener(v -> logout());
        BottomNav.setup(this, BottomNav.Tab.HOME);
        renderProfile();
        loadProfilePhoto();
    }

    @Override
    protected void onStart() {
        super.onStart();
        connectivity.start(findViewById(R.id.offline_banner));
        loadProfileDetails();
    }

    @Override
    protected void onStop() {
        connectivity.stop();
        super.onStop();
    }

    private void renderProfile() {
        user = UserSession.getCurrentUser();
        String name = profile != null && !profile.getName().isEmpty()
                ? profile.getName() : user == null ? "" : user.getFullName();
        initialsView.setText(user == null ? initials(name) : user.getInitials());
        ((TextView) findViewById(R.id.profile_name)).setText(name);
        ((TextView) findViewById(R.id.profile_program)).setText(profile == null || profile.getProgram().isEmpty()
                ? "Add your academic profile" : profile.getProgram());
        ((TextView) findViewById(R.id.profile_email)).setText(user == null ? "" : user.getEmail());
        findViewById(R.id.profile_verified_badge).setVisibility(
                profile != null && profile.isVerified() ? View.VISIBLE : View.GONE);

        String status = profile == null ? localStatusLabel(Repositories.status()) : profile.getStatusLabel();
        ((TextView) findViewById(R.id.profile_status_text)).setText(status);
        boolean available = "active".equals(profile == null ? "" : profile.getEffectiveStatus())
                || "Available now".equals(status);
        int statusColor = getColor(available ? R.color.brand_success : R.color.brand_slate);
        findViewById(R.id.profile_status_dot).setBackgroundTintList(ColorStateList.valueOf(statusColor));
        ((TextView) findViewById(R.id.profile_status_text)).setTextColor(getColor(
                available ? R.color.brand_success_dark : R.color.brand_slate));
        findViewById(R.id.profile_status_button).setEnabled(profile != null && !profileBusy);

        ((TextView) findViewById(R.id.profile_semester)).setText(profile == null
                ? "Semester not set" : semesterLabel(profile.getSemester(), profile.getYear()));
        ((TextView) findViewById(R.id.profile_academic_level)).setText(profile == null
                ? "Not set" : profile.getAcademicLevelLabel());
        String campus = profile == null ? "" : joinNonEmpty(profile.getCampus(), profile.getUniversity(), " • ");
        ((TextView) findViewById(R.id.profile_campus)).setText(campus.isEmpty() ? "Campus not set" : campus);
        ((TextView) findViewById(R.id.profile_student_id)).setText(profile == null || profile.getStudentId().isEmpty()
                ? "Student ID not set" : "ID: " + profile.getStudentId());
        ((TextView) findViewById(R.id.profile_academic_status)).setText(status);
        setStat(R.id.profile_plans_stat, R.drawable.ic_calendar,
                profile == null ? "—" : String.valueOf(profile.getPlansCompleted()), "Plans completed");
        setStat(R.id.profile_groups_stat, R.drawable.ic_group,
                profile == null ? "—" : String.valueOf(profile.getActiveGroups()), "Active groups");
        setStat(R.id.profile_rate_stat, R.drawable.ic_timer,
                profile == null || profile.getOnTimeRate() == null ? "—" : profile.getOnTimeRate() + "%",
                "On-time rate");
        ((TextView) findViewById(R.id.profile_groups_subtitle)).setText(profile == null
                ? "Your study and activity groups" : profile.getActiveGroups() + " groups");
        findViewById(R.id.profile_edit_button).setEnabled(user != null && profile != null && !profileBusy);
        findViewById(R.id.profile_photo_edit_button).setEnabled(user != null && !photoBusy);
        if (profile != null && profile.isFromCache()) {
            profileFreshness.setVisibility(View.VISIBLE);
            profileFreshness.setText("Offline copy · " + formatSavedAt(profile.getSavedAtMillis())
                    + " · May be out of date. Reconnect to refresh.");
        } else {
            profileFreshness.setVisibility(View.GONE);
        }
    }

    private void loadProfileDetails() {
        UserProfile current = UserSession.getCurrentUser();
        ProfileRepository repository = Repositories.profileDetails();
        if (current == null || repository == null) {
            profileError.setText(current == null ? "Sign in to view your profile." : "Profile service unavailable.");
            profileError.setVisibility(View.VISIBLE);
            retryProfile.setVisibility(View.GONE);
            profileLoading.setVisibility(View.GONE);
            renderProfile();
            return;
        }
        profileLoading.setVisibility(profile == null ? View.VISIBLE : View.GONE);
        retryProfile.setVisibility(View.GONE);
        repository.load(current.getId(), new Callback<ProfileDetails>() {
            @Override public void onSuccess(ProfileDetails details) {
                if (isFinishing() || isDestroyed()) return;
                UserProfile latest = UserSession.getCurrentUser();
                if (latest == null || !latest.getId().equals(current.getId())) return;
                profile = details;
                profileError.setVisibility(View.GONE);
                profileLoading.setVisibility(View.GONE);
                renderProfile();
            }

            @Override public void onError(Exception error) {
                if (isFinishing() || isDestroyed()) return;
                profileLoading.setVisibility(View.GONE);
                profileError.setText("Unable to load your profile. Check your connection and retry.");
                profileError.setVisibility(View.VISIBLE);
                retryProfile.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showEditProfile() {
        if (profile == null || profileBusy) return;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(4), dp(4), dp(4));
        EditText name = editField("Full name", profile.getName(), InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        EditText program = editField("Program", profile.getProgram(), InputType.TYPE_CLASS_TEXT);
        EditText semester = editField("Semester", profile.getSemester() == 0 ? "" : String.valueOf(profile.getSemester()),
                InputType.TYPE_CLASS_NUMBER);
        EditText year = editField("Year", profile.getYear() == 0 ? "" : String.valueOf(profile.getYear()),
                InputType.TYPE_CLASS_NUMBER);
        EditText campus = editField("Campus", profile.getCampus(), InputType.TYPE_CLASS_TEXT);
        EditText university = editField("University", profile.getUniversity(), InputType.TYPE_CLASS_TEXT);
        EditText studentId = editField("Student ID", profile.getStudentId(), InputType.TYPE_CLASS_TEXT);
        addField(form, name);
        addField(form, program);
        Spinner level = spinner(new String[]{"Not set", "Undergraduate", "Graduate", "Master", "PhD"});
        int savedLevel = academicLevelIndex(profile.getAcademicLevel());
        level.setSelection(savedLevel);
        form.addView(level, fieldLayout());
        addField(form, semester);
        addField(form, year);
        addField(form, campus);
        addField(form, university);
        addField(form, studentId);
        TextView photoAction = new TextView(this);
        photoAction.setText("Change profile photo");
        photoAction.setTextColor(getColor(R.color.brand_primary));
        photoAction.setTextSize(14);
        photoAction.setPadding(dp(4), dp(12), dp(4), dp(8));
        photoAction.setOnClickListener(v -> {
            ((View) v.getParent()).setTag("photo_action");
        });
        form.addView(photoAction);

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(440)));
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit profile")
                .setView(scroll)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save changes", null)
                .create();
        photoAction.setOnClickListener(v -> {
            dialog.dismiss();
            showPhotoChoices();
        });
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String fullName = name.getText().toString().trim();
            if (fullName.isEmpty()) {
                name.setError("Enter your name");
                return;
            }
            int semesterValue = parseOptionalNumber(semester, 0, 20, "Semester");
            int yearValue = parseOptionalNumber(year, 0, 2100, "Year");
            if (semesterValue < 0 || yearValue < 0) return;
            String[] levels = {"", "undergraduate", "graduate", "master", "phd"};
            ProfileDetails updated = profile.withEditableFields(fullName,
                    program.getText().toString().trim(), semesterValue, yearValue,
                    levels[level.getSelectedItemPosition()], campus.getText().toString().trim(),
                    university.getText().toString().trim(), studentId.getText().toString().trim());
            saveProfile(updated, dialog);
        }));
        dialog.show();
    }

    private void showPresenceEditor() {
        if (profile == null || profileBusy) return;
        ProfileRepository repository = Repositories.profileDetails();
        if (repository == null) {
            Toast.makeText(this, "Availability updates require the backend.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = {"Available now", "In class", "Focused", "Busy", "In transit"};
        String[] values = {"active", "inClass", "focused", "busy", "inTransit"};
        String[] durations = {"For 1 hour", "Until 6:00 PM", "Until tomorrow"};
        String[] visibilityLabels = {"Shared group members", "Close friends", "Only me"};
        String[] visibilityValues = {"groups", "closeFriends", "private"};
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(4), dp(4), dp(4), dp(4));
        Spinner status = spinner(labels);
        Spinner duration = spinner(durations);
        Spinner visibility = spinner(visibilityLabels);
        status.setSelection(indexOf(values, profile.getStatus(), 0));
        duration.setSelection(0);
        visibility.setSelection(indexOf(visibilityValues, profile.getStatusVisibility(), 0));
        addLabeledSpinner(form, "Status", status);
        addLabeledSpinner(form, "Duration", duration);
        addLabeledSpinner(form, "Visible to", visibility);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Availability status")
                .setView(form)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save status", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String selectedStatus = values[status.getSelectedItemPosition()];
            String selectedDuration = durations[duration.getSelectedItemPosition()];
            long expiry = statusExpiry(selectedDuration);
            String selectedVisibility = visibilityValues[visibility.getSelectedItemPosition()];
            setProfileBusy(true);
            repository.updatePresence(profile.getUserId(), selectedStatus, isoUtc(expiry), selectedVisibility,
                    new Callback<ProfileDetails>() {
                        @Override public void onSuccess(ProfileDetails updated) {
                            if (isFinishing() || isDestroyed()) return;
                            profile = updated;
                            syncLocalStatus(selectedStatus, selectedDuration);
                            setProfileBusy(false);
                            renderProfile();
                            dialog.dismiss();
                        }

                        @Override public void onError(Exception error) {
                            if (isFinishing() || isDestroyed()) return;
                            setProfileBusy(false);
                            Toast.makeText(ProfileActivity.this,
                                    "Could not save availability. Your previous status is unchanged.",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        }));
        dialog.show();
    }

    private void saveProfile(ProfileDetails updated, AlertDialog dialog) {
        ProfileRepository repository = Repositories.profileDetails();
        if (repository == null || profileBusy) return;
        setProfileBusy(true);
        repository.updateProfile(updated, new Callback<ProfileDetails>() {
            @Override public void onSuccess(ProfileDetails saved) {
                if (isFinishing() || isDestroyed()) return;
                profile = saved;
                UserProfile current = UserSession.getCurrentUser();
                if (current != null && !current.getFullName().equals(saved.getName())) {
                    user = current.withFullName(saved.getName());
                    UserSession.setCurrentUser(user);
                }
                setProfileBusy(false);
                renderProfile();
                dialog.dismiss();
                Toast.makeText(ProfileActivity.this, "Profile updated", Toast.LENGTH_SHORT).show();
            }

            @Override public void onError(Exception error) {
                if (isFinishing() || isDestroyed()) return;
                setProfileBusy(false);
                profileError.setText("Could not save your profile. Your changes are still in the form.");
                profileError.setVisibility(View.VISIBLE);
            }
        });
    }

    private void setProfileBusy(boolean busy) {
        profileBusy = busy;
        findViewById(R.id.profile_edit_button).setEnabled(!busy && profile != null);
        findViewById(R.id.profile_status_button).setEnabled(!busy && profile != null);
        if (busy) {
            profileLoading.setVisibility(View.VISIBLE);
            profileError.setVisibility(View.GONE);
        } else {
            profileLoading.setVisibility(View.GONE);
        }
    }

    private EditText editField(String hint, String value, int inputType) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setHint(hint);
        field.setText(value);
        field.setInputType(inputType);
        return field;
    }

    private void addField(LinearLayout parent, EditText field) {
        parent.addView(field, fieldLayout());
    }

    private LinearLayout.LayoutParams fieldLayout() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        return params;
    }

    private Spinner spinner(String[] labels) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels);
        spinner.setAdapter(adapter);
        return spinner;
    }

    private void addLabeledSpinner(LinearLayout parent, String title, Spinner spinner) {
        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(getColor(R.color.brand_slate));
        label.setTextSize(12);
        parent.addView(label);
        parent.addView(spinner, fieldLayout());
    }

    private int parseOptionalNumber(EditText field, int min, int max, String label) {
        String raw = field.getText().toString().trim();
        if (raw.isEmpty()) return 0;
        try {
            int value = Integer.parseInt(raw);
            if (value < min || value > max) {
                field.setError(label + " must be between " + min + " and " + max);
                return -1;
            }
            return value;
        } catch (NumberFormatException error) {
            field.setError("Enter a valid " + label.toLowerCase(Locale.ROOT));
            return -1;
        }
    }

    private int academicLevelIndex(String value) {
        switch (value) {
            case "undergraduate": return 1;
            case "graduate": return 2;
            case "master": return 3;
            case "phd": return 4;
            default: return 0;
        }
    }

    private int indexOf(String[] values, String value, int fallback) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return i;
        return fallback;
    }

    private void setStat(int containerId, int iconId, String value, String label) {
        View container = findViewById(containerId);
        ((ImageView) container.findViewById(R.id.profile_stat_icon)).setImageResource(iconId);
        ((TextView) container.findViewById(R.id.profile_stat_value)).setText(value);
        ((TextView) container.findViewById(R.id.profile_stat_label)).setText(label);
    }

    private String semesterLabel(int semester, int year) {
        if (semester <= 0) return "Semester not set";
        String label = semester + ordinalSuffix(semester) + " Semester";
        return year <= 0 ? label : label + " • " + year;
    }

    private String ordinalSuffix(int number) {
        int lastTwo = number % 100;
        if (lastTwo >= 11 && lastTwo <= 13) return "th";
        switch (number % 10) {
            case 1: return "st";
            case 2: return "nd";
            case 3: return "rd";
            default: return "th";
        }
    }

    private String joinNonEmpty(String first, String second, String separator) {
        String a = first == null ? "" : first.trim();
        String b = second == null ? "" : second.trim();
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        return a + separator + b;
    }

    private String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "";
        String[] words = name.trim().split("\\s+");
        return (words[0].substring(0, 1) + (words.length > 1
                ? words[words.length - 1].substring(0, 1) : "")).toUpperCase(Locale.ROOT);
    }

    private String localStatusLabel(StatusService status) {
        if (status == null) return "Availability unavailable";
        return status.getAvailability();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private String formatSavedAt(long timestamp) {
        SimpleDateFormat format = new SimpleDateFormat("d MMM yyyy, h:mm a", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("America/Bogota"));
        return "Saved " + format.format(new Date(timestamp)) + " · Bogotá";
    }

    private long statusExpiry(String duration) {
        long now = System.currentTimeMillis();
        if ("Until tomorrow".equals(duration)) return now + 24L * 60 * 60 * 1000;
        if ("Until 6:00 PM".equals(duration)) {
            java.util.Calendar cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("America/Bogota"));
            cal.setTimeInMillis(now);
            cal.set(java.util.Calendar.HOUR_OF_DAY, 18);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            if (cal.getTimeInMillis() <= now) cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
            return cal.getTimeInMillis();
        }
        return now + 60L * 60 * 1000;
    }

    private String isoUtc(long timestamp) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(timestamp));
    }

    private void syncLocalStatus(String backendStatus, String duration) {
        String local = null;
        switch (backendStatus) {
            case "active": local = "Available now"; break;
            case "inClass": local = "In Class"; break;
            case "focused": local = "Deep Focus"; break;
            case "busy": local = "Busy / Personal"; break;
            case "inTransit": local = "Commute / In Transit"; break;
        }
        if (local != null) Repositories.status().setAvailability(local, duration);
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
        findViewById(R.id.profile_photo_edit_button).setEnabled(!busy && user != null);
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
        new AlertDialog.Builder(this)
                .setTitle("Log out?")
                .setMessage("You will need to sign in again.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log out", (dialog, which) -> confirmLogout())
                .show();
    }

    private void confirmLogout() {
        Repositories.auth().signOut();
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
