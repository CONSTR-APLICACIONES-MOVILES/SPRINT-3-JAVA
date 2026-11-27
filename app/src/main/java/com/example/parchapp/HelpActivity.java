package com.example.parchapp;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.parchapp.data.HelpFaqEntity;
import com.example.parchapp.data.HelpRepository;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/* Help: contextual help that works without network. It opens from the ? button of the top bar and shows first the
 * questions of the screen the user came from. It reads Room (never the network), so it also works offline. */
public class HelpActivity extends AppCompatActivity {

    private static final String EXTRA_SCREEN = "help_screen";
    private static final long SEARCH_DELAY_MS = 250;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final HelpAdapter adapter = new HelpAdapter("ParchApp " + BuildConfig.VERSION_NAME
            + ". Help for your groups, schedule and plans, also without internet.");
    // eventual connectivity: banner while offline, and the help is read again when the network returns
    private final ConnectivityWatcher connectivity = new ConnectivityWatcher(this::load);
    private final Runnable searchLater = this::startSearch;

    private HelpRepository repository;
    private String screen;
    private EditText searchInput;
    private TextView updatedText;
    private TextView emptyText;
    private HelpScreenState lastState;

    // multithreading: futures. The one that loads the screen and the one of the last search, to cancel them
    private CompletableFuture<?> loading;
    private CompletableFuture<?> searching;

    // the screen name is what TopHeader sends: groups, schedule, dashboard...
    public static void open(Activity from, String screen) {
        from.startActivity(new Intent(from, HelpActivity.class).putExtra(EXTRA_SCREEN, screen));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        repository = HelpRepository.get(this);
        String requested = getIntent().getStringExtra(EXTRA_SCREEN);
        screen = requested == null ? HelpSections.GENERAL : requested;

        findViewById(R.id.help_back_button).setOnClickListener(v -> finish());
        updatedText = findViewById(R.id.help_updated);
        emptyText = findViewById(R.id.help_empty);
        RecyclerView list = findViewById(R.id.help_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        searchInput = findViewById(R.id.help_search_input);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            // micro-optimization: wait 250 ms after the last key before searching, so typing a word is one search
            @Override
            public void afterTextChanged(Editable s) {
                handler.removeCallbacks(searchLater);
                handler.postDelayed(searchLater, SEARCH_DELAY_MS);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        connectivity.start(findViewById(R.id.offline_banner));
        load();
    }

    @Override
    protected void onStop() {
        connectivity.stop();
        super.onStop();
    }

    // multithreading: futures (Multi-threading slides). Two independent reads at once, joined, then drawn on the main thread
    private void load() {
        if (loading != null) loading.cancel(true);
        Executor main = ContextCompat.getMainExecutor(this);
        CompletableFuture<List<HelpSection>> questions = CompletableFuture
                .supplyAsync(repository::questions, HelpRepository.IO)
                .thenApply(rows -> HelpSections.originFirst(rows, screen)); // grouping also off the main thread
        CompletableFuture<Long> lastSync = CompletableFuture.supplyAsync(repository::lastSync, HelpRepository.IO);
        CompletableFuture<Void> drawn = questions.thenCombine(lastSync, HelpScreenState::new)
                .thenAcceptAsync(this::render, main);
        drawn.exceptionally(this::showError); // antipattern #3: the screen says what failed
        loading = drawn;
    }

    private void startSearch() {
        String text = searchInput.getText().toString();
        if (searching != null) searching.cancel(true); // only the last search is drawn
        if (text.trim().isEmpty()) {
            if (lastState != null) render(lastState);
            return;
        }
        CompletableFuture<Void> drawn = CompletableFuture
                .supplyAsync(() -> repository.search(text), HelpRepository.IO)
                .thenAcceptAsync(this::showResults, ContextCompat.getMainExecutor(this));
        drawn.exceptionally(this::showError);
        searching = drawn;
    }

    private void render(HelpScreenState state) {
        if (isFinishing() || isDestroyed()) return;
        lastState = state;
        updatedText.setText(state.lastSync == 0L
                ? getString(R.string.help_built_in)
                : getString(R.string.help_updated_on, new SimpleDateFormat("MMM d, yyyy", Locale.US)
                        .format(new java.util.Date(state.lastSync))));
        // a search in progress keeps its results; the sections come back when the box is empty
        if (searchInput.getText().toString().trim().isEmpty()) {
            emptyText.setVisibility(View.GONE);
            adapter.setSections(state.sections);
        }
    }

    private void showResults(List<HelpFaqEntity> results) {
        if (isFinishing() || isDestroyed()) return;
        adapter.setResults(results);
        emptyText.setVisibility(results.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // a cancelled future is not an error: it only means a newer one took its place
    private Void showError(Throwable error) {
        if (error instanceof CancellationException || error.getCause() instanceof CancellationException) return null;
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                Toast.makeText(this, R.string.help_load_error, Toast.LENGTH_SHORT).show();
            }
        });
        return null;
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(searchLater);
        if (loading != null) loading.cancel(true); // nothing is drawn on a closed screen
        if (searching != null) searching.cancel(true);
        super.onDestroy();
    }
}
