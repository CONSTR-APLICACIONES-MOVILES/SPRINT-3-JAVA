package com.example.parchapp.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;

import java.util.ArrayList;
import java.util.List;

// strategy 7, local storage: one row for each email of a draft, position keeps the order of the form
// the foreign key removes the emails when their draft is removed
@Entity(tableName = "draft_invites",
        primaryKeys = {"owner", "position"},
        foreignKeys = @ForeignKey(entity = GroupDraftEntity.class, parentColumns = "owner",
                childColumns = "owner", onDelete = ForeignKey.CASCADE))
public class DraftInviteEntity {
    @NonNull
    public String owner = "";
    public int position;
    public String email;

    static List<DraftInviteEntity> listOf(String owner, List<String> emails) {
        List<DraftInviteEntity> invites = new ArrayList<>();
        for (int i = 0; i < emails.size(); i++) {
            DraftInviteEntity invite = new DraftInviteEntity();
            invite.owner = owner;
            invite.position = i;
            invite.email = emails.get(i);
            invites.add(invite);
        }
        return invites;
    }
}
