package com.example.parchapp;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.parchapp.analytics.AnalyticsTracker;
import com.example.parchapp.data.AndesEventsRepository;
import com.example.parchapp.data.AndesEventsStore;
import com.example.parchapp.data.AndesOutbox;
import com.example.parchapp.data.OfflineScheduleRepository;
import com.example.parchapp.data.Repositories;
import com.example.parchapp.domain.AndesCacheWindow;
import com.example.parchapp.domain.model.AndesEvent;
import com.example.parchapp.util.Callback;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.functions.FirebaseFunctionsException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeoutException;

/* Andes Events, the same screen as the Flutter AndesEventsView of the sch-back branch: the published university
 * events, search and categories, saved events on the phone and "Add to Schedule".
 * Caching: the copy of the next 15 days (Room) is shown at once while the backend is asked for the full catalog;
 * the confirmed answer replaces it and becomes the new copy. Both requests run at the same time and a late or
 * old answer is dropped by its generation number, so it never replaces a newer one.
 * Eventual connectivity: offline the banner says it and shows when the copy was confirmed; search, categories,
 * details and bookmarks still work; "Add to Schedule" is saved in the outbox and sent when the connection is
 * back; removing needs the connection. An event shows as added only after the backend confirms it. */
public class AndesEventsActivity extends AppCompatActivity implements AndesOutbox.Listener {
    public static final String EXTRA_CHANGED = "andes_changed";
    private static final String[] CATEGORY_FILTERS = {"all", "academic", "cultural", "sports", "other"};
    private static final int BLUE = Color.parseColor("#0047BA");
    private static final int INK = Color.parseColor("#111827");
    private static final int SLATE = Color.parseColor("#64748B");
    private static final int RED = Color.parseColor("#EF4444");
    private static final TimeZone BOGOTA = TimeZone.getTimeZone("America/Bogota");

    public static Intent intent(Context context) {
        return new Intent(context, AndesEventsActivity.class);
    }

    private final AndesEventsRepository repository = Repositories.andesEvents();
    private final AndesEventsStore store = Repositories.andesStore();
    private final AndesOutbox outbox = Repositories.andesOutbox();
    private final AnalyticsTracker.ConnectivityListener connectivityListener =
            online -> runOnUiThread(() -> onConnectivityChanged(online));

    // the state of the Flutter AndesEventsViewModel
    private boolean networkAvailable;
    private boolean serverConfirmed;
    private boolean refreshing;
    private boolean refreshInFlight;
    private boolean reconnecting;
    private boolean loading = true;
    private boolean cacheFailure;
    @Nullable
    private String error;
    private long lastSync;
    private int invalidCount;
    private int generation;
    private List<AndesEvent> events = new ArrayList<>();
    private final Set<String> added = new HashSet<>();
    private final Set<String> busy = new HashSet<>();
    private List<AndesEvent> savedEvents = new ArrayList<>();
    private boolean savedLoading;
    @Nullable
    private String savedError;
    private final Set<String> saving = new HashSet<>();
    private boolean showSaved;
    private String query = "";
    private String category = "all";
    private boolean changed;

