package com.example.parchapp.data;

import com.example.parchapp.domain.model.Group;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;

import java.util.Collections;
import java.util.NoSuchElementException;

/** Empty Sprint 3 base adapter; Firestore group behavior belongs to its feature branch. */
class EmptyGroupRepository implements GroupRepository {
    @Override public void getGroup(String groupId, Callback<Group> callback) {
        MainThread.post(() -> callback.onError(new NoSuchElementException("Groups feature is not enabled")));
    }
    @Override public void getGroups(String userId, Callback<java.util.List<Group>> callback) {
        MainThread.post(() -> callback.onSuccess(Collections.emptyList()));
    }
    @Override public void observeGroup(String groupId, GroupListener listener) { }
    @Override public void removeGroupListener(String groupId, GroupListener listener) { }
    @Override public void setRsvp(String groupId, String userId, boolean going) { }
    @Override public void addInvite(String groupId, String email) { }
    @Override public void removeInvite(String groupId, String email) { }
}
