package com.example.parchapp.data;

import android.util.Log;

import com.example.parchapp.domain.model.Group;
import com.example.parchapp.domain.model.GroupInvite;
import com.example.parchapp.domain.model.Member;
import com.example.parchapp.domain.model.TimeBlock;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.functions.FirebaseFunctions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/* F6: the groups collection. Reads try the local cache first and then the server, and live updates come from one snapshot listener per observed group. New groups are created by the backend function createGroup, with the user as owner. */
class FirestoreGroupRepository extends BaseGroupRepository {
    private static final String TAG = "FirestoreGroupRepo";
    private static final String COLLECTION = "groups";
// the longest time the backend accepts, 7 days
    private static final int INVITE_HOURS = 168;

    private final CollectionReference collection;
// f6: the rules do not let the app write a new group, so the backend function creates it
    private final FirebaseFunctions functions;
    private final Map<String, ListenerRegistration> registrations = new HashMap<>();
// last data of each group, a change of only the metadata does not draw the screen again
    private final Map<String, Map<String, Object>> lastDataByGroup = new HashMap<>();

    FirestoreGroupRepository(FirebaseFirestore db, FirebaseFunctions functions) {
        collection = db.collection(COLLECTION);
        this.functions = functions;
    }

// f6: calls the createGroup function, then saves the emails as pending invites of the new group
    @Override
    public void createGroup(String name, String category, List<String> inviteEmails, Callback<String> callback) {
        Map<String, Object> input = new HashMap<>();
// f6: a new request id for each try, the backend uses it so the same group is not created two times
        input.put("requestId", UUID.randomUUID().toString());
        input.put("name", name);
        input.put("category", category);
        input.put("inviteeIds", new ArrayList<String>());
        functions.getHttpsCallable("createGroup").call(input)
                .addOnSuccessListener(result -> {
                    Object data = result.getData();
                    Object id = data instanceof Map ? ((Map<?, ?>) data).get("groupId") : null;
                    if (id == null) {
                        MainThread.post(() -> callback.onError(new IllegalStateException(
                                "Backend response did not contain groupId")));
                        return;
                    }
                    String groupId = id.toString();
                    if (inviteEmails.isEmpty()) {
                        MainThread.post(() -> callback.onSuccess(groupId));
                        return;
                    }
                    collection.document(groupId)
                            .update("pendingInvites", FieldValue.arrayUnion(inviteEmails.toArray()))
                            .addOnSuccessListener(done -> MainThread.post(() -> callback.onSuccess(groupId)))
// f6: the group already exists, so we still open it even if the invites failed
                            .addOnFailureListener(error -> {
                                Log.w(TAG, "Saving the invites of " + groupId + " failed", error);
                                MainThread.post(() -> callback.onSuccess(groupId));
                            });
                })
                .addOnFailureListener(error -> MainThread.post(() -> callback.onError(error)));
    }

// the backend only gives a code to the owner of the group
    @Override
    public void createGroupInvite(String groupId, String userId, Callback<String> callback) {
        Map<String, Object> input = new HashMap<>();
        input.put("requestId", UUID.randomUUID().toString());
        input.put("kind", "group");
        input.put("entityId", groupId);
        input.put("hours", INVITE_HOURS);
        input.put("clientUserId", userId);
        functions.getHttpsCallable("createShareLink").call(input)
                .addOnSuccessListener(result -> {
                    Object code = responseField(result.getData(), "token");
                    MainThread.post(() -> {
                        if (code == null) {
                            callback.onError(new IllegalStateException("Backend response did not contain token"));
                        } else {
                            callback.onSuccess(code.toString());
                        }
                    });
                })
                .addOnFailureListener(error -> MainThread.post(() -> callback.onError(error)));
    }

    @Override
    public void previewInvite(String code, Callback<GroupInvite> callback) {
        Map<String, Object> input = new HashMap<>();
        input.put("token", code);
        functions.getHttpsCallable("resolveShareLink").call(input)
                .addOnSuccessListener(result -> {
                    Object data = result.getData();
                    Object groupId = responseField(data, "entityId");
                    MainThread.post(() -> {
                        if (groupId == null || !"group".equals(responseField(data, "kind"))) {
                            callback.onError(new IllegalArgumentException("This code is not a group invitation"));
                        } else {
                            callback.onSuccess(new GroupInvite(groupId.toString(),
                                    asString(responseField(data, "title")),
                                    Boolean.TRUE.equals(responseField(data, "member"))));
                        }
                    });
                })
                .addOnFailureListener(error -> MainThread.post(() -> callback.onError(error)));
    }

    @Override
    public void joinWithInvite(String code, String userId, Callback<String> callback) {
        Map<String, Object> input = new HashMap<>();
        input.put("token", code);
        input.put("clientUserId", userId);
        functions.getHttpsCallable("joinSharedGroup").call(input)
                .addOnSuccessListener(result -> {
                    Object groupId = responseField(result.getData(), "groupId");
                    MainThread.post(() -> {
                        if (groupId == null) {
                            callback.onError(new IllegalStateException("Backend response did not contain groupId"));
                        } else {
                            callback.onSuccess(groupId.toString());
                        }
                    });
                })
                .addOnFailureListener(error -> MainThread.post(() -> callback.onError(error)));
    }

    private static Object responseField(Object data, String field) {
        return data instanceof Map ? ((Map<?, ?>) data).get(field) : null;
    }

