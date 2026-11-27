package com.example.parchapp;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.example.parchapp.analytics.AnalyticsTracker;
import com.example.parchapp.data.ActivityDraftsRepository;
import com.example.parchapp.data.Repositories;
import com.example.parchapp.domain.model.SavedActivityDraft;
import com.example.parchapp.util.Callback;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Lists the local activity drafts and continues or deletes them. */
public class ActivityDraftsActivity extends AppCompatActivity {
    private ActivityDraftsRepository repository;
    private LinearLayout container;
    private ProgressBar loading;
    private TextView message;
    private String renderedSignature;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity_drafts);
        repository = Repositories.activityDrafts();
        container = findViewById(R.id.drafts_container);
        loading = findViewById(R.id.drafts_loading);
        message = findViewById(R.id.drafts_message);
        findViewById(R.id.drafts_back).setOnClickListener(v -> finish());
        findViewById(R.id.drafts_sync).setOnClickListener(v -> loadAndSync());
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadAndSync();
    }

    private void loadAndSync() {
        setLoading(true);
        repository.load(new Callback<List<SavedActivityDraft>>() {
            @Override public void onSuccess(List<SavedActivityDraft> drafts) {
                render(drafts);
                setLoading(false);
                if (AnalyticsTracker.getInstance().isOnline()) {
                    repository.sync(new Callback<Void>() {
                        @Override public void onSuccess(Void ignored) {
                            repository.load(new Callback<List<SavedActivityDraft>>() {
                                @Override public void onSuccess(List<SavedActivityDraft> synced) {
                                    render(synced);
                                }

                                @Override public void onError(Exception error) { }
                            });
                        }

                        @Override public void onError(Exception error) { }
                    });
                }
            }

            @Override public void onError(Exception error) {
                setLoading(false);
                showMessage("Unable to access saved drafts.");
            }
        });
    }

    private void render(List<SavedActivityDraft> drafts) {
        String signature = signature(drafts);
        if (signature.equals(renderedSignature)) return;
        renderedSignature = signature;
        long started = System.nanoTime();
        com.example.parchapp.util.BackgroundThreads.beginSection("ActivityDrafts.render");
        try {
            renderRows(drafts);
        } finally {
            com.example.parchapp.util.BackgroundThreads.endSection();
            android.util.Log.d("ParchPerf", String.format(Locale.US,
                    "Drafts.render %d rows in %.2f ms",
                    drafts.size(), (System.nanoTime() - started) / 1_000_000.0));
        }
    }

    private static String signature(List<SavedActivityDraft> drafts) {
        StringBuilder value = new StringBuilder();
        for (SavedActivityDraft draft : drafts) {
            value.append(draft.getId()).append(':')
                    .append(draft.getRevision()).append(':')
                    .append(draft.getUpdatedAt()).append(':')
                    .append(draft.getSyncStatus()).append(':')
                    .append(draft.getLocalState()).append(':')
                    .append(draft.getActivity().getTitle()).append('|');
        }
        return value.toString();
    }

    private void renderRows(List<SavedActivityDraft> drafts) {
        container.removeAllViews();
        if (drafts.isEmpty()) {
            showMessage("No drafts yet. Return to New Activity and choose Save draft.");
            return;
        }
        message.setVisibility(View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (SavedActivityDraft draft : drafts) {
            View row = inflater.inflate(R.layout.item_activity_draft, container, false);
            ((TextView) row.findViewById(R.id.draft_title)).setText(
                    draft.getActivity().getTitle().trim().isEmpty()
                            ? "Untitled activity" : draft.getActivity().getTitle());
            ((TextView) row.findViewById(R.id.draft_meta)).setText(
                    draft.getActivity().getCategory() + " · " + formatDate(draft.getUpdatedAt()));
            ((TextView) row.findViewById(R.id.draft_status)).setText(status(draft));
            AppCompatButton continueButton = row.findViewById(R.id.draft_continue);
            AppCompatButton deleteButton = row.findViewById(R.id.draft_delete);
            View conflictActions = row.findViewById(R.id.draft_conflict_actions);
            boolean conflict = draft.isConflict();
            continueButton.setEnabled(!conflict && draft.isActive());
            continueButton.setOnClickListener(v -> continueDraft(draft));
            deleteButton.setOnClickListener(v -> confirmDelete(draft));
            conflictActions.setVisibility(conflict ? View.VISIBLE : View.GONE);
            if (conflict) {
                row.findViewById(R.id.draft_use_cloud).setOnClickListener(v -> resolve(draft, false));
                row.findViewById(R.id.draft_keep_local).setOnClickListener(v -> resolve(draft, true));
            }
            container.addView(row);
        }
    }

    private void continueDraft(SavedActivityDraft draft) {
        CreateActivitySheet.newInstance(draft.getActivity().getGroupId(), 0, null, -1)
                .withDraft(draft)
                .show(getSupportFragmentManager(), "create_activity");
    }

    private void confirmDelete(SavedActivityDraft draft) {
        new AlertDialog.Builder(this)
                .setTitle("Delete draft?")
                .setMessage("The draft will be removed here and from your cloud drafts after synchronization.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    repository.delete(draft, new Callback<Void>() {
                        @Override public void onSuccess(Void ignored) { loadAndSync(); }
                        @Override public void onError(Exception error) {
                            Toast.makeText(ActivityDraftsActivity.this,
                                    "Could not delete this draft.", Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .show();
    }

    private void resolve(SavedActivityDraft draft, boolean keepLocal) {
        if (keepLocal) {
            repository.keepLocalAsNew(draft, new Callback<SavedActivityDraft>() {
                @Override public void onSuccess(SavedActivityDraft ignored) { loadAndSync(); }
                @Override public void onError(Exception error) { loadAndSync(); }
            });
        } else {
            repository.useCloud(draft, new Callback<Void>() {
                @Override public void onSuccess(Void ignored) { loadAndSync(); }
                @Override public void onError(Exception error) { loadAndSync(); }
            });
        }
    }

    private static String status(SavedActivityDraft draft) {
        if (draft.isConflict()) return "Changed on another device. Choose which version to keep.";
        if (SavedActivityDraft.SYNC_SYNCED.equals(draft.getSyncStatus())) {
            return "Saved in your cloud account";
        }
        if (SavedActivityDraft.SYNC_PENDING.equals(draft.getSyncStatus())) {
            return "Saved here · cloud sync pending";
        }
        return "Saved on this device";
    }

    private static String formatDate(long timestamp) {
        SimpleDateFormat format = new SimpleDateFormat("d MMM · h:mm a", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("America/Bogota"));
        return format.format(new Date(timestamp)) + " · Bogotá";
    }

    private void setLoading(boolean visible) {
        loading.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) message.setVisibility(View.GONE);
    }

    private void showMessage(String text) {
        message.setText(text);
        message.setVisibility(View.VISIBLE);
    }
}
