package com.example.parchapp.data;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import java.util.List;

// strategy 7, local storage: the queries of the create group draft
// room does not let these run on the main thread, GroupDraftStore calls them from its queue
@Dao
public abstract class GroupDraftDao {
    @Nullable
    @Query("SELECT * FROM group_drafts WHERE owner = :owner")
    public abstract GroupDraftEntity draft(String owner);

    @Query("SELECT email FROM draft_invites WHERE owner = :owner ORDER BY position")
    public abstract List<String> emails(String owner);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void putDraft(GroupDraftEntity draft);

    @Insert
    public abstract void putInvites(List<DraftInviteEntity> invites);

    @Query("DELETE FROM draft_invites WHERE owner = :owner")
    public abstract void removeInvites(String owner);

    @Query("DELETE FROM group_drafts WHERE owner = :owner")
    public abstract void remove(String owner);

// a transaction, so the draft and its emails change together or do not change
    @Transaction
    public void replace(GroupDraftEntity draft, List<DraftInviteEntity> invites) {
        removeInvites(draft.owner);
        putDraft(draft);
        putInvites(invites);
    }
}
