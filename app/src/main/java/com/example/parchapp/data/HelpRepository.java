package com.example.parchapp.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.ArrayMap;
import android.util.Log;

import com.example.parchapp.R;
import com.example.parchapp.util.BackgroundThreads;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// eventual connectivity: the help screen never waits for the network, it always reads Room.
// cache, falling back to network + generic fallback: an empty table is filled with the copy that comes inside the app
public final class HelpRepository {
    // multithreading: one pool for the help, in the profiler its threads are parch-help-1 and parch-help-2
    public static final Executor IO = BackgroundThreads.pool("parch-help", 2);

    private static final String PREFS = "parchapp_help";
    private static final String KEY_LAST_SYNC = "last_sync";
    private static final String KEY_LAST_ATTEMPT = "last_attempt";
    private static final String TAG = "HelpRepository";
    // onCapabilitiesChanged fires many times and a failed download does not move lastSync, so a try is made at most every 30 min
    private static final long RETRY_WAIT = TimeUnit.MINUTES.toMillis(30);
    // caching: the last searches in an ArrayMap, memory friendly for small collections (Caching DS)
    private static final int MAX_SEARCHES = 20;
    private static volatile HelpRepository instance;

    private final Context context;
    private final HelpDao dao;
    private final SharedPreferences prefs;
    private final ArrayMap<String, List<HelpFaqEntity>> searches = new ArrayMap<>();
    private final AtomicBoolean refreshing = new AtomicBoolean(false);

    private HelpRepository(Context context) {
        this.context = context.getApplicationContext();
        this.dao = HelpDatabase.get(this.context).help();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static HelpRepository get(Context context) {
        if (instance == null) {
            synchronized (HelpRepository.class) {
                if (instance == null) {
                    instance = new HelpRepository(context);
                }
            }
        }
        return instance;
    }

    // blocking: call it from the help pool, never from the main thread
    public List<HelpFaqEntity> questions() {
        if (dao.count() == 0) dao.seedIfEmpty(readSeed());
        return dao.all();
    }

    // blocking, same as questions(); an empty search shows every question.
    // a repeated search is answered from memory and does not touch Room
    // the methods are synchronized because ArrayMap is not safe between threads and the help pool has two
    public synchronized List<HelpFaqEntity> search(String text) {
        String key = HelpSearchQuery.normalize(text);
        String match = HelpSearchQuery.toMatch(key);
        if (match.isEmpty()) return questions();
        List<HelpFaqEntity> saved = searches.get(key);
        if (saved != null) {
            Log.d(TAG, "search from memory: " + key);
            return saved;
        }
        List<HelpFaqEntity> rows = dao.search(match);
        if (searches.size() >= MAX_SEARCHES) searches.removeAt(0); // keeps the map small (it is not always the oldest)
        searches.put(key, rows);
        return rows;
    }

    // the saved searches belonged to the old questions
    public synchronized void clearSearches() {
        searches.clear();
    }

    // 0 means "never downloaded": the screen shows the copy that comes inside the app
    public long lastSync() {
        return prefs.getLong(KEY_LAST_SYNC, 0L);
    }

    public long lastAttempt() {
        return prefs.getLong(KEY_LAST_ATTEMPT, 0L);
    }

    // eventual connectivity: runs on the help pool; only one download at a time, and the screen never waits for it.
    // if it fails, the saved copy stays and the user sees nothing
    public void refreshFromNetwork() {
        long now = System.currentTimeMillis();
        if (now - lastAttempt() < RETRY_WAIT) return;
        if (!refreshing.compareAndSet(false, true)) return;
        prefs.edit().putLong(KEY_LAST_ATTEMPT, now).apply();
        IO.execute(() -> {
            try {
                if (FirebaseApp.getApps(context).isEmpty()) return; // no backend in this build
                QuerySnapshot snapshot = Tasks.await(FirebaseFirestore.getInstance().collection("helpFaq")
                        .whereEqualTo("active", true).get(Source.SERVER), 8, TimeUnit.SECONDS);
                List<HelpFaqEntity> rows = toRows(snapshot);
                if (rows.isEmpty()) return; // an empty answer never replaces the saved questions
                dao.replaceAll(rows);
                clearSearches();
                prefs.edit().putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply();
                Log.d(TAG, "help refreshed on Wi-Fi, " + rows.size() + " questions");
            } catch (Exception error) {
                Log.d(TAG, "help refresh skipped: " + error.getMessage()); // the saved copy stays
            } finally {
                refreshing.set(false);
            }
        });
    }

    private static List<HelpFaqEntity> toRows(QuerySnapshot snapshot) {
        List<HelpFaqEntity> rows = new ArrayList<>();
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            String screen = doc.getString("screen");
            String question = doc.getString("question");
            String answer = doc.getString("answer");
            if (screen == null || question == null || answer == null) continue; // a broken document is skipped
            HelpFaqEntity row = new HelpFaqEntity();
            row.questionId = doc.getId();
            row.screen = screen;
            row.question = question;
            row.answer = answer;
            Long position = doc.getLong("position");
            row.position = position == null ? 0 : position.intValue();
            rows.add(row);
        }
        return rows;
    }

    private List<HelpFaqEntity> readSeed() {
        try (InputStream in = context.getResources().openRawResource(R.raw.help_faq)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return parse(out.toString(StandardCharsets.UTF_8.name()));
        } catch (IOException | JSONException error) {
            return Collections.emptyList();
        }
    }

    static List<HelpFaqEntity> parse(String json) throws JSONException {
        JSONArray array = new JSONArray(json);
        List<HelpFaqEntity> rows = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.getJSONObject(i);
            HelpFaqEntity row = new HelpFaqEntity();
            row.questionId = item.getString("id");
            row.screen = item.getString("screen");
            row.question = item.getString("question");
            row.answer = item.getString("answer");
            row.position = item.optInt("position", i);
            rows.add(row);
        }
        return rows;
    }
}
