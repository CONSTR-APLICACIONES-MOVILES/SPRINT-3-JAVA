package com.example.parchapp.data;

import com.example.parchapp.domain.model.Group;
import com.example.parchapp.util.Callback;

import java.util.List;

/** Single access point for group data and actions; controllers do not depend directly on Firestore. */
public interface GroupRepository {
    void getGroup(String groupId, Callback<Group> callback);

    void getGroups(String userId, Callback<List<Group>> callback);

    /* Subscribes to live updates. The listener also receives the current value. */
    void observeGroup(String groupId, GroupListener listener);

    /* Call from {@code onStop()} to avoid leaking the screen. */
    void removeGroupListener(String groupId, GroupListener listener);

    void setRsvp(String groupId, String userId, boolean going);

    void addInvite(String groupId, String email);

    void removeInvite(String groupId, String email);
}
