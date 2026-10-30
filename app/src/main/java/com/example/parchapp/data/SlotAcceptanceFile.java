package com.example.parchapp.data;

import androidx.annotation.Nullable;

import com.example.parchapp.domain.model.SlotAcceptanceInsight;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

// strategy 7, local storage: the last bq3 result in a json file of the app, it survives closing the app
// firestore can delete its own copy when its cache needs space, this file stays until the next result
// it is one small document that is always read whole, so it does not need a table
// the result is the same for everyone, so it is not per account
final class SlotAcceptanceFile {
    private final File file;

    SlotAcceptanceFile(File file) {
        this.file = file;
    }

// call it from a worker thread, it first writes a temporary file so a closed app never leaves half a result
    void save(SlotAcceptanceInsight insight) throws IOException {
        File temporary = new File(file.getPath() + ".tmp");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(temporary), StandardCharsets.UTF_8)) {
            writer.write(toJson(insight));
        }
        // on Android rename replaces the old file in one step; on Windows (unit tests on a PC) it does not,
        // so the old file is removed and the rename is tried once more
        if (!temporary.renameTo(file) && !(file.delete() && temporary.renameTo(file))) {
            throw new IOException("Could not replace " + file.getName());
        }
    }

// call it from a worker thread, the answer is null when there is no file or it can not be read
    @Nullable
    SlotAcceptanceInsight read() {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line);
            }
            JSONObject json = new JSONObject(text.toString());
            return new SlotAcceptanceInsight(json.getInt("position"),
                    json.has("percent") ? Double.valueOf(json.getDouble("percent")) : null,
                    json.getInt("sample"));
        } catch (IOException | JSONException error) {
            return null;
        }
    }

    private static String toJson(SlotAcceptanceInsight insight) throws IOException {
        try {
            JSONObject json = new JSONObject();
            json.put("position", insight.getRecommendedPosition());
            json.put("sample", insight.getSampleSize());
            if (insight.getPercentUnchanged() != null) {
                json.put("percent", insight.getPercentUnchanged().doubleValue());
            }
            return json.toString();
        } catch (JSONException error) {
            throw new IOException("Could not write the bq3 result", error);
        }
    }
}
