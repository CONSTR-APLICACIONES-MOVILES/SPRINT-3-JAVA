package com.example.parchapp.data;

import android.content.Context;

import androidx.annotation.Nullable;

import com.example.parchapp.UserProfile;
import com.example.parchapp.UserSession;
import com.example.parchapp.analytics.AnalyticsTracker;
import com.example.parchapp.domain.model.ActivityDraft;
import com.example.parchapp.domain.model.PlannedActivity;
import com.example.parchapp.domain.model.SavedActivityDraft;
import com.example.parchapp.util.BackgroundThreads;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.FirebaseFunctionsException;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/** Room-backed drafts with the private cloud sync used by the Flutter app. */
public final class RoomActivityDraftsRepository implements ActivityDraftsRepository {
    private final ActivityDraftsDao dao;
    private final FirebaseFunctions functions;
    private final ExecutorService worker = BackgroundThreads.serial("parch-drafts");
    private static final Callback<Void> NO_OP = new Callback<Void>() {
        @Override public void onSuccess(Void ignored) { }
        @Override public void onError(Exception error) { }
    };
    private boolean syncing;
    private boolean syncAgain;

    public RoomActivityDraftsRepository(Context context, @Nullable FirebaseFunctions functions) {
        dao = ParchDatabase.get(context.getApplicationContext()).activityDrafts();
        this.functions = functions;
    }

    @Override
    public void load(Callback<List<SavedActivityDraft>> callback) {
        String owner = currentUserId();
        if (owner == null) {
            callback.onError(new IllegalStateException("Sign in to open your drafts."));
            return;
        }
        worker.execute(() -> {
            List<SavedActivityDraft> result = new ArrayList<>();
            for (ActivityDraftEntity entity : dao.visible(owner)) {
                result.add(toSaved(entity));
            }
            MainThread.post(() -> callback.onSuccess(result));
        });
    }

    @Override
    public void save(ActivityDraft draft, @Nullable SavedActivityDraft previous,
                     Callback<SavedActivityDraft> callback) {
        String owner = currentUserId();
        if (owner == null || draft == null) {
            callback.onError(new IllegalStateException("Sign in to save a draft."));
            return;
        }
        worker.execute(() -> {
            String id = previous == null ? randomId() : previous.getId();
            long revision = previous == null ? 1 : previous.getRevision() + 1;
            long remoteVersion = previous == null ? 0 : previous.getRemoteVersion();
            String payload;
            try {
                payload = draft.toJson();
            } catch (JSONException error) {
                MainThread.post(() -> callback.onError(error));
                return;
            }
            ActivityDraftEntity entity = new ActivityDraftEntity(owner, id, revision,
                    System.currentTimeMillis(), draft.getCategory(), "active", payload,
                    remoteVersion, true, randomId(), null, draft.getRequestId());
            dao.put(entity);
            SavedActivityDraft saved = toSaved(entity);
            MainThread.post(() -> {
                callback.onSuccess(saved);
                if (canSync()) sync(NO_OP);
            });
        });
    }

    @Override
    public void delete(SavedActivityDraft draft, Callback<Void> callback) {
        String owner = currentUserId();
        if (owner == null || draft == null) {
            callback.onError(new IllegalStateException("Sign in to delete a draft."));
            return;
        }
        worker.execute(() -> {
            ActivityDraftEntity entity = dao.get(owner, draft.getId());
            if (entity == null) {
                MainThread.post(() -> callback.onSuccess(null));
                return;
            }
            entity.state = "deleted";
            entity.payload = null;
            entity.pending = true;
            entity.updatedAt = System.currentTimeMillis();
            entity.operationId = randomId();
            dao.put(entity);
            MainThread.post(() -> {
                callback.onSuccess(null);
                if (canSync()) sync(NO_OP);
            });
        });
    }

    @Override
    public void published(SavedActivityDraft draft, String requestId, Callback<Void> callback) {
        String owner = currentUserId();
        if (owner == null || draft == null) {
            callback.onError(new IllegalStateException("Sign in to close a draft."));
            return;
        }
        worker.execute(() -> {
            ActivityDraftEntity entity = dao.get(owner, draft.getId());
            if (entity == null) {
                MainThread.post(() -> callback.onSuccess(null));
                return;
            }
            entity.state = "published";
            entity.payload = null;
            entity.pending = true;
            entity.updatedAt = System.currentTimeMillis();
            entity.operationId = randomId();
            entity.requestKey = requestId;
            dao.put(entity);
            MainThread.post(() -> {
                callback.onSuccess(null);
                if (canSync()) sync(NO_OP);
            });
        });
    }

