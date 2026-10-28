package com.example.parchapp.data;

import com.example.parchapp.util.Callback;

/* Results that the analytics pipeline writes back for the app to use (Type 2 feedback loop).*/
public interface InsightsRepository {
    int DEFAULT_RECOMMENDED_POSITION = 0;

    /* F2 (BQ3): position in the ranked list of the slot that organisers accept most often without changes. The Compare Availability sheet marks that slot as Recommended. It never fails, it falls back to {@link #DEFAULT_RECOMMENDED_POSITION}. */
    void getRecommendedSlotPosition(Callback<Integer> callback);
}
