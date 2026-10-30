package com.example.parchapp.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.parchapp.domain.model.ComparisonEvidence;
import com.example.parchapp.domain.model.PlannedActivity;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ActivityCreationOutboxTest {

    @Test
    public void durableRoundTripKeepsRequestAndComparisonContextUserScoped() {
        MemoryStore store = new MemoryStore();
        ActivityCreationOutbox firstProcess = new ActivityCreationOutbox(store);
        PendingActivityCreation draft = draft("owner", "request-1", PendingActivityCreation.State.PENDING);

        assertTrue(firstProcess.save(draft));

        ActivityCreationOutbox afterRestart = new ActivityCreationOutbox(store);
        PendingActivityCreation restored = afterRestart.get("owner", "request-1");
        assertNotNull(restored);
        assertNull(afterRestart.get("another-user", "request-1"));
        assertEquals("group-1", restored.getActivity().getGroupId());
        assertEquals("comparison-1", restored.getComparisonEvidence().getId());
        assertEquals(Arrays.asList("friend-1", "owner"), restored.getComparisonEvidence().getParticipantIds());
        assertArrayEquals(new int[]{540, 600, 660}, restored.getShownStartMinutes());
        assertEquals(PendingActivityCreation.State.PENDING, restored.getState());
    }

    @Test
    public void recoveringInterruptedPublishOnlyResetsTheRequestedUsersDrafts() {
        MemoryStore store = new MemoryStore();
        ActivityCreationOutbox outbox = new ActivityCreationOutbox(store);
        assertTrue(outbox.save(draft("owner", "owner-request", PendingActivityCreation.State.SYNCING)));
        assertTrue(outbox.save(draft("other", "other-request", PendingActivityCreation.State.SYNCING)));

        outbox.recoverInterrupted("owner");

        assertEquals(PendingActivityCreation.State.PENDING,
                outbox.get("owner", "owner-request").getState());
        assertEquals(PendingActivityCreation.State.SYNCING,
                outbox.get("other", "other-request").getState());
    }

    private static PendingActivityCreation draft(String userId, String requestId,
                                                  PendingActivityCreation.State state) {
        PlannedActivity activity = new PlannedActivity(null, "group-1", "Study session", "study",
                "2026-11-02", "09:00", "Library", PlannedActivity.STATUS_PROPOSED,
                1_800_000_000_000L, 3, true, "2026-11-02T14:00:00.000Z",
                "2026-11-02T15:00:00.000Z");
        ComparisonEvidence evidence = ComparisonEvidence.of("comparison-1", userId,
                Collections.singletonList("friend-1"), "2026-11-02");
        return new PendingActivityCreation(userId, requestId, "session-1", 1_800_000_000_000L,
                activity, evidence, 1, 60, 1_800_000_000_000L, 1_800_000_060_000L,
                new int[]{540, 600, 660}, "flow-1", 1_799_999_000_000L,
                Collections.singletonList("GROUP"), state, "waiting", null, "");
    }

    private static final class MemoryStore implements ActivityCreationOutbox.Store {
        private String serialized = "[]";

        @Override public String read() { return serialized; }
        @Override public boolean write(String value) {
            serialized = value;
            return true;
        }
    }
}