    @Override
    public void sync(Callback<Void> callback) {
        String owner = currentUserId();
        if (functions == null || owner == null || !AnalyticsTracker.getInstance().isOnline()) {
            callback.onSuccess(null);
            return;
        }
        synchronized (this) {
            if (syncing) {
                syncAgain = true;
                callback.onSuccess(null);
                return;
            }
            syncing = true;
        }
        pushNext(owner, callback);
    }

    @Override
    public void useCloud(SavedActivityDraft draft, Callback<Void> callback) {
        String owner = currentUserId();
        if (owner == null || draft == null) {
            callback.onError(new IllegalStateException("Sign in to reload your drafts."));
            return;
        }
        worker.execute(() -> {
            dao.delete(owner, draft.getId());
            MainThread.post(() -> sync(callback));
        });
    }

    @Override
    public void keepLocalAsNew(SavedActivityDraft draft, Callback<SavedActivityDraft> callback) {
        String owner = currentUserId();
        if (owner == null || draft == null || draft.getActivity() == null) {
            callback.onError(new IllegalStateException("Sign in to keep this draft."));
            return;
        }
        worker.execute(() -> {
            dao.delete(owner, draft.getId());
            ActivityDraft activity = draft.getActivity();
            String payload;
            try {
                payload = activity.toJson();
            } catch (JSONException error) {
                MainThread.post(() -> callback.onError(error));
                return;
            }
            ActivityDraftEntity entity = new ActivityDraftEntity(owner, randomId(), 1,
                    System.currentTimeMillis(), activity.getCategory(), "active", payload, 0, true,
                    randomId(), null, activity.getRequestId());
            dao.put(entity);
            SavedActivityDraft saved = toSaved(entity);
            MainThread.post(() -> {
                callback.onSuccess(saved);
                if (canSync()) sync(NO_OP);
            });
        });
    }

    private void pushNext(String owner, Callback<Void> callback) {
        worker.execute(() -> {
            List<ActivityDraftEntity> pending = dao.pending(owner);
            if (pending.isEmpty()) {
                pull(owner, callback);
                return;
            }
            ActivityDraftEntity entity = pending.get(0);
            Map<String, Object> input = new HashMap<>();
            input.put("id", entity.id);
            input.put("operationId", entity.operationId);
            input.put("expectedVersion", entity.remoteVersion);
            input.put("state", entity.state);
            input.put("category", entity.category);
            input.put("clientUserId", owner);
            if ("active".equals(entity.state)) {
                ActivityDraft draft = entity.payload == null ? null : ActivityDraft.fromJson(entity.payload);
                if (draft == null) {
                    markConflict(owner, entity.id, "The local draft could not be read.");
                    pushNext(owner, callback);
                    return;
                }
                input.put("payload", draft.toRemotePayload());
                if (draft.getRequestId() != null) input.put("requestId", draft.getRequestId());
            } else {
                input.put("payload", null);
                if (entity.requestKey != null) input.put("requestId", entity.requestKey);
            }
            functions.getHttpsCallable("syncActivityDraft").call(input)
                    .addOnSuccessListener(result -> {
                        long version = number(map(result.getData()).get("version"), entity.remoteVersion + 1);
                        worker.execute(() -> {
                            if ("active".equals(entity.state)) {
                                dao.markSynced(owner, entity.id, version, System.currentTimeMillis());
                            } else {
                                dao.delete(owner, entity.id);
                            }
                            pushNext(owner, callback);
                        });
                    })
                    .addOnFailureListener(error -> {
                        if (error instanceof FirebaseFunctionsException
                                && ((FirebaseFunctionsException) error).getCode()
                                == FirebaseFunctionsException.Code.ABORTED) {
                            worker.execute(() -> {
                                markConflict(owner, entity.id,
                                        "Changed on another device. Choose which version to keep.");
                                pushNext(owner, callback);
                            });
                        } else if (!AnalyticsTracker.getInstance().isOnline()) {
                            finishSync(callback);
                        } else {
                            worker.execute(() -> {
                                markConflict(owner, entity.id, error.getLocalizedMessage());
                                pushNext(owner, callback);
                            });
                        }
                    });
        });
    }

