package com.example.parchapp.domain.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

// tests for the bq3 result the app shows in compare availability
public class SlotAcceptanceInsightTest {

// with 20 slots or more the percent can be shown
    @Test
    public void enoughDataWithTwentySlots() {
        SlotAcceptanceInsight insight = new SlotAcceptanceInsight(1, 64.0, 25);
        assertTrue(insight.hasEnoughData());
        assertEquals(64, insight.roundedPercent());
        assertEquals(1, insight.getRecommendedPosition());
    }

// with less than 20 slots or without percent the app says there is not enough data
    @Test
    public void notEnoughDataWithFewSlots() {
        assertFalse(new SlotAcceptanceInsight(1, 64.0, 19).hasEnoughData());
        assertFalse(new SlotAcceptanceInsight(1, null, 30).hasEnoughData());
    }

// with enough slots but zero accepted without changes there is no best position
    @Test
    public void zeroPercentMeansNoBestPosition() {
        assertTrue(new SlotAcceptanceInsight(0, 0.0, 30).noneAcceptedUnchanged());
        assertFalse(new SlotAcceptanceInsight(1, 25.9, 27).noneAcceptedUnchanged());
        assertFalse(new SlotAcceptanceInsight(0, 0.0, 10).noneAcceptedUnchanged());
    }

// a very small percent is never shown as 0, because 0 means nobody accepted a slot
    @Test
    public void smallPercentIsAtLeastOne() {
        assertEquals(1, new SlotAcceptanceInsight(2, 0.4, 250).roundedPercent());
        assertEquals(26, new SlotAcceptanceInsight(1, 25.9, 27).roundedPercent());
        assertEquals(0, new SlotAcceptanceInsight(0, 0.0, 30).roundedPercent());
    }

// without document the first slot is recommended and there is no data
    @Test
    public void withoutDataKeepsTheDefaultPosition() {
        SlotAcceptanceInsight insight = SlotAcceptanceInsight.withoutData(0);
        assertEquals(0, insight.getRecommendedPosition());
        assertEquals(0, insight.getSampleSize());
        assertFalse(insight.hasEnoughData());
    }

// a wrong negative value from the database never breaks the list
    @Test
    public void negativeValuesBecomeZero() {
        SlotAcceptanceInsight insight = new SlotAcceptanceInsight(-3, 50.4, -1);
        assertEquals(0, insight.getRecommendedPosition());
        assertEquals(0, insight.getSampleSize());
        assertEquals(50, insight.roundedPercent());
    }
}
