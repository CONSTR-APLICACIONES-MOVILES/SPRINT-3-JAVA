package com.example.parchapp.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One backend-ranked candidate shown in Suggested Times. */
public final class RecommendedSlot {
    private final String id;
    private final long startTimeMillis;
    private final long endTimeMillis;
    private final int availableParticipants;
    private final int totalParticipants;
    private final List<String> reasons;

    public RecommendedSlot(String id, long startTimeMillis, long endTimeMillis,
                           int availableParticipants, int totalParticipants,
                           List<String> reasons) {
        if (id == null || id.trim().isEmpty() || endTimeMillis <= startTimeMillis
                || totalParticipants < 1 || availableParticipants < 0
                || availableParticipants > totalParticipants) {
            throw new IllegalArgumentException("Invalid recommended slot.");
        }
        List<String> safeReasons = new ArrayList<>();
        if (reasons != null) {
            for (String reason : reasons) {
                if (reason != null && !reason.trim().isEmpty()) safeReasons.add(reason.trim());
            }
        }
        if (safeReasons.isEmpty()) throw new IllegalArgumentException("A slot needs a reason.");
        this.id = id;
        this.startTimeMillis = startTimeMillis;
        this.endTimeMillis = endTimeMillis;
        this.availableParticipants = availableParticipants;
        this.totalParticipants = totalParticipants;
        this.reasons = Collections.unmodifiableList(safeReasons);
    }

    public String getId() { return id; }
    public long getStartTimeMillis() { return startTimeMillis; }
    public long getEndTimeMillis() { return endTimeMillis; }
    public int getAvailableParticipants() { return availableParticipants; }
    public int getTotalParticipants() { return totalParticipants; }
    public List<String> getReasons() { return reasons; }
}
