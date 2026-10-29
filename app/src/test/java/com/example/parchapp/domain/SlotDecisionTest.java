package com.example.parchapp.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SlotDecisionTest {

    private static final long H = 60 * 60 * 1000L;

    private static List<long[]> suggestions() {
        // backend order: 17:30, 9:00, 14:00, 19:00, all one hour long
        return Arrays.asList(new long[]{17 * H + H / 2, 18 * H + H / 2}, new long[]{9 * H, 10 * H},
                new long[]{14 * H, 15 * H}, new long[]{19 * H, 20 * H});
    }

    @Test
    public void keptWhenTheSavedTimeIsOneOfTheSuggestions() {
        SlotDecision decision = SlotDecision.choose(suggestions(), 14 * H, 15 * H, 0);

        assertFalse(decision.modified);
        assertEquals(2, decision.position);
    }

    @Test
    public void modifiedWhenTheTimeChangedUsesThePickedPosition() {
        SlotDecision decision = SlotDecision.choose(suggestions(), 9 * H + H / 2, 10 * H + H / 2, 1);

        assertTrue(decision.modified);
        assertEquals(1, decision.position);
    }

    @Test
    public void aPickBelowTheVisibleSlotsCountsAgainstTheLastVisibleOne() {
        SlotDecision decision = SlotDecision.choose(suggestions(), 8 * H, 9 * H, 6);

        assertTrue(decision.modified);
        assertEquals(SlotDecision.VISIBLE_SUGGESTIONS - 1, decision.position);
    }

    @Test
    public void nothingToDecideWithoutSuggestionsOrWithoutAComparePick() {
        assertNull(SlotDecision.choose(Collections.emptyList(), 9 * H, 10 * H, 0));
        assertNull(SlotDecision.choose(suggestions(), 9 * H, 10 * H, -1));
    }

    @Test
    public void presentsTheVisibleSlotsAndTheDecidedOneBelowThem() {
        assertEquals(3, SlotDecision.presentedCount(4, SlotDecision.choose(suggestions(), 9 * H, 10 * H, 0)));
        assertEquals(4, SlotDecision.presentedCount(4, SlotDecision.choose(suggestions(), 19 * H, 20 * H, 0)));
        assertEquals(2, SlotDecision.presentedCount(2, null));
    }
}
