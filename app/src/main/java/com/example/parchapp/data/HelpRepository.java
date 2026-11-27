package com.example.parchapp.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.parchapp.R;
import com.example.parchapp.util.BackgroundThreads;

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

// eventual connectivity: the help screen never waits for the network, it always reads Room.
// cache, falling back to network + generic fallback: an empty table is filled with the copy that comes inside the app
public final class HelpRepository {
    // multithreading: one pool for the help, in the profiler its threads are parch-help-1 and parch-help-2
    public static final Executor IO = BackgroundThreads.pool("parch-help", 2);

    private static final String PREFS = "parchapp_help";
    private static final String KEY_LAST_SYNC = "last_sync";
    private static volatile HelpRepository instance;

    private final Context context;
    private final HelpDao dao;
    private final SharedPreferences prefs;

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

    // blocking, same as questions(); an empty search shows every question
    public List<HelpFaqEntity> search(String text) {
        String match = HelpSearchQuery.toMatch(text);
        if (match.isEmpty()) return questions();
        return dao.search(match);
    }

    // 0 means "never downloaded": the screen shows the copy that comes inside the app
    public long lastSync() {
        return prefs.getLong(KEY_LAST_SYNC, 0L);
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
