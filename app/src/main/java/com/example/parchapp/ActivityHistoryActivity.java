package com.example.parchapp;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.example.parchapp.data.ActivityHistoryCache;
import com.example.parchapp.data.Repositories;
import com.example.parchapp.domain.history.ActivityHistorySupport;
import com.example.parchapp.domain.model.PlannedActivity;
import com.example.parchapp.util.Callback;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ActivityHistoryActivity extends AppCompatActivity {
    private static final String TAG = "ActivityHistory";

    private final ExecutorService summaryWorker = Executors.newSingleThreadExecutor();
    private final HistoryAdapter adapter = new HistoryAdapter();
    private TextView offlineBanner;
    private TextView errorText;
    private TextView emptyText;
    private TextView summaryText;
    private TextView totalText;
    private TextView confirmedText;
    private TextView thisMonthText;
    private ProgressBar loading;
    private ListView historyList;
    private View retryButton;
    private boolean requestInProgress;
    // the search: the whole history stays here and the list shows only what matches
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable applySearch = this::showFiltered;
    private List<PlannedActivity> allActivities = new ArrayList<>();
    private View searchRow;
    private EditText searchInput;
    private TextView noMatchText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity_history);

        offlineBanner = findViewById(R.id.history_offline_banner);
        errorText = findViewById(R.id.history_error);
        emptyText = findViewById(R.id.history_empty);
        summaryText = findViewById(R.id.history_summary);
        totalText = findViewById(R.id.history_total);
        confirmedText = findViewById(R.id.history_confirmed);
        thisMonthText = findViewById(R.id.history_this_month);
        loading = findViewById(R.id.history_loading);
        historyList = findViewById(R.id.history_list);
        retryButton = findViewById(R.id.history_retry);
        historyList.setAdapter(adapter);

        searchRow = findViewById(R.id.history_search_row);
        searchInput = findViewById(R.id.history_search_input);
        noMatchText = findViewById(R.id.history_no_match);

        findViewById(R.id.history_back).setOnClickListener(v -> finish());
        findViewById(R.id.history_refresh).setOnClickListener(v -> loadHistory());
        retryButton.setOnClickListener(v -> loadHistory());
        findViewById(R.id.history_search_toggle).setOnClickListener(v -> toggleSearch());
        findViewById(R.id.history_search_clear).setOnClickListener(v -> searchInput.setText(""));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            // wait 250 ms after the last key, so typing a word filters once
            @Override
            public void afterTextChanged(Editable s) {
                handler.removeCallbacks(applySearch);
                handler.postDelayed(applySearch, 250);
            }
        });
        loadHistory();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(applySearch);
        summaryWorker.shutdownNow();
        super.onDestroy();
    }

    // the magnifier of the top bar opens and closes the search; closing it clears the text
    private void toggleSearch() {
        boolean opening = searchRow.getVisibility() != View.VISIBLE;
        searchRow.setVisibility(opening ? View.VISIBLE : View.GONE);
        if (opening) {
            searchInput.requestFocus();
            InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
        } else {
            searchInput.setText("");
        }
    }

    // draws the history that matches the search: the summary above always counts the whole history
    private void showFiltered() {
        if (isFinishing() || isDestroyed() || allActivities.isEmpty()) return;
        List<PlannedActivity> shown = ActivityHistorySupport.filter(allActivities, searchInput.getText().toString());
        adapter.replace(shown);
        boolean none = shown.isEmpty();
        noMatchText.setVisibility(none ? View.VISIBLE : View.GONE);
        historyList.setVisibility(none ? View.GONE : View.VISIBLE);
    }

    private void loadHistory() {
        if (requestInProgress) return;
        requestInProgress = true;
        loading.setVisibility(View.VISIBLE);
        offlineBanner.setVisibility(View.GONE);
        errorText.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        emptyText.setVisibility(View.GONE);
        historyList.setVisibility(View.GONE);
        noMatchText.setVisibility(View.GONE);
        summaryText.setText(R.string.history_calculating);

        String userId = UserSession.getCurrentUser() == null
                ? null : UserSession.getCurrentUser().getId();
        if (userId == null || userId.trim().isEmpty()) {
            showFailure(null);
            return;
        }
        Repositories.activities().getActivities(userId, new Callback<List<PlannedActivity>>() {
            @Override
            public void onSuccess(List<PlannedActivity> activities) {
                if (isFinishing() || isDestroyed()) return;
                prepareSnapshot(new ArrayList<>(activities), userId, !hasValidatedNetwork());
            }

            @Override
            public void onError(Exception error) {
                if (isFinishing() || isDestroyed()) return;
                loadSavedSnapshot(userId, error);
            }
        });
    }

    private void prepareSnapshot(List<PlannedActivity> activities, String userId, boolean offline) {
        long now = System.currentTimeMillis();
        summaryWorker.execute(() -> {
            try {
                final List<PlannedActivity> past;
                final long savedAt;
                if (offline) {
                    // Firestore answers from its own copy without a network: show our saved copy with its real date,
                    // and do not save again, or the date would say "now" for old data
                    ActivityHistoryCache.Snapshot saved =
                            ActivityHistoryCache.read(getApplicationContext(), userId);
                    if (saved == null && activities.isEmpty()) {
                        // nothing saved and nothing in the Firestore copy: say it could not load, not "no activities"
                        runOnUiThread(() -> {
                            if (!isFinishing() && !isDestroyed()) showFailure(null);
                        });
                        return;
                    }
                    past = saved != null ? saved.activities
                            : ActivityHistorySupport.pastActivities(activities, now);
                    savedAt = ActivityHistorySupport.shownSavedAt(true, saved == null ? 0L : saved.savedAt, now);
                } else {
                    past = ActivityHistorySupport.pastActivities(activities, now);
                    ActivityHistoryCache.save(getApplicationContext(), userId, past, now);
                    savedAt = ActivityHistorySupport.shownSavedAt(false, 0L, now);
                }
                ActivityHistorySupport.Summary summary =
                        ActivityHistorySupport.summarize(past, savedAt > 0L ? savedAt : now);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        showSnapshot(past, savedAt, offline, summary, false);
                    }
                });
            } catch (RuntimeException error) {
                Log.e(TAG, "Could not prepare the activity history", error);
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) showFailure(error);
                });
            }
        });
    }

    private void loadSavedSnapshot(String userId, Exception fetchError) {
        Log.w(TAG, "Could not refresh the activity history", fetchError);
        boolean offline = !hasValidatedNetwork();
        summaryWorker.execute(() -> {
            ActivityHistoryCache.Snapshot saved =
                    ActivityHistoryCache.read(getApplicationContext(), userId);
            if (saved == null) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) showFailure(fetchError);
                });
                return;
            }
            ActivityHistorySupport.Summary summary =
                    ActivityHistorySupport.summarize(saved.activities, saved.savedAt);
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    showSnapshot(saved.activities, saved.savedAt, offline, summary, true);
                }
            });
        });
    }

    private void showSnapshot(List<PlannedActivity> activities, long savedAt, boolean offline,
                              ActivityHistorySupport.Summary summary, boolean refreshFailed) {
        requestInProgress = false;
        loading.setVisibility(View.GONE);
        if (offline) {
            offlineBanner.setText(getString(R.string.history_offline_saved, formatSavedAt(savedAt)));
            offlineBanner.setVisibility(View.VISIBLE);
        } else {
            offlineBanner.setVisibility(View.GONE);
        }
        if (refreshFailed) {
            errorText.setText(R.string.history_refresh_error);
            errorText.setVisibility(View.VISIBLE);
            retryButton.setVisibility(View.VISIBLE);
        } else {
            errorText.setVisibility(View.GONE);
        }
        totalText.setText(String.valueOf(summary.total));
        confirmedText.setText(String.valueOf(summary.confirmed));
        thisMonthText.setText(String.valueOf(summary.thisMonth));
        bindSummary(summary);
        allActivities = new ArrayList<>(activities);
        noMatchText.setVisibility(View.GONE);
        if (activities.isEmpty()) {
            adapter.replace(activities);
            emptyText.setVisibility(View.VISIBLE);
            historyList.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            // keeps the search the user already typed when the history is refreshed
            showFiltered();
        }
    }

    private void bindSummary(ActivityHistorySupport.Summary summary) {
        if (summary.total == 0 || summary.favoriteTime.isEmpty()) {
            summaryText.setText("");
        } else if (summary.favoritePlace.isEmpty()) {
            summaryText.setText(getString(R.string.history_summary_time,
                    summary.favoriteTime.toLowerCase(Locale.US)));
        } else {
            summaryText.setText(getString(R.string.history_summary_place,
                    summary.favoriteTime.toLowerCase(Locale.US), summary.favoritePlace));
        }
    }

    private void showFailure(Exception error) {
        requestInProgress = false;
        loading.setVisibility(View.GONE);
        summaryText.setText(R.string.history_summary_unavailable);
        totalText.setText("—");
        confirmedText.setText("—");
        thisMonthText.setText("—");
        errorText.setText(R.string.history_load_error);
        errorText.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        noMatchText.setVisibility(View.GONE);
        historyList.setVisibility(View.GONE);
        if (error != null) Log.w(TAG, "Activity history is unavailable", error);
    }

    private boolean hasValidatedNetwork() {
        ConnectivityManager manager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        Network active = manager.getActiveNetwork();
        NetworkCapabilities capabilities = active == null ? null : manager.getNetworkCapabilities(active);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private String formatSavedAt(long savedAt) {
        if (savedAt <= 0L) return getString(R.string.history_saved_unknown);
        return ActivityHistorySupport.withoutTrailingDot(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(new Date(savedAt)));
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class HistoryAdapter extends BaseAdapter {
        private final List<PlannedActivity> activities = new ArrayList<>();

        void replace(List<PlannedActivity> rows) {
            activities.clear();
            activities.addAll(rows);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return activities.size();
        }

        @Override
        public PlannedActivity getItem(int position) {
            return activities.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            RowHolder holder;
            if (convertView == null) {
                LinearLayout card = new LinearLayout(ActivityHistoryActivity.this);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setGravity(Gravity.CENTER_VERTICAL);
                card.setBackgroundResource(R.drawable.bg_dashboard_card);
                card.setElevation(dp(1));
                card.setPadding(dp(12), dp(12), dp(12), dp(12));

                ImageView map = new ImageView(ActivityHistoryActivity.this);
                map.setBackgroundColor(getColor(R.color.brand_chip_background));
                map.setScaleType(ImageView.ScaleType.CENTER_CROP);
                map.setContentDescription(null);
                card.addView(map, new LinearLayout.LayoutParams(dp(56), dp(56)));

                LinearLayout details = new LinearLayout(ActivityHistoryActivity.this);
                details.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams detailsParams =
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                detailsParams.setMargins(dp(12), 0, dp(8), 0);
                card.addView(details, detailsParams);

                TextView title = textView(15, R.color.brand_dark, true);
                TextView datePlace = textView(12, R.color.brand_slate, false);
                TextView people = textView(12, R.color.brand_slate, false);
                details.addView(title);
                details.addView(datePlace);
                details.addView(people);

                TextView status = textView(11, R.color.brand_slate, true);
                card.addView(status);
                holder = new RowHolder(map, title, datePlace, people, status);
                card.setTag(holder);
                convertView = card;
            } else {
                holder = (RowHolder) convertView.getTag();
            }

            PlannedActivity activity = getItem(position);
            holder.title.setText(activity.getTitle());
            holder.datePlace.setText(formatDateTime(activity)
                    + (activity.getLocation().trim().isEmpty() ? "" : " · " + activity.getLocation()));
            holder.people.setText(getString(R.string.history_people, activity.getParticipantCount()));
            boolean confirmed = PlannedActivity.STATUS_CONFIRMED.equalsIgnoreCase(activity.getStatus());
            holder.status.setText(confirmed
                    ? R.string.history_confirmed_status : R.string.history_proposed_status);
            holder.status.setTextColor(getColor(confirmed
                    ? R.color.brand_success_dark : R.color.brand_slate));
            loadMapTile(holder.map, activity);
            return convertView;
        }

        private TextView textView(int size, int color, boolean bold) {
            TextView view = new TextView(ActivityHistoryActivity.this);
            view.setTextSize(size);
            view.setTextColor(getColor(color));
            if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
            return view;
        }

        private void loadMapTile(ImageView image, PlannedActivity activity) {
            String tileUrl = ActivityHistorySupport.mapTileUrl(activity);
            if (tileUrl == null) {
                Glide.with(ActivityHistoryActivity.this).clear(image);
                image.setImageResource(R.drawable.ic_location);
                image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                return;
            }
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            GlideUrl request = new GlideUrl(tileUrl, new LazyHeaders.Builder()
                    .addHeader("User-Agent", "ParchApp/1.0 student project")
                    .build());
            Glide.with(ActivityHistoryActivity.this)
                    .load(request)
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .placeholder(R.drawable.ic_location)
                    .error(R.drawable.ic_location)
                    .centerCrop()
                    .into(image);
        }

        private String formatDateTime(PlannedActivity activity) {
            long startsAt = ActivityHistorySupport.startsAtMillis(activity);
            if (startsAt <= 0L) {
                return (activity.getDate() + " " + activity.getTime()).trim();
            }
            return new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    .format(new Date(startsAt));
        }
    }

    private static final class RowHolder {
        final ImageView map;
        final TextView title;
        final TextView datePlace;
        final TextView people;
        final TextView status;

        RowHolder(ImageView map, TextView title, TextView datePlace, TextView people,
                  TextView status) {
            this.map = map;
            this.title = title;
            this.datePlace = datePlace;
            this.people = people;
            this.status = status;
        }
    }
}
