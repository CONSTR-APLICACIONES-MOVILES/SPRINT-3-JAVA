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

// bq3: the slots the app sends keep its order and get the length of the plan
    @Test
    public void shownSlotsKeepTheAppOrderAndThePlanLength() {
        List<long[]> shown = SlotDecision.shownSlots(new int[]{18 * 60, 8 * 60, 14 * 60}, 0, 60);

        assertEquals(3, shown.size());
        assertEquals(18 * H, shown.get(0)[0]);
        assertEquals(19 * H, shown.get(0)[1]);
        assertEquals(8 * H, shown.get(1)[0]);
        assertEquals(15 * H, shown.get(2)[1]);
    }

// a slot that would pass midnight ends at midnight and only the first 10 are sent
    @Test
    public void shownSlotsStopAtMidnightAndAtTen() {
        assertEquals(24 * H, SlotDecision.shownSlots(new int[]{23 * 60}, 0, 120).get(0)[1]);
        assertEquals(SlotDecision.MAX_SHOWN, SlotDecision.shownSlots(new int[15], 0, 60).size());
    }

// keeping the time of the picked slot counts as unchanged at the position of the app
    @Test
    public void onShownSlotsKeptAtTheAppPosition() {
        List<long[]> shown = SlotDecision.shownSlots(new int[]{18 * 60, 8 * 60, 14 * 60}, 0, 60);
        SlotDecision decision = SlotDecision.onShownSlots(shown, 8 * H, 9 * H, 1);

        assertFalse(decision.modified);
        assertEquals(1, decision.position);
    }

// moving the time counts as modified at the picked position, also below the third slot
    @Test
    public void onShownSlotsModifiedAtThePickedPosition() {
        List<long[]> shown = SlotDecision.shownSlots(new int[]{18 * 60, 8 * 60, 14 * 60, 16 * 60}, 0, 60);
        SlotDecision decision = SlotDecision.onShownSlots(shown, 16 * H + H / 2, 17 * H + H / 2, 3);

        assertTrue(decision.modified);
        assertEquals(3, decision.position);
    }

// a slot that was not sent, like the slot 15 of the full screen, gives no decision
    @Test
    public void onShownSlotsNothingToDecideOutsideTheList() {
        List<long[]> shown = SlotDecision.shownSlots(new int[]{18 * 60, 8 * 60}, 0, 60);

        assertNull(SlotDecision.onShownSlots(shown, 9 * H, 10 * H, 5));
        assertNull(SlotDecision.onShownSlots(shown, 9 * H, 10 * H, -1));
    }

    @Test
    public void presentsTheVisibleSlotsAndTheDecidedOneBelowThem() {
        assertEquals(3, SlotDecision.presentedCount(4, SlotDecision.choose(suggestions(), 9 * H, 10 * H, 0)));
        assertEquals(4, SlotDecision.presentedCount(4, SlotDecision.choose(suggestions(), 19 * H, 20 * H, 0)));
        assertEquals(2, SlotDecision.presentedCount(2, null));
    }
}
