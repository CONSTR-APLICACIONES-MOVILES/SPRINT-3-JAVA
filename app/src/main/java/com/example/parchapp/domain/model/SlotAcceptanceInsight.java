package com.example.parchapp.domain.model;

// bq3: how many suggested slots the organizers accept without changes and which position works best
// the backend writes it every night in insights/bq3_slot_acceptance
public final class SlotAcceptanceInsight {
// same number the backend uses, with less slots the result is not trusted yet
    public static final int MIN_SAMPLE = 20;

    private final int recommendedPosition;
    private final Double percentUnchanged;
    private final int sampleSize;

    public SlotAcceptanceInsight(int recommendedPosition, Double percentUnchanged, int sampleSize) {
        this.recommendedPosition = Math.max(0, recommendedPosition);
        this.percentUnchanged = percentUnchanged;
        this.sampleSize = Math.max(0, sampleSize);
    }

// when there is no document yet the first slot is the recommended one
    public static SlotAcceptanceInsight withoutData(int recommendedPosition) {
        return new SlotAcceptanceInsight(recommendedPosition, null, 0);
    }

// position in the list starting at 0, the screen shows it starting at 1
    public int getRecommendedPosition() {
        return recommendedPosition;
    }

    public Double getPercentUnchanged() {
        return percentUnchanged;
    }

    public int getSampleSize() {
        return sampleSize;
    }

// true when there are enough slots to show the percent to the user
    public boolean hasEnoughData() {
        return percentUnchanged != null && sampleSize >= MIN_SAMPLE;
    }

// true when there are enough slots but nobody accepted one without changes
// then the backend did not find a best position and the first slot is only the default
    public boolean noneAcceptedUnchanged() {
        return hasEnoughData() && percentUnchanged <= 0;
    }

// the percent without decimals, so the text is short
// a small percent like 0.4 shows 1, because 0 would say that nobody accepted a slot
    public int roundedPercent() {
        if (percentUnchanged == null || percentUnchanged <= 0) {
            return 0;
        }
        return (int) Math.max(1, Math.round(percentUnchanged));
    }
}
