package com.example.parchapp.data;

import android.content.Context;
import android.util.Log;

import com.example.parchapp.domain.model.GroupDraft;
import com.example.parchapp.util.BackgroundThreads;
import com.example.parchapp.util.Callback;
import com.example.parchapp.util.MainThread;

import java.util.concurrent.ExecutorService;

// strategy 7, local storage: the create group draft lives in room, in two related tables
// a draft has many invites, so a table for each one is cleaner than one text with all the emails
// each account has its own draft, so two people on the same phone do not mix them
public final class GroupDraftStore {
    private static final String TAG = "GroupDraftStore";
// strategy 6, multithreading: the database is i/o, so it never runs on the main thread
// one serial queue for the whole app, reads and writes run one after the other in the order they were sent
// so the last draft always wins and a read never sees half a write, in the profiler the thread is parch-draft-1
    private static final ExecutorService QUEUE = BackgroundThreads.serial("parch-draft");
    private final GroupDraftDao dao;
    private final String owner;

    public GroupDraftStore(Context context, String userId) {
        dao = ParchDatabase.get(context).groupDrafts();
        owner = userId == null ? "anonymous" : userId;
    }

// saves the draft every time the user changes the form, an empty form removes the old draft
// a GroupDraft does not change after it is made, so the queue can read it safely from its thread
    public void save(GroupDraft draft) {
        if (draft.isEmpty()) {
            clear();
            return;
        }
        QUEUE.execute(() -> write(() -> dao.replace(GroupDraftEntity.from(owner, draft),
                DraftInviteEntity.listOf(owner, draft.getEmails()))));
    }

// strategy 6: the queue reads the database and the main thread gets the answer, it can touch the views
// the answer is null when there is nothing saved
    public void load(Callback<GroupDraft> callback) {
        QUEUE.execute(() -> {
            try {
                GroupDraftEntity entity = dao.draft(owner);
                GroupDraft draft = entity == null ? null : entity.toDraft(dao.emails(owner));
                MainThread.post(() -> callback.onSuccess(draft == null || draft.isEmpty() ? null : draft));
            } catch (RuntimeException error) {
                MainThread.post(() -> callback.onError(error));
            }
        });
    }

// the group was created, so the draft is not needed anymore, the foreign key removes its emails too
// it goes to the same queue, so it always runs after the saves that came before
    public void clear() {
        QUEUE.execute(() -> write(() -> dao.remove(owner)));
    }

// an error of the phone storage loses one save of the draft, it must not close the app
    private static void write(Runnable change) {
        try {
            change.run();
        } catch (RuntimeException error) {
            Log.w(TAG, "Could not write the group draft", error);
        }
    }
}
