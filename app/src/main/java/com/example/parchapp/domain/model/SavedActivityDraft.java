package com.example.parchapp.domain.model;

/** Local metadata around one saved activity draft. */
public final class SavedActivityDraft {
    public static final String SYNC_LOCAL = "local";
    public static final String SYNC_PENDING = "pending";
    public static final String SYNC_SYNCED = "synced";
    public static final String SYNC_CONFLICT = "conflict";

    private final String id;
    private final long revision;
    private final long updatedAt;
    private final ActivityDraft activity;
    private final String syncStatus;
    private final String localState;
    private final long remoteVersion;

    public SavedActivityDraft(String id, long revision, long updatedAt, ActivityDraft activity,
                              String syncStatus, String localState, long remoteVersion) {
        this.id = id;
        this.revision = revision;
        this.updatedAt = updatedAt;
        this.activity = activity;
        this.syncStatus = syncStatus;
        this.localState = localState;
        this.remoteVersion = remoteVersion;
    }

    public String getId() { return id; }
    public long getRevision() { return revision; }
    public long getUpdatedAt() { return updatedAt; }
    public ActivityDraft getActivity() { return activity; }
    public String getSyncStatus() { return syncStatus; }
    public String getLocalState() { return localState; }
    public long getRemoteVersion() { return remoteVersion; }
    public boolean isConflict() { return SYNC_CONFLICT.equals(syncStatus); }
    public boolean isActive() { return "active".equals(localState); }
}
