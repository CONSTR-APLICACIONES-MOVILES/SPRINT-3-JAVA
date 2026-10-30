package com.example.parchapp.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import com.example.parchapp.domain.model.GroupDraft;

import java.util.List;

// strategy 7, local storage: one row for each account, so two people on the same phone do not mix their drafts
// the emails are in draft_invites, one draft has many invites
@Entity(tableName = "group_drafts")
public class GroupDraftEntity {
    @PrimaryKey
    @NonNull
    public String owner = "";
    public String name;
    public long pickedDate;
    public int pickedMinute;
    public String dateText;
    public String timeText;

    static GroupDraftEntity from(String owner, GroupDraft draft) {
        GroupDraftEntity entity = new GroupDraftEntity();
        entity.owner = owner;
        entity.name = draft.getName();
        entity.pickedDate = draft.getPickedDate();
        entity.pickedMinute = draft.getPickedMinute();
        entity.dateText = draft.getDateText();
        entity.timeText = draft.getTimeText();
        return entity;
    }

    GroupDraft toDraft(List<String> emails) {
        return new GroupDraft(name, pickedDate, pickedMinute, dateText, timeText, emails);
    }
}
