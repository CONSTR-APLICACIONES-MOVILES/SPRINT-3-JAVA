package com.example.parchapp.domain.model;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/* BQ9: what publishActivity is told about the comparison behind a plan, like the Flutter app does.
 * With an id: the backend comparison (a validated Schedule slot or a proposal) and its participants.
 * direct(): a plan made without comparing. A plan with no evidence at all is left unknown. */
public final class ComparisonEvidence {
    @Nullable
    private final String id;
    private final List<String> participantIds;
    @Nullable
    private final String date;

    private ComparisonEvidence(@Nullable String id, List<String> participantIds, @Nullable String date) {
        this.id = id;
        this.participantIds = Collections.unmodifiableList(participantIds);
        this.date = date;
    }

    /* the backend compares the sorted ids of me and my friends with the participants of the plan */
    public static ComparisonEvidence of(String id, String myId, List<String> friendIds, @Nullable String date) {
        List<String> ids = new ArrayList<>();
        ids.add(myId);
        for (String friend : friendIds) {
            if (!ids.contains(friend)) ids.add(friend);
        }
        Collections.sort(ids);
        return new ComparisonEvidence(id, ids, date);
    }

    public static ComparisonEvidence direct() {
        return new ComparisonEvidence(null, new ArrayList<>(), null);
    }

    public boolean isComparison() {
        return id != null && !id.isEmpty() && participantIds.size() > 1;
    }

    /* the evidence is for one day; if the organizer moved the plan to another day it no longer applies */
    public boolean appliesTo(@Nullable String planDate) {
        return isComparison() && (date == null || date.equals(planDate));
    }

    /* a group can only host the plan when every compared friend is in it */
    public boolean fitsGroup(List<String> memberIds) {
        return memberIds.containsAll(participantIds);
    }

    @Nullable
    public String getId() { return id; }

    public List<String> getParticipantIds() { return participantIds; }

    @Nullable
    public String getDate() { return date; }
}
