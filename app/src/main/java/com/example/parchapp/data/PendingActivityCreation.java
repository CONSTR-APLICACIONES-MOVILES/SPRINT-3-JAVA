package com.example.parchapp.data;

import androidx.annotation.Nullable;

import com.example.parchapp.domain.model.ComparisonEvidence;
import com.example.parchapp.domain.model.PlannedActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One durable activity publication and the metadata needed to safely retry it. */
public final class PendingActivityCreation {
    public enum State { PENDING, SYNCING, NEEDS_REVIEW, CREATED }

    private final String userId;
    private final String requestId;
    private final String creationSessionId;
    private final long attemptStartedAtMillis;
    private final PlannedActivity activity;
    @Nullable private final ComparisonEvidence comparisonEvidence;
    private final int slotPosition;
    private final int durationMinutes;
    private final long selectedStartMillis;
    private final long selectedEndMillis;
    private final int[] shownStartMinutes;
    private final String flowId;
    private final long flowStartedAtMillis;
    private final List<String> flowSteps;
    private final State state;
    private final String message;
    @Nullable private final String createdActivityId;
    private final String recommendationOutcome;

    public PendingActivityCreation(String userId, String requestId, String creationSessionId,
                                   long attemptStartedAtMillis, PlannedActivity activity,
                                   @Nullable ComparisonEvidence comparisonEvidence,
                                   int slotPosition, int durationMinutes, long selectedStartMillis,
                                   long selectedEndMillis, int[] shownStartMinutes,
                                   String flowId, long flowStartedAtMillis,
                                   List<String> flowSteps, State state, String message,
                                   @Nullable String createdActivityId, String recommendationOutcome) {
        this.userId = userId == null ? "" : userId;
        this.requestId = requestId == null ? "" : requestId;
        this.creationSessionId = creationSessionId == null ? "" : creationSessionId;
        this.attemptStartedAtMillis = attemptStartedAtMillis;
        this.activity = activity;
        this.comparisonEvidence = comparisonEvidence;
        this.slotPosition = slotPosition;
        this.durationMinutes = durationMinutes;
        this.selectedStartMillis = selectedStartMillis;
        this.selectedEndMillis = selectedEndMillis;
        this.shownStartMinutes = shownStartMinutes == null ? null : shownStartMinutes.clone();
        this.flowId = flowId == null ? "" : flowId;
        this.flowStartedAtMillis = flowStartedAtMillis;
        this.flowSteps = Collections.unmodifiableList(new ArrayList<>(
                flowSteps == null ? Collections.emptyList() : flowSteps));
        this.state = state == null ? State.PENDING : state;
        this.message = message == null ? "" : message;
        this.createdActivityId = createdActivityId;
        this.recommendationOutcome = recommendationOutcome == null ? "" : recommendationOutcome;
    }

    public PendingActivityCreation withState(State next, String nextMessage, String activityId,
                                             String nextRecommendationOutcome) {
        return new PendingActivityCreation(userId, requestId, creationSessionId,
                attemptStartedAtMillis, activity, comparisonEvidence, slotPosition, durationMinutes,
                selectedStartMillis, selectedEndMillis, shownStartMinutes, flowId, flowStartedAtMillis, flowSteps,
                next, nextMessage, activityId, nextRecommendationOutcome);
    }

    public String getUserId() { return userId; }
    public String getRequestId() { return requestId; }
    public String getCreationSessionId() { return creationSessionId; }
    public long getAttemptStartedAtMillis() { return attemptStartedAtMillis; }
    public PlannedActivity getActivity() { return activity; }
    public ComparisonEvidence getComparisonEvidence() { return comparisonEvidence; }
    public int getSlotPosition() { return slotPosition; }
    public int getDurationMinutes() { return durationMinutes; }
    public long getSelectedStartMillis() { return selectedStartMillis; }
    public long getSelectedEndMillis() { return selectedEndMillis; }
    public int[] getShownStartMinutes() { return shownStartMinutes == null ? null : shownStartMinutes.clone(); }
    public String getFlowId() { return flowId; }
    public long getFlowStartedAtMillis() { return flowStartedAtMillis; }
    public List<String> getFlowSteps() { return flowSteps; }
    public State getState() { return state; }
    public String getMessage() { return message; }
    @Nullable public String getCreatedActivityId() { return createdActivityId; }
    public String getRecommendationOutcome() { return recommendationOutcome; }
}