    @Override
    public void getGroup(String groupId, Callback<Group> callback) {
        DocumentReference ref = collection.document(groupId);
        ref.get(Source.CACHE).addOnCompleteListener(cacheTask -> {
            if (cacheTask.isSuccessful() && cacheTask.getResult().exists()) {
                callback.onSuccess(fromSnapshot(cacheTask.getResult()));
                return;
            }
            ref.get()
                    .addOnSuccessListener(snapshot -> {
                        if (snapshot.exists()) {
                            callback.onSuccess(fromSnapshot(snapshot));
                            return;
                        }
                        callback.onError(new NoSuchElementException("Group " + groupId + " not found"));
                    })
                    .addOnFailureListener(callback::onError);
        });
    }

    // the security rules only let me read groups where i am in memberIds, so the query has to filter by it
    @Override
    public void getGroups(String userId, Callback<List<Group>> callback) {
        collection.whereArrayContains("memberIds", userId).get()
                .addOnSuccessListener(snapshot -> {
                    List<Group> groups = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        groups.add(fromSnapshot(document));
                    }
                    MainThread.post(() -> callback.onSuccess(groups));
                })
                .addOnFailureListener(error -> MainThread.post(() -> callback.onError(error)));
    }

    // runs on a worker thread: Tasks.await blocks until firestore answers, which must never happen on the main thread
    @Override
    public List<Group> loadGroups(String userId, boolean fromCache) throws Exception {
        QuerySnapshot snapshot = Tasks.await(collection.whereArrayContains("memberIds", userId)
                .get(fromCache ? Source.CACHE : Source.DEFAULT));
        List<Group> groups = new ArrayList<>();
        for (DocumentSnapshot document : snapshot.getDocuments()) {
            groups.add(fromSnapshot(document));
        }
        return groups;
    }

    @Override
    protected void startObserving(String groupId) {
// metadata changes too, so we also hear when the same data stops coming from the cache and comes from the server
        ListenerRegistration registration = collection.document(groupId).addSnapshotListener(
                com.google.firebase.firestore.MetadataChanges.INCLUDE, (snapshot, error) -> {
// an error here usually means the user can not read the group anymore
            if (error != null) {
                Log.w(TAG, "Listening to group " + groupId + " failed", error);
                notifyUnavailable(groupId);
                return;
            }
            if (snapshot != null && snapshot.exists()) {
                Map<String, Object> data = snapshot.getData();
                if (data == null || !data.equals(lastDataByGroup.get(groupId))) {
                    lastDataByGroup.put(groupId, data);
                    notifyListeners(fromSnapshot(snapshot));
                }
                notifySync(groupId, snapshot.getMetadata().isFromCache());
// the document does not exist, for example it was deleted while the screen was open
            } else if (snapshot != null && !snapshot.getMetadata().isFromCache()) {
                notifyUnavailable(groupId);
            }
        });
        registrations.put(groupId, registration);
    }

    @Override
    protected void stopObserving(String groupId) {
        ListenerRegistration registration = registrations.remove(groupId);
        lastDataByGroup.remove(groupId);
        if (registration != null) {
            registration.remove();
        }
    }

    @Override
    public void setRsvp(String groupId, String userId, boolean going) {
        Log.i(TAG, "RSVP is not part of this branch");
    }

// f6: adds one email to the pending invites, the snapshot listener shows the change on every screen
    @Override
    public void addInvite(String groupId, String email) {
        collection.document(groupId)
                .update("pendingInvites", FieldValue.arrayUnion(email))
                .addOnFailureListener(e -> Log.w(TAG, "Adding invite failed", e));
    }

// f6: removes one email from the pending invites
    @Override
    public void removeInvite(String groupId, String email) {
        collection.document(groupId)
                .update("pendingInvites", FieldValue.arrayRemove(email))
                .addOnFailureListener(e -> Log.w(TAG, "Removing invite failed", e));
    }

    @SuppressWarnings("unchecked")
    static Group fromSnapshot(DocumentSnapshot snapshot) {
        List<Member> members = new ArrayList<>();
        List<Map<String, Object>> rawMembers = (List<Map<String, Object>>) snapshot.get("members");
        if (rawMembers != null) {
            for (Map<String, Object> raw : rawMembers) {
                List<TimeBlock> busy = new ArrayList<>();
                List<Map<String, Object>> rawBusy = (List<Map<String, Object>>) raw.get("busy");
                if (rawBusy != null) {
                    for (Map<String, Object> block : rawBusy) {
                        busy.add(new TimeBlock(asInt(block.get("start")), asInt(block.get("end"))));
                    }
                }
                members.add(new Member(asString(raw.get("id")), asString(raw.get("name")),
                        asString(raw.get("initials")), busy,
                        asInt(raw.get("preferredStart")), asInt(raw.get("preferredEnd"))));
            }
        }
        return new Group(snapshot.getId(), asString(snapshot.get("name")), asString(snapshot.get("icon")),
                asString(snapshot.get("planTitle")), members, stringList(snapshot.get("goingIds")),
                stringList(snapshot.get("maybeIds")), stringList(snapshot.get("pendingInvites")),
                asString(snapshot.get("ownerId")));
    }

    private static int asInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private static String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    private static List<String> stringList(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                result.add(asString(item));
            }
        }
        return result;
    }
}
