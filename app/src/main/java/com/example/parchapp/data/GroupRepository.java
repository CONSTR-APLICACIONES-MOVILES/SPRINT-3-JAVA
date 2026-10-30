package com.example.parchapp.data;

import com.example.parchapp.domain.model.Group;
import com.example.parchapp.util.Callback;

import java.util.ArrayList;
import java.util.List;

/** Single access point for group data and actions; controllers do not depend directly on Firestore. */
public interface GroupRepository {
    void getGroup(String groupId, Callback<Group> callback);

    void getGroups(String userId, Callback<List<Group>> callback);

    /* Multithreading: the same read, but it waits for the answer, so call it only from a worker thread.
     * fromCache reads only what is saved on the phone. */
    default List<Group> loadGroups(String userId, boolean fromCache) throws Exception {
        return new ArrayList<>();
    }

// f6: creates the group in the backend and saves the emails as pending invites, the callback gets the id of the new group
    void createGroup(String name, String category, List<String> inviteEmails, Callback<String> callback);

    /* Subscribes to live updates. The listener also receives the current value. */
    void observeGroup(String groupId, GroupListener listener);

    /* Call from {@code onStop()} to avoid leaking the screen. */
    void removeGroupListener(String groupId, GroupListener listener);

    void setRsvp(String groupId, String userId, boolean going);

    void addInvite(String groupId, String email);

    void removeInvite(String groupId, String email);
}
