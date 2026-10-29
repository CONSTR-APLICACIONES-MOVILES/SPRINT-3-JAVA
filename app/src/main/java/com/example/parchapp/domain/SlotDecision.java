package com.example.parchapp.domain;

import java.util.List;

/* BQ3: turns the slot the organizer picked in Compare Availability into a decision on the backend's own suggestions for the new activity. If the saved time is one of the suggestions it was kept unchanged; otherwise the suggestion at the position the organizer picked was modified. */
public final class SlotDecision {
    /* Compare shows the best slot and two more without scrolling, so those are the ones the organizer saw. */
    public static final int VISIBLE_SUGGESTIONS = 3;

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

    /* Positions to acknowledge as presented: the visible ones, plus the decided one if it was further down. */
    public static int presentedCount(int suggestions, SlotDecision decision) {
        int shown = Math.min(VISIBLE_SUGGESTIONS, suggestions);
        return decision == null ? shown : Math.max(shown, decision.position + 1);
    }
}
