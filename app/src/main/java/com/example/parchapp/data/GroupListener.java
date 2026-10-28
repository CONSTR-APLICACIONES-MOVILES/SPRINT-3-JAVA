package com.example.parchapp.data;

import com.example.parchapp.domain.model.Group;

/* Observer of a group document. Called on the main thread every time the group changes. */
public interface GroupListener {
    void onGroupChanged(Group group);
}
