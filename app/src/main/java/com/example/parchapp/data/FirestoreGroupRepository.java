package com.example.parchapp.data;

import android.util.Log;

import com.example.parchapp.domain.model.Group;
import com.example.parchapp.domain.model.Member;
import com.example.parchapp.domain.model.TimeBlock;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/* F6: reads the groups collection for the Dashboard. Reads try the local cache first and then the server, and live updates come from one snapshot listener per observed group. Writing groups (create, RSVP, invites) is the Create Group feature, so those calls only log for now. */
class FirestoreGroupRepository extends BaseGroupRepository {
    private static final String TAG = "FirestoreGroupRepo";
    private static final String COLLECTION = "groups";

    private final CollectionReference collection;
    private final Map<String, ListenerRegistration> registrations = new HashMap<>();

    FirestoreGroupRepository(FirebaseFirestore db) {
        collection = db.collection(COLLECTION);
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

    @Override
    protected void startObserving(String groupId) {
        ListenerRegistration registration = collection.document(groupId).addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Log.w(TAG, "Listening to group " + groupId + " failed", error);
                return;
            }
            if (snapshot != null && snapshot.exists()) {
                notifyListeners(fromSnapshot(snapshot));
            }
        });
        registrations.put(groupId, registration);
    }

    @Override
    protected void stopObserving(String groupId) {
        ListenerRegistration registration = registrations.remove(groupId);
        if (registration != null) {
            registration.remove();
        }
    }

    @Override
    public void setRsvp(String groupId, String userId, boolean going) {
        Log.i(TAG, "RSVP is not part of this branch");
    }

    @Override
    public void addInvite(String groupId, String email) {
        Log.i(TAG, "Invites are not part of this branch");
    }

    @Override
    public void removeInvite(String groupId, String email) {
        Log.i(TAG, "Invites are not part of this branch");
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
                stringList(snapshot.get("maybeIds")), stringList(snapshot.get("pendingInvites")));
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
