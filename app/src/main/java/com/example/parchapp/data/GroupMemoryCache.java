package com.example.parchapp.data;

import androidx.annotation.Nullable;
import androidx.collection.LruCache;

import com.example.parchapp.domain.model.Group;

// strategy 9, caching: the groups opened last stay in memory, so group details draws at once
// firestore still answers after this and replaces the group, so there is no time limit here
public final class GroupMemoryCache {
// about the groups of one student, each entry counts as 1 because sizeOf is not changed
// when it is full the group that was not used for the longest time goes first
    static final int MAX_GROUPS = 20;

// the key has the account, so another account on the phone never sees these groups
// LruCache is synchronized inside, the screens can use it from any thread
    private final LruCache<String, Group> groups = new LruCache<>(MAX_GROUPS);

    @Nullable
    public Group get(@Nullable String userId, String groupId) {
        return groups.get(key(userId, groupId));
    }

    public void put(@Nullable String userId, Group group) {
        groups.put(key(userId, group.getId()), group);
    }

// the group was deleted or the user left it, so it must not be drawn again
    public void remove(@Nullable String userId, String groupId) {
        groups.remove(key(userId, groupId));
    }

    private static String key(@Nullable String userId, String groupId) {
        return (userId == null ? "anonymous" : userId) + "." + groupId;
    }
}
