package com.example.parchapp.analytics;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;

// bq7: follows one opening of the new activity form and logs each step only one time
// it has no android code, so the tests can run without a phone
// it never logs titles, places or emails, only the step, its order and times
public final class CreationFlowTracker {

// the steps of the form in the order they are on the screen
    public enum Step {
        DETAILS(1, "details"),
        GROUP(2, "group"),
        DURATION(3, "duration"),
        DATE_TIME(4, "date_time"),
        PLACE(5, "place"),
        REVIEW(6, "review");

        public final int index;
        public final String stepName;

        Step(int index, String stepName) {
            this.index = index;
            this.stepName = stepName;
        }
    }

// where the events go, the app uses the analytics tracker and the tests use a list
    public interface EventSink {
        void track(String name, Map<String, Object> params);
    }

    private final String flowId;
    private final long startedAt;
    private final EventSink sink;
    private final LongSupplier clock;
    private final Set<Step> seen = EnumSet.noneOf(Step.class);
    private boolean finished;

    private CreationFlowTracker(String flowId, long startedAt, EventSink sink, LongSupplier clock) {
        this.flowId = flowId;
        this.startedAt = startedAt;
        this.sink = sink;
        this.clock = clock;
    }

// the form was opened, a new flow id is made and creation_started is logged
    public static CreationFlowTracker start(String entryPoint) {
        return start(entryPoint, AnalyticsTracker.getInstance()::track, System::currentTimeMillis);
    }

    static CreationFlowTracker start(String entryPoint, EventSink sink, LongSupplier clock) {
        CreationFlowTracker flow = new CreationFlowTracker(UUID.randomUUID().toString(), clock.getAsLong(), sink, clock);
        Map<String, Object> params = flow.base();
        params.put(AnalyticsEvents.PARAM_ENTRY_POINT, entryPoint);
        sink.track(AnalyticsEvents.CREATION_STARTED, params);
        return flow;
    }

// the phone turned and the form was made again, it is the same flow, so nothing is logged again
    public static CreationFlowTracker restore(String flowId, long startedAt, List<String> seenSteps, boolean finished) {
        return restore(flowId, startedAt, seenSteps, finished,
                AnalyticsTracker.getInstance()::track, System::currentTimeMillis);
    }

    static CreationFlowTracker restore(String flowId, long startedAt, List<String> seenSteps, boolean finished,
                                       EventSink sink, LongSupplier clock) {
        CreationFlowTracker flow = new CreationFlowTracker(flowId, startedAt, sink, clock);
        for (Step step : Step.values()) {
            if (seenSteps != null && seenSteps.contains(step.name())) {
                flow.seen.add(step);
            }
        }
        flow.finished = finished;
        return flow;
    }

// the user reached a step, each step is logged only one time for each flow
    public void stepViewed(Step step) {
        if (finished || !seen.add(step)) {
            return;
        }
        Map<String, Object> params = base();
        params.put(AnalyticsEvents.PARAM_STEP_NAME, step.stepName);
        params.put(AnalyticsEvents.PARAM_STEP_INDEX, step.index);
        sink.track(AnalyticsEvents.CREATION_STEP_VIEWED, params);
    }

// the activity was created, we log how long the whole flow took
    public void finished() {
        if (finished) {
            return;
        }
        finished = true;
        Map<String, Object> params = base();
        params.put(AnalyticsEvents.PARAM_TOTAL_MS, Math.max(0L, clock.getAsLong() - startedAt));
        sink.track(AnalyticsEvents.CREATION_FINISHED, params);
    }

    public String getFlowId() {
        return flowId;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public boolean isFinished() {
        return finished;
    }

// names of the steps already logged, the form saves them when the phone turns
    public ArrayList<String> seenSteps() {
        ArrayList<String> names = new ArrayList<>();
        for (Step step : seen) {
            names.add(step.name());
        }
        return names;
    }

    private Map<String, Object> base() {
        Map<String, Object> params = new HashMap<>();
        params.put(AnalyticsEvents.PARAM_FLOW_ID, flowId);
        return params;
    }
}
