package com.example.parchapp.data;

import androidx.annotation.Nullable;

import com.example.parchapp.domain.model.ActivityDraft;
import com.example.parchapp.domain.model.SavedActivityDraft;
import com.example.parchapp.util.Callback;

import java.util.List;

/** Locally saved activity drafts, synchronized with the user's private cloud drafts. */
public interface ActivityDraftsRepository {
    void load(Callback<List<SavedActivityDraft>> callback);

    void save(ActivityDraft draft, @Nullable SavedActivityDraft previous,
              Callback<SavedActivityDraft> callback);

    void delete(SavedActivityDraft draft, Callback<Void> callback);

    void published(SavedActivityDraft draft, String requestId, Callback<Void> callback);

    void sync(Callback<Void> callback);

    void useCloud(SavedActivityDraft draft, Callback<Void> callback);

    void keepLocalAsNew(SavedActivityDraft draft, Callback<SavedActivityDraft> callback);
}
