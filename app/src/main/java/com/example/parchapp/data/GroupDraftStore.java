package com.example.parchapp.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.parchapp.domain.model.GroupDraft;

// strategy 7, local storage: the create group draft lives in shared preferences
// it is small key value data of one screen, so it does not need a database
// each account has its own draft, so two people on the same phone do not mix them
public final class GroupDraftStore {
    private static final String PREFS = "parchapp_group_draft";
    private final SharedPreferences prefs;
    private final String owner;

    public GroupDraftStore(Context context, String userId) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        owner = userId == null ? "anonymous" : userId;
    }

// saves the draft every time the user changes the form, an empty form removes the old draft
    public void save(GroupDraft draft) {
        if (draft.isEmpty()) {
            clear();
            return;
        }
        prefs.edit()
                .putString(key("name"), draft.getName())
                .putLong(key("date"), draft.getPickedDate())
                .putInt(key("time"), draft.getPickedMinute())
                .putString(key("date_text"), draft.getDateText())
                .putString(key("time_text"), draft.getTimeText())
                .putString(key("emails"), GroupDraft.encodeEmails(draft.getEmails()))
                .apply();
    }

// the saved draft, or null when there is nothing saved
    public GroupDraft load() {
        if (!prefs.contains(key("name"))) {
            return null;
        }
        GroupDraft draft = new GroupDraft(prefs.getString(key("name"), ""),
                prefs.getLong(key("date"), GroupDraft.NONE),
                prefs.getInt(key("time"), (int) GroupDraft.NONE),
                prefs.getString(key("date_text"), ""),
                prefs.getString(key("time_text"), ""),
                GroupDraft.decodeEmails(prefs.getString(key("emails"), "")));
        return draft.isEmpty() ? null : draft;
    }

// the group was created, so the draft is not needed anymore
    public void clear() {
        prefs.edit()
                .remove(key("name")).remove(key("date")).remove(key("time"))
                .remove(key("date_text")).remove(key("time_text")).remove(key("emails"))
                .apply();
    }

    private String key(String field) {
        return owner + "_" + field;
    }
}
