package com.example.parchapp.data;

import com.example.parchapp.domain.model.Group;

/* Observer of a group document. Called on the main thread every time the group changes. */
public interface GroupListener {
    void onGroupChanged(Group group);

// the group was deleted, the user left it or can not read it anymore
// screens that do not need it keep this empty version
    default void onGroupUnavailable(String groupId) {
    }

// fromCache is true when the data comes from the copy saved on the phone and not from the server
// screens that show when the data was synced use it, the others keep this empty version
    default void onGroupSyncChanged(String groupId, boolean fromCache) {
    }
}