    private final EventsAdapter adapter = new EventsAdapter();
    private final SimpleDateFormat dateFormat = format("MMM d, yyyy");
    private final SimpleDateFormat timeFormat = format("h:mm a");
    @Nullable
    private Runnable refreshDetails;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (repository == null || store == null || outbox == null) {
            Toast.makeText(this, "Andes Events need the ParchApp backend.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setContentView(R.layout.activity_andes_events);
        BottomNav.setup(this, BottomNav.Tab.SCHEDULE);
        findViewById(R.id.andes_back).setOnClickListener(v -> finish());
        findViewById(R.id.andes_banner_retry).setOnClickListener(v -> refresh());
        findViewById(R.id.andes_failed).setOnClickListener(v -> openFailed());
        findViewById(R.id.andes_tab_all).setOnClickListener(v -> selectSaved(false));
        findViewById(R.id.andes_tab_saved).setOnClickListener(v -> selectSaved(true));
        ((TextView) findViewById(R.id.andes_search)).addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString();
                render();
            }
        });
        RecyclerView list = findViewById(R.id.andes_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        networkAvailable = AnalyticsTracker.getInstance().isOnline();
        render();
        load();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (outbox == null) {
            return;
        }
        outbox.addListener(this);
        AnalyticsTracker.getInstance().addConnectivityListener(connectivityListener);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        // back on screen: prune the copy again and ask the server; a confirmed list is never replaced by the copy
        if (networkAvailable) {
            refresh();
        }
        showCache();
    }

    @Override
    protected void onStop() {
        if (outbox != null) {
            outbox.removeListener(this);
            AnalyticsTracker.getInstance().removeConnectivityListener(connectivityListener);
        }
        super.onStop();
    }

    @Override
    public void finish() {
        // Schedule draws my day again when an event was added or removed
        setResult(RESULT_OK, new Intent().putExtra(EXTRA_CHANGED, changed));
        super.finish();
    }

    // ---------- Data ----------

    private void load() {
        outbox.refresh();
        if (networkAvailable) {
            outbox.sync();
            refresh();
        }
        showCache();
        loadSaved();
    }

    /* the copy on the phone; ignored once the server confirmed the catalog or when a newer request started */
    private void showCache() {
        int requested = generation;
        store.readCatalog(new AndesCacheWindow(System.currentTimeMillis()), new Callback<AndesEventsStore.CachedCatalog>() {
            @Override
            public void onSuccess(AndesEventsStore.CachedCatalog cached) {
                if (isFinishing() || requested != generation || serverConfirmed) {
                    return;
                }
                events = cached.events;
                lastSync = cached.lastSync;
                cacheFailure = false;
                loading = false;
                render();
            }

            @Override
            public void onError(Exception failure) {
                if (isFinishing() || requested != generation || serverConfirmed) {
                    return;
                }
                events = new ArrayList<>();
                cacheFailure = true;
                loading = false;
                render();
            }
        });
    }

    /* the full catalog from the backend; one request at a time, a request made meanwhile reuses it */
    private void refresh() {
        if (refreshInFlight) {
            return;
        }
        refreshInFlight = true;
        int requested = ++generation;
        refreshing = true;
        error = null;
        render();
        repository.load(new Callback<AndesEventsRepository.Catalog>() {
            @Override
            public void onSuccess(AndesEventsRepository.Catalog catalog) {
                refreshInFlight = false;
                if (isFinishing()) {
                    return;
                }
                if (requested != generation || !networkAvailable) {
                    refreshAgainIfNeeded();
                    return;
                }
                long now = System.currentTimeMillis();
                List<AndesEvent> upcoming = new ArrayList<>();
                for (AndesEvent event : catalog.events) {
                    if (!event.hasEnded(now)) {
                        upcoming.add(event);
                    }
                }
                Collections.sort(upcoming, (a, b) -> Long.compare(a.getStart(), b.getStart()));
                events = upcoming;
                invalidCount = catalog.invalidCount;
                if (busy.isEmpty()) {
                    added.clear();
                    added.addAll(catalog.addedIds);
                }
                serverConfirmed = true;
                loading = false;
                reconnecting = false;
                refreshing = false;
                error = null;
                render();
                outbox.sync();
                // the confirmed catalog becomes the copy of the next 15 days
                store.replaceCatalog(upcoming, new AndesCacheWindow(now), new Callback<Long>() {
                    @Override
                    public void onSuccess(Long syncedAt) {
                        if (requested == generation) {
                            lastSync = syncedAt;
                            cacheFailure = false;
                        }
                    }

                    @Override
                    public void onError(Exception failure) {
                        if (requested == generation) {
                            cacheFailure = true;
                        }
                    }
                });
            }

            @Override
            public void onError(Exception failure) {
                refreshInFlight = false;
                if (isFinishing()) {
                    return;
                }
                if (requested != generation) {
                    refreshAgainIfNeeded();
                    return;
                }
                serverConfirmed = false;
                reconnecting = false;
                refreshing = false;
                loading = false;
                error = message(failure);
                showCache();
                render();
            }
        });
    }

    // an answer that arrived after the connection changed is dropped; if we are online and still not confirmed,
    // the catalog is asked again
    private void refreshAgainIfNeeded() {
        if (networkAvailable && !serverConfirmed) {
            refresh();
        } else {
            refreshing = false;
            render();
        }
    }

    private void onConnectivityChanged(boolean online) {
        if (isFinishing()) {
            return;
        }
        boolean previous = networkAvailable;
        networkAvailable = online;
        if (!online && previous) {
            generation++;
            refreshing = false;
            serverConfirmed = false;
            reconnecting = false;
            events = new ArrayList<>();
            showCache();
        } else if (online && !previous) {
            reconnecting = true;
            refresh();
            outbox.sync();
        }
        render();
    }

    private void loadSaved() {
        String owner = owner();
        if (owner == null) {
            savedError = "Sign in to access saved events.";
            render();
            return;
        }
        savedLoading = true;
        savedError = null;
        render();
        store.saved(owner, new Callback<List<AndesEvent>>() {
            @Override
            public void onSuccess(List<AndesEvent> result) {
                savedEvents = result;
                savedLoading = false;
                render();
            }

            @Override
            public void onError(Exception failure) {
                savedError = "Could not load saved events. Please try again.";
                savedLoading = false;
                render();
            }
        });
    }

    // ---------- Actions ----------

    private boolean isSaved(String id) {
        for (AndesEvent event : savedEvents) {
            if (event.getId().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAdded(AndesEvent event) {
        AndesOutbox.Operation operation = outbox.operation(event.getId());
        return added.contains(event.getId()) || operation != null && AndesOutbox.CONFIRMED.equals(operation.status);
    }

    private void toggleSaved(AndesEvent event) {
        String owner = owner();
        if (owner == null) {
            snackbar("Sign in to save events.", null);
            return;
        }
        if (saving.contains(event.getId())) {
            return;
        }
        saving.add(event.getId());
        render();
        store.setSaved(owner, event, !isSaved(event.getId()), new Callback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                saving.remove(event.getId());
                loadSaved();
            }

            @Override
            public void onError(Exception failure) {
                saving.remove(event.getId());
                render();
                snackbar("Could not update saved events. Please try again.", null);
            }
        });
    }

    /* saved first in the outbox; sent now when the catalog is confirmed online, otherwise when the network returns */
    private void add(AndesEvent event) {
        if (busy.contains(event.getId()) || isAdded(event)) {
            return;
        }
        outbox.enqueue(event, failure -> {
            if (failure != null) {
                snackbar(failure, () -> add(event));
            } else if (serverConfirmed && networkAvailable) {
                outbox.sync();
            }
        });
    }

    private void remove(AndesEvent event) {
        if (!serverConfirmed || !networkAvailable) {
            snackbar("An internet connection is required to remove an event from Schedule.", null);
            return;
        }
        if (busy.contains(event.getId()) || !isAdded(event)) {
            return;
        }
        busy.add(event.getId());
        render();
        repository.remove(event.getId(), new Callback<Void>() {
            @Override
            public void onSuccess(Void ignored) {
                busy.remove(event.getId());
                added.remove(event.getId());
                outbox.forget(event.getId());
                forgetScheduleDays();
                changed = true;
                render();
            }

            @Override
            public void onError(Exception failure) {
                busy.remove(event.getId());
                render();
                snackbar(message(failure), () -> remove(event));
            }
        });
    }

    private void retry(AndesEvent shownNow) {
        // the card the user sees now is a fresh choice: it replaces the event saved in the failed request
        outbox.retry(shownNow, failure -> {
            if (failure != null) {
                snackbar(failure, null);
            }
        });
    }

    @Override
    public void onOperationsChanged() {
        if (isFinishing()) {
            return;
        }
        String owner = owner();
        if (owner != null) {
            for (AndesOutbox.Operation operation : collectConfirmed()) {
                if (added.add(operation.event.getId())) {
                    changed = true;
                }
            }
        }
        render();
    }

    private List<AndesOutbox.Operation> collectConfirmed() {
        List<AndesOutbox.Operation> confirmed = new ArrayList<>();
        for (AndesEvent event : events) {
            AndesOutbox.Operation operation = outbox.operation(event.getId());
            if (operation != null && AndesOutbox.CONFIRMED.equals(operation.status)) {
                confirmed.add(operation);
            }
        }
        return confirmed;
    }

    private void forgetScheduleDays() {
        if (Repositories.schedules() instanceof OfflineScheduleRepository) {
            ((OfflineScheduleRepository) Repositories.schedules()).forgetDaysInMemory();
        }
    }

    private void selectSaved(boolean value) {
        showSaved = value;
        render();
    }

    // ---------- Render ----------

    private void render() {
        if (isFinishing() || findViewById(R.id.andes_list) == null) {
            return;
        }
        renderBanner();
        renderFailed();
        renderTabs();
        renderCategories();
        TextView heading = findViewById(R.id.andes_heading);
        heading.setVisibility(showSaved ? View.GONE : View.VISIBLE);
        heading.setText(serverConfirmed ? "All upcoming events"
                : reconnecting ? "Cached events · Updating" : "Cached events · Next 15 days");
        renderContent();
        if (refreshDetails != null) {
            refreshDetails.run();
        }
    }

    /* the Flutter _statusBanner: nothing once the server confirmed the catalog online */
    private void renderBanner() {
        View banner = findViewById(R.id.andes_banner);
        boolean offline = !networkAvailable;
        if ((serverConfirmed && !offline) || (loading && error == null && !offline && !reconnecting)) {
            banner.setVisibility(View.GONE);
            return;
        }
        boolean reconnectingNow = reconnecting && refreshing;
        boolean accessError = error != null && (error.contains("Access denied") || error.contains("sign in"));
        String title = reconnectingNow ? "Connection restored" : offline ? "You're offline"
                : accessError ? "Access to events unavailable" : "Couldn't update events";
        String text = reconnectingNow ? "Updating the full event catalog…"
                : offline ? "Showing saved event information for the next 15 days. Some details may have changed."
                : accessError ? error : "Showing previously saved information. Try again when the service is available.";
        banner.setVisibility(View.VISIBLE);
        ((ImageView) findViewById(R.id.andes_banner_icon)).setImageResource(
                offline ? R.drawable.ic_wifi_off : R.drawable.ic_cloud_off);
        ((TextView) findViewById(R.id.andes_banner_title)).setText(title);
        ((TextView) findViewById(R.id.andes_banner_text)).setText(text);
        TextView sync = findViewById(R.id.andes_banner_sync);
        sync.setVisibility(lastSync > 0 ? View.VISIBLE : View.GONE);
        sync.setText(lastSync > 0 ? "Last synced: " + dateFormat.format(lastSync) + " " + timeFormat.format(lastSync) : "");
        findViewById(R.id.andes_banner_retry).setVisibility(!offline && !reconnectingNow ? View.VISIBLE : View.GONE);
    }

    private void renderFailed() {
        TextView failed = findViewById(R.id.andes_failed);
        int count = outbox.failed().size();
        failed.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
        failed.setText("Couldn't add " + count + " event" + (count == 1 ? "" : "s") + " · View");
    }

    private void renderTabs() {
        TextView all = findViewById(R.id.andes_tab_all);
        TextView saved = findViewById(R.id.andes_tab_saved);
        saved.setText("Saved (" + savedEvents.size() + ")");
        all.setBackgroundResource(showSaved ? R.drawable.bg_andes_tab_off : R.drawable.bg_andes_tab_on);
        saved.setBackgroundResource(showSaved ? R.drawable.bg_andes_tab_on : R.drawable.bg_andes_tab_off);
        all.setTextColor(showSaved ? SLATE : BLUE);
        saved.setTextColor(showSaved ? BLUE : SLATE);
    }

    private void renderCategories() {
        LinearLayout row = findViewById(R.id.andes_categories);
        if (row.getChildCount() == 0) {
            for (String value : CATEGORY_FILTERS) {
                TextView chip = new TextView(this);
                chip.setText(value.substring(0, 1).toUpperCase(Locale.US) + value.substring(1));
                chip.setTextSize(13);
                chip.setTypeface(Typeface.DEFAULT_BOLD);
                chip.setGravity(Gravity.CENTER);
                chip.setPadding(dp(14), 0, dp(14), 0);
                chip.setTag(value);
                chip.setOnClickListener(v -> {
                    category = value;
                    render();
                });
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
                params.setMarginEnd(dp(8));
                row.addView(chip, params);
            }
        }
        for (int i = 0; i < row.getChildCount(); i++) {
            TextView chip = (TextView) row.getChildAt(i);
            boolean on = category.equals(chip.getTag());
            chip.setBackgroundResource(on ? R.drawable.bg_andes_tab_on : R.drawable.bg_andes_tab_off);
            chip.setTextColor(on ? BLUE : SLATE);
        }
    }

    private List<AndesEvent> filtered() {
        List<AndesEvent> result = new ArrayList<>();
        for (AndesEvent event : showSaved ? savedEvents : events) {
            if (("all".equals(category) || category.equals(event.getCategory())) && event.matches(query)) {
                result.add(event);
            }
        }
        Collections.sort(result, (a, b) -> Long.compare(a.getStart(), b.getStart()));
        return result;
    }

    private void renderContent() {
        if (showSaved) {
            if (savedLoading) {
                showMessage(null, true, null);
                return;
            }
            if (savedError != null) {
                showMessage(savedError, false, "Sign in to access saved events.".equals(savedError) ? null : this::loadSaved);
                return;
            }
            if (savedEvents.isEmpty()) {
                showMessage("No saved events yet. Bookmark an event to keep it on this device.", false, null);
                return;
            }
        } else {
            if (loading) {
                showMessage("Loading events…", true, null);
                return;
            }
            if (events.isEmpty()) {
                showMessage(!serverConfirmed
                        ? "No offline events available\nConnect to the internet to load university events."
                        : invalidCount > 0 ? "Invalid event data. No valid upcoming events are available."
                        : "No published upcoming events.", false, serverConfirmed ? this::load : null);
                return;
            }
        }
        List<AndesEvent> shown = filtered();
        if (shown.isEmpty()) {
            showMessage("No matching search results.", false, null);
            return;
        }
        findViewById(R.id.andes_message_box).setVisibility(View.GONE);
        findViewById(R.id.andes_list).setVisibility(View.VISIBLE);
        String note = showSaved ? "Saved on this device. Details may not be up to date."
                : invalidCount > 0 ? "Some events have invalid data and could not be displayed." : null;
        adapter.show(note, shown);
    }

    private void showMessage(@Nullable String text, boolean progress, @Nullable Runnable retry) {
        findViewById(R.id.andes_list).setVisibility(View.GONE);
        findViewById(R.id.andes_message_box).setVisibility(View.VISIBLE);
        findViewById(R.id.andes_progress).setVisibility(progress ? View.VISIBLE : View.GONE);
        TextView message = findViewById(R.id.andes_message);
        message.setVisibility(text == null ? View.GONE : View.VISIBLE);
        message.setText(text);
        View retryButton = findViewById(R.id.andes_message_retry);
        retryButton.setVisibility(retry == null ? View.GONE : View.VISIBLE);
        retryButton.setOnClickListener(retry == null ? null : v -> retry.run());
    }

    private void bindCard(View card, AndesEvent event) {
        ((TextView) card.findViewById(R.id.andes_item_category)).setText(event.categoryLabel());
        ((TextView) card.findViewById(R.id.andes_item_title)).setText(event.getTitle());
        ((TextView) card.findViewById(R.id.andes_item_when)).setText(date(event) + " • " + times(event));
        ((TextView) card.findViewById(R.id.andes_item_where)).setText(event.getLocation());
        ((TextView) card.findViewById(R.id.andes_item_description)).setText(event.getDescription());
        ImageView bookmark = card.findViewById(R.id.andes_item_bookmark);
        boolean saved = isSaved(event.getId());
        bookmark.setImageResource(saved ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border);
        bookmark.setContentDescription(saved ? "Remove from Saved" : "Save event");
        boolean canSave = !saving.contains(event.getId()) && !savedLoading
                && (savedError == null || "Sign in to access saved events.".equals(savedError));
        bookmark.setEnabled(canSave);
        bookmark.setAlpha(canSave ? 1f : 0.4f);
        bookmark.setOnClickListener(v -> toggleSaved(event));
        LinearLayout action = card.findViewById(R.id.andes_item_action);
        action.removeAllViews();
        action.addView(actionView(event));
        card.setOnClickListener(v -> openDetails(event));
    }

    /* the Flutter button(): pending or syncing, failed with its reason and Retry, or Add / Remove */
    private View actionView(AndesEvent event) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        AndesOutbox.Operation operation = outbox.operation(event.getId());
        String status = operation == null ? null : operation.status;
        if (AndesOutbox.PENDING.equals(status) || AndesOutbox.SYNCING.equals(status)) {
            column.addView(button(AndesOutbox.SYNCING.equals(status) ? "Syncing…" : "Pending sync", false, false, null));
            column.addView(note("This event will be added when your connection is restored.", SLATE));
            return column;
        }
        if (AndesOutbox.FAILED.equals(status)) {
            TextView title = note("Couldn't add event", INK);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            column.addView(title);
            String reason = operation.error == null ? "Please try again." : operation.error;
            column.addView(note(reason, SLATE));
            boolean final_ = reason.contains("already ended") || reason.contains("no longer available");
            boolean changedEvent = reason.contains("changed");
            if (!final_ && (!changedEvent || serverConfirmed)) {
                TextView retry = note("Retry", BLUE);
                retry.setTypeface(Typeface.DEFAULT_BOLD);
                retry.setPadding(0, dp(10), dp(16), dp(10));
                retry.setOnClickListener(v -> retry(event));
                column.addView(retry);
            }
            return column;
        }
        boolean isAdded = isAdded(event);
        boolean working = busy.contains(event.getId());
        String label = working ? (isAdded ? "Removing…" : "Adding…") : isAdded ? "Remove from Schedule" : "Add to Schedule";
        column.addView(button(label, !isAdded, !working, v -> {
            if (isAdded) {
                remove(event);
            } else {
                add(event);
            }
        }));
        return column;
    }

    private TextView button(String label, boolean filled, boolean enabled, @Nullable View.OnClickListener click) {
        TextView button = new TextView(this);
        button.setText(label);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(20), 0, dp(20), 0);
        button.setBackgroundResource(filled ? R.drawable.bg_andes_btn : R.drawable.bg_andes_btn_outline);
        button.setTextColor(filled ? Color.WHITE : enabled ? BLUE : SLATE);
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.7f);
        button.setOnClickListener(click);
        button.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));
        return button;
    }

    private TextView note(String text, int color) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(13);
        view.setTextColor(color);
        view.setPadding(0, dp(4), 0, 0);
        return view;
    }

    private void openDetails(AndesEvent event) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(24), dp(24), dp(24));
        Runnable fill = () -> {
            root.removeAllViews();
            TextView kind = note(event.categoryLabel(), BLUE);
            root.addView(kind);
            TextView title = note(event.getTitle(), INK);
            title.setTextSize(22);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setPadding(0, dp(8), 0, dp(16));
            root.addView(title);
            if (!serverConfirmed || showSaved) {
                root.addView(note("Saved event information may have changed.", SLATE));
            }
            root.addView(note(event.getDescription(), INK));
            TextView when = note(date(event), INK);
            when.setPadding(0, dp(16), 0, 0);
            root.addView(when);
            root.addView(note(times(event), INK));
            root.addView(note(event.getLocation(), INK));
            View action = actionView(event);
            action.setPadding(0, dp(20), 0, 0);
            root.addView(action);
        };
        fill.run();
        androidx.core.widget.NestedScrollView scroll = new androidx.core.widget.NestedScrollView(this);
        scroll.addView(root);
        sheet.setContentView(scroll);
        refreshDetails = fill;
        sheet.setOnDismissListener(d -> refreshDetails = null);
        sheet.show();
    }

    private void openFailed() {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(16), dp(24), dp(24));
        for (AndesOutbox.Operation operation : outbox.failed()) {
            TextView title = note(operation.event.getTitle(), INK);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setPadding(0, dp(12), 0, 0);
            root.addView(title);
            root.addView(note(operation.error == null ? "Couldn't add event." : operation.error, RED));
        }
        sheet.setContentView(root);
        sheet.show();
    }

    // ---------- Helpers ----------

    /* the Flutter message(): what failed, in words for the banner and the snackbars */
    static String message(Exception failure) {
        if (failure instanceof TimeoutException || failure.getCause() instanceof TimeoutException) {
            return "The event service timed out. Please try again.";
        }
        if (failure instanceof FirebaseFunctionsException) {
            FirebaseFunctionsException.Code code = ((FirebaseFunctionsException) failure).getCode();
            if (code == FirebaseFunctionsException.Code.UNAUTHENTICATED) {
                return "Please sign in again to access events.";
            }
            if (code == FirebaseFunctionsException.Code.PERMISSION_DENIED) {
                return "Access denied. Please check your account permissions.";
            }
            if (code == FirebaseFunctionsException.Code.UNAVAILABLE
                    || code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED) {
                return "Connection unavailable. Please try again.";
            }
            if (code == FirebaseFunctionsException.Code.FAILED_PRECONDITION
                    || code == FirebaseFunctionsException.Code.NOT_FOUND) {
                return failure.getMessage();
            }
        }
        if (OfflineGuard.isNetworkError(failure)) {
            return "Connection unavailable. Please try again.";
        }
        return "Could not load or save events. Please try again.";
    }

    @Nullable
    private static String owner() {
        UserProfile user = UserSession.getCurrentUser();
        return user == null ? null : user.getId();
    }

    private String date(AndesEvent event) {
        return dateFormat.format(event.getStart());
    }

    private String times(AndesEvent event) {
        return timeFormat.format(event.getStart()) + " – " + timeFormat.format(event.getEnd());
    }

    private static SimpleDateFormat format(String pattern) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
        format.setTimeZone(BOGOTA);
        return format;
    }

    private void snackbar(String message, @Nullable Runnable retry) {
        Snackbar bar = Snackbar.make(findViewById(R.id.andes_list), message, Snackbar.LENGTH_LONG);
        if (retry != null) {
            bar.setAction("Retry", v -> retry.run());
        }
        bar.show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /* the cards; a RecyclerView reuses the card views while scrolling instead of creating one per event */
    private final class EventsAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int NOTE = 0;
        private static final int CARD = 1;
        @Nullable
        private String note;
        private List<AndesEvent> items = new ArrayList<>();

        void show(@Nullable String note, List<AndesEvent> items) {
            this.note = note;
            this.items = items;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return note != null && position == 0 ? NOTE : CARD;
        }

        @Override
        public int getItemCount() {
            return items.size() + (note == null ? 0 : 1);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = viewType == NOTE ? note("", SLATE)
                    : LayoutInflater.from(parent.getContext()).inflate(R.layout.item_andes_event, parent, false);
            if (viewType == NOTE) {
                view.setPadding(0, 0, 0, dp(8));
            }
            return new RecyclerView.ViewHolder(view) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (getItemViewType(position) == NOTE) {
                ((TextView) holder.itemView).setText(note);
                return;
            }
            bindCard(holder.itemView, items.get(position - (note == null ? 0 : 1)));
        }
    }
}