    private void pull(String owner, Callback<Void> callback) {
        functions.getHttpsCallable("getActivityDrafts").call(new HashMap<>())
                .addOnSuccessListener(result -> {
                    Object raw = map(result.getData()).get("drafts");
                    List<Map<?, ?>> remote = new ArrayList<>();
                    if (raw instanceof List) {
                        for (Object item : (List<?>) raw) {
                            if (item instanceof Map) remote.add((Map<?, ?>) item);
                        }
                    }
                    worker.execute(() -> {
                        Map<String, ActivityDraftEntity> local = new HashMap<>();
                        for (ActivityDraftEntity entity : dao.all(owner)) local.put(entity.id, entity);
                        for (Map<?, ?> row : remote) {
                            mergeRemote(owner, local, row);
                        }
                        finishSync(callback);
                    });
                })
                .addOnFailureListener(error -> finishSync(callback));
    }

    private void mergeRemote(String owner, Map<String, ActivityDraftEntity> local, Map<?, ?> row) {
        String id = string(row.get("id"));
        if (id.isEmpty()) return;
        long version = number(row.get("version"), 0);
        Object rawPayload = row.get("payload");
        if (!(rawPayload instanceof Map)) return;
        ActivityDraft draft = ActivityDraft.fromRemotePayload((Map<?, ?>) rawPayload);
        if (draft == null) return;
        String payload;
        try {
            payload = draft.toJson();
        } catch (JSONException error) {
            return;
        }
        ActivityDraftEntity current = local.get(id);
        if (current == null) {
            dao.put(new ActivityDraftEntity(owner, id, version, System.currentTimeMillis(),
                    draft.getCategory(), "active", payload, version, false, randomId(), null,
                    draft.getRequestId()));
            return;
        }
        if (current.pending && current.remoteVersion != version) {
            dao.markConflict(owner, id, "Changed on another device. Choose which version to keep.");
        } else if (!current.pending && version > current.remoteVersion) {
            current.revision = version;
            current.updatedAt = System.currentTimeMillis();
            current.category = draft.getCategory();
            current.payload = payload;
            current.remoteVersion = version;
            current.pending = false;
            current.syncError = null;
            current.requestKey = draft.getRequestId();
            dao.put(current);
        }
    }

    private void markConflict(String owner, String id, @Nullable String message) {
        dao.markConflict(owner, id, message == null ? "Changed on another device." : message);
    }

    private void finishSync(Callback<Void> callback) {
        boolean again;
        synchronized (this) {
            syncing = false;
            again = syncAgain;
            syncAgain = false;
        }
        if (again) {
            sync(callback);
        } else {
            MainThread.post(() -> callback.onSuccess(null));
        }
    }

    private boolean canSync() {
        return functions != null && currentUserId() != null
                && AnalyticsTracker.getInstance().isOnline();
    }

    private SavedActivityDraft toSaved(ActivityDraftEntity entity) {
        ActivityDraft activity = entity.payload == null ? null : ActivityDraft.fromJson(entity.payload);
        if (activity == null) {
            activity = new ActivityDraft("", "Draft unavailable", "", entity.category, "",
                    PlannedActivity.STATUS_PROPOSED, "", "", null, null, null, null,
                    new ArrayList<>(), "", -1);
        }
        String status;
        if (functions == null) {
            status = SavedActivityDraft.SYNC_LOCAL;
        } else if ("conflict".equals(entity.syncError)) {
            status = SavedActivityDraft.SYNC_CONFLICT;
        } else if (entity.pending) {
            status = SavedActivityDraft.SYNC_PENDING;
        } else {
            status = SavedActivityDraft.SYNC_SYNCED;
        }
        return new SavedActivityDraft(entity.id, entity.revision, entity.updatedAt, activity,
                status, entity.state, entity.remoteVersion);
    }

    private static String currentUserId() {
        UserProfile user = UserSession.getCurrentUser();
        return user == null ? null : user.getId();
    }

    private static String randomId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static Map<?, ?> map(Object value) {
        return value instanceof Map ? (Map<?, ?>) value : new HashMap<>();
    }

    private static String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private static long number(Object value, long fallback) {
        return value instanceof Number ? ((Number) value).longValue() : fallback;
    }
}
