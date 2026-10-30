package com.example.parchapp.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.parchapp.domain.model.GroupDraft;
import com.example.parchapp.util.BackgroundThreads;

import java.util.concurrent.ExecutorService;

// strategy 7, local storage: the create group draft lives in shared preferences
// it is small key value data of one screen, so it does not need a database
// each account has its own draft, so two people on the same phone do not mix them
public final class GroupDraftStore {
    private static final String PREFS = "parchapp_group_draft";
// strategy 6, multithreading: writing to the disk is i/o, so it does not run on the main thread
// one serial queue for the whole app, the writes run one after the other in the order they were sent
// so the last draft always wins and two writes never mix, in the profiler the thread is parch-draft-1
    private static final ExecutorService WRITER = BackgroundThreads.serial("parch-draft");
    private final SharedPreferences prefs;
    private final String owner;

    public GroupDraftStore(Context context, String userId) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        owner = userId == null ? "anonymous" : userId;
    }

// saves the draft every time the user changes the form, an empty form removes the old draft
// the main thread only copies the values, the queue writes them to the disk with commit
    public void save(GroupDraft draft) {
        if (draft.isEmpty()) {
            clear();
            return;
        }
        String name = draft.getName();
        long date = draft.getPickedDate();
        int time = draft.getPickedMinute();
        String dateText = draft.getDateText();
        String timeText = draft.getTimeText();
        String emails = GroupDraft.encodeEmails(draft.getEmails());
        WRITER.execute(() -> prefs.edit()
                .putString(key("name"), name)
                .putLong(key("date"), date)
                .putInt(key("time"), time)
                .putString(key("date_text"), dateText)
                .putString(key("time_text"), timeText)
                .putString(key("emails"), emails)
                .commit());
    }

// the saved draft, or null when there is nothing saved
// shared preferences keeps the values in memory after the first read, so this read is fast
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
// it goes to the same queue, so it always runs after the saves that came before
    public void clear() {
        WRITER.execute(() -> prefs.edit()
                .remove(key("name")).remove(key("date")).remove(key("time"))
                .remove(key("date_text")).remove(key("time_text")).remove(key("emails"))
                .commit());
    }

    private String key(String field) {
        return owner + "_" + field;
    }
}
