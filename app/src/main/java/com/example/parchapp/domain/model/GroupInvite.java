package com.example.parchapp.domain.model;

// what the backend says about an invitation before the user decides to join
public final class GroupInvite {
    private final String groupId;
    private final String groupName;
    private final boolean alreadyMember;

    public GroupInvite(String groupId, String groupName, boolean alreadyMember) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.alreadyMember = alreadyMember;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public boolean isAlreadyMember() {
        return alreadyMember;
    }
}
