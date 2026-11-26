package com.example.parchapp.data;

import androidx.annotation.Nullable;

import com.example.parchapp.domain.model.AndesEvent;
import com.example.parchapp.util.Callback;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableReference;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/* Calls getAndesEvents, addAndesEventToSchedule and removeAndesEventFromSchedule. Like Flutter, a request
 * that does not answer in 15 seconds fails, so the screen falls back to the copy on the phone. */
class FunctionsAndesEventsRepository implements AndesEventsRepository {
    static final long TIMEOUT_SECONDS = 15;
    private final FirebaseFunctions functions;

    FunctionsAndesEventsRepository(FirebaseFunctions functions) {
        this.functions = functions;
    }

    private HttpsCallableReference callable(String name) {
        HttpsCallableReference reference = functions.getHttpsCallable(name);
        reference.setTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return reference;
    }

    @Override
    public void load(Callback<Catalog> callback) {
        callable("getAndesEvents").call(new HashMap<String, Object>())
                .addOnSuccessListener(result -> {
                    Map<String, Object> data = asMap(result.getData());
                    callback.onSuccess(parseCatalog(data));
                })
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void remove(String eventId, Callback<Void> callback) {
        Map<String, Object> request = new HashMap<>();
        request.put("eventId", eventId);
        callable("removeAndesEventFromSchedule").call(request)
                .addOnSuccessListener(result -> callback.onSuccess(null))
                .addOnFailureListener(callback::onError);
    }

    @Override
    public void addBlocking(String ownerId, AndesEvent shown) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("eventId", shown.getId());
        // what the card showed: the backend refuses the add when the time or the place changed since
        request.put("start", iso(shown.getStart()));
        request.put("end", iso(shown.getEnd()));
        request.put("location", shown.getLocation());
        // the account that asked: a request saved offline is never sent for another account
        request.put("clientUserId", ownerId);
        Tasks.await(callable("addAndesEventToSchedule").call(request), TIMEOUT_SECONDS + 5, TimeUnit.SECONDS);
    }

    static Catalog parseCatalog(Map<String, Object> data) {
        List<AndesEvent> events = new ArrayList<>();
        int invalid = asInt(data.get("invalidCount"));
        for (Map<String, Object> raw : listOfMaps(data.get("events"))) {
            AndesEvent event = parseEvent(raw);
            if (event == null) {
                invalid++;
            } else {
                events.add(event);
            }
        }
        Set<String> added = new HashSet<>();
        Object ids = data.get("addedIds");
        if (ids instanceof List) {
            for (Object id : (List<?>) ids) {
                if (id instanceof String) {
                    added.add((String) id);
                }
            }
        }
        return new Catalog(events, invalid, added);
    }

    @Nullable
    static AndesEvent parseEvent(Map<String, Object> raw) {
        Long start = instant(raw.get("start"));
        Long end = instant(raw.get("end"));
        if (start == null || end == null) {
            return null;
        }
        Object url = raw.get("sourceUrl");
        AndesEvent event = new AndesEvent(asString(raw.get("id")), asString(raw.get("title")),
                asString(raw.get("description")), asString(raw.get("category")), asString(raw.get("location")),
                start, end, url instanceof String ? (String) url : null);
        return event.isValid() ? event : null;
    }

    // the backend sends Date.toISOString(): "2026-11-25T19:00:00.000Z"
    private static SimpleDateFormat isoFormat() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        format.setLenient(false);
        return format;
    }

    @Nullable
    static Long instant(Object value) {
        if (!(value instanceof String)) {
            return null;
        }
        try {
            return isoFormat().parse((String) value).getTime();
        } catch (ParseException | NullPointerException invalid) {
            return null;
        }
    }

    static String iso(long millis) {
        return isoFormat().format(new java.util.Date(millis));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOfMaps(Object value) {
        List<Map<String, Object>> maps = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<Object>) value) {
                if (item instanceof Map) {
                    maps.add((Map<String, Object>) item);
                }
            }
        }
        return maps;
    }

    private static int asInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static String asString(Object value) {
        return value instanceof String ? ((String) value).trim() : "";
    }
}
