package com.example.parchapp.domain;

import java.util.ArrayList;
import java.util.List;

/* BQ3: turns the slot the organizer picked in Compare Availability into a decision on the backend's own suggestions for the new activity. If the saved time is one of the suggestions it was kept unchanged; otherwise the suggestion at the position the organizer picked was modified. */
public final class SlotDecision {
    /* Compare shows the best slot and two more without scrolling, so those are the ones the organizer saw. */
    public static final int VISIBLE_SUGGESTIONS = 3;
    private static final int MINUTES_PER_DAY = 1440;
    private static final long MINUTE_MS = 60_000L;

    public final int position;
    public final boolean modified;

    private SlotDecision(int position, boolean modified) {
        this.position = position;
        this.modified = modified;
    }

    /* startsMs/endsMs: the backend suggestions in their ranked order. slotPosition: the position picked in Compare. Returns null when there is no suggestion to decide on. */
    public static SlotDecision choose(List<long[]> suggestions, long chosenStartMs, long chosenEndMs, int slotPosition) {
        if (suggestions.isEmpty() || slotPosition < 0) {
            return null;
        }
        for (int i = 0; i < suggestions.size(); i++) {
            long[] slot = suggestions.get(i);
            if (slot[0] == chosenStartMs && slot[1] == chosenEndMs) {
                return new SlotDecision(i, false);
            }
        }
        int shown = Math.min(VISIBLE_SUGGESTIONS, suggestions.size());
        return new SlotDecision(Math.min(slotPosition, shown - 1), true);
    }

// the backend keeps 10 suggestions at most, so only the first 10 slots of the app are sent
    public static final int MAX_SHOWN = 10;

// bq3: start and end of each slot the organizer saw, in the same order, all with the length of the plan
// a slot that would go past midnight ends at midnight, so no slot is removed and the positions do not move
    public static List<long[]> shownSlots(int[] startMinutes, long midnightMs, int durationMinutes) {
        List<long[]> slots = new ArrayList<>();
        int count = Math.min(MAX_SHOWN, startMinutes.length);
        for (int i = 0; i < count; i++) {
            int start = startMinutes[i];
            int end = Math.min(start + durationMinutes, MINUTES_PER_DAY);
            slots.add(new long[]{midnightMs + start * MINUTE_MS, midnightMs + end * MINUTE_MS});
        }
        return slots;
    }

// bq3: the decision on the list the organizer saw, the position is always the one of the app
// unchanged when the plan has exactly the time of one of those slots, modified when the organizer moved it
// null when the picked slot was not sent, for example the slot 15 of the full screen
    public static SlotDecision onShownSlots(List<long[]> shown, long chosenStartMs, long chosenEndMs, int slotPosition) {
        if (slotPosition < 0 || slotPosition >= shown.size()) {
            return null;
        }
        for (int i = 0; i < shown.size(); i++) {
            long[] slot = shown.get(i);
            if (slot[0] == chosenStartMs && slot[1] == chosenEndMs) {
                return new SlotDecision(i, false);
            }
        }
        return new SlotDecision(slotPosition, true);
    }

    /* Positions to acknowledge as presented: the visible ones, plus the decided one if it was further down. */
    public static int presentedCount(int suggestions, SlotDecision decision) {
        int shown = Math.min(VISIBLE_SUGGESTIONS, suggestions);
        return decision == null ? shown : Math.max(shown, decision.position + 1);
    }
}
