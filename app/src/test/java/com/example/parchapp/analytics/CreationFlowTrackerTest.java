package com.example.parchapp.analytics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

// tests for the bq7 events of the new activity form
public class CreationFlowTrackerTest {

    private final List<String> names = new ArrayList<>();
    private final List<Map<String, Object>> params = new ArrayList<>();
    private final AtomicLong now = new AtomicLong(1_000L);

    private CreationFlowTracker open(String entryPoint) {
        return CreationFlowTracker.start(entryPoint, (name, values) -> {
            names.add(name);
            params.add(new HashMap<>(values));
        }, now::get);
    }

// a complete flow logs start, the six steps in order and the end with the total time
    @Test
    public void completeFlow() {
        CreationFlowTracker flow = open(AnalyticsEvents.ENTRY_DASHBOARD);
        for (CreationFlowTracker.Step step : CreationFlowTracker.Step.values()) {
            flow.stepViewed(step);
        }
        now.set(31_000L);
        flow.finished();

        assertEquals(8, names.size());
        assertEquals(AnalyticsEvents.CREATION_STARTED, names.get(0));
        assertEquals(AnalyticsEvents.ENTRY_DASHBOARD, params.get(0).get(AnalyticsEvents.PARAM_ENTRY_POINT));
        assertEquals("details", params.get(1).get(AnalyticsEvents.PARAM_STEP_NAME));
        assertEquals(6, params.get(6).get(AnalyticsEvents.PARAM_STEP_INDEX));
        assertEquals(AnalyticsEvents.CREATION_FINISHED, names.get(7));
        assertEquals(30_000L, params.get(7).get(AnalyticsEvents.PARAM_TOTAL_MS));
        for (Map<String, Object> event : params) {
            assertEquals(flow.getFlowId(), event.get(AnalyticsEvents.PARAM_FLOW_ID));
        }
    }

// an abandon in step 2 has start and two steps, and no end event
    @Test
    public void abandonInStepTwo() {
        CreationFlowTracker flow = open(AnalyticsEvents.ENTRY_GROUP);
        flow.stepViewed(CreationFlowTracker.Step.DETAILS);
        flow.stepViewed(CreationFlowTracker.Step.GROUP);

        assertEquals(3, names.size());
        assertFalse(names.contains(AnalyticsEvents.CREATION_FINISHED));
        assertEquals(2, params.get(2).get(AnalyticsEvents.PARAM_STEP_INDEX));
        assertFalse(flow.isFinished());
    }

// the same step many times is logged only one time, and nothing is logged after the end
    @Test
    public void repeatedStepsAreNotDuplicated() {
        CreationFlowTracker flow = open(AnalyticsEvents.ENTRY_SLOT);
        flow.stepViewed(CreationFlowTracker.Step.DETAILS);
        flow.stepViewed(CreationFlowTracker.Step.DETAILS);
        flow.stepViewed(CreationFlowTracker.Step.PLACE);
        flow.stepViewed(CreationFlowTracker.Step.DETAILS);
        flow.finished();
        flow.finished();
        flow.stepViewed(CreationFlowTracker.Step.REVIEW);

        assertEquals(4, names.size());
    }

// every opening of the form has its own flow id
    @Test
    public void eachOpeningHasAnotherFlowId() {
        CreationFlowTracker first = open(AnalyticsEvents.ENTRY_DASHBOARD);
        CreationFlowTracker second = open(AnalyticsEvents.ENTRY_DASHBOARD);

        assertNotEquals(first.getFlowId(), second.getFlowId());
    }

// after the phone turns the flow continues, the start and the old steps are not logged again
    @Test
    public void restoredFlowDoesNotLogAgain() {
        CreationFlowTracker flow = open(AnalyticsEvents.ENTRY_SCHEDULE);
        flow.stepViewed(CreationFlowTracker.Step.DETAILS);
        CreationFlowTracker restored = CreationFlowTracker.restore(flow.getFlowId(), flow.getStartedAt(),
                flow.seenSteps(), false, (name, values) -> {
                    names.add(name);
                    params.add(new HashMap<>(values));
                }, now::get);
        restored.stepViewed(CreationFlowTracker.Step.DETAILS);
        restored.stepViewed(CreationFlowTracker.Step.GROUP);

        assertEquals(3, names.size());
        assertEquals(flow.getFlowId(), params.get(2).get(AnalyticsEvents.PARAM_FLOW_ID));
        assertTrue(restored.seenSteps().contains("GROUP"));
    }
}
