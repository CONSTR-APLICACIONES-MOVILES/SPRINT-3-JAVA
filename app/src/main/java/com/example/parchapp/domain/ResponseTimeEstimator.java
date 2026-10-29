package com.example.parchapp.domain;

import com.example.parchapp.domain.model.ResponseTimeEstimate;

import java.util.List;

/** Chooses the closest available group-size estimate, preferring the smaller size on ties. */
public final class ResponseTimeEstimator {
    private ResponseTimeEstimator() { }

    public static ResponseTimeEstimate forGroupSize(int groupSize,
                                                     List<ResponseTimeEstimate> estimates) {
        if (groupSize <= 0 || estimates == null || estimates.isEmpty()) return null;
        ResponseTimeEstimate best = null;
        for (ResponseTimeEstimate estimate : estimates) {
            if (estimate == null) continue;
            if (best == null) {
                best = estimate;
                continue;
            }
            int distance = Math.abs(estimate.getGroupSize() - groupSize);
            int bestDistance = Math.abs(best.getGroupSize() - groupSize);
            if (distance < bestDistance
                    || (distance == bestDistance && estimate.getGroupSize() < best.getGroupSize())) {
                best = estimate;
            }
        }
        return best;
    }
}
