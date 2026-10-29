package com.example.parchapp.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.example.parchapp.domain.model.ResponseTimeEstimate;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class ResponseTimeEstimatorTest {
    @Test
    public void choosesNearestGroupSizeAndPrefersSmallerSizeOnTie() {
        ResponseTimeEstimate four = new ResponseTimeEstimate(4, 40, ResponseTimeEstimate.Kind.PER_RESPONSE);
        ResponseTimeEstimate six = new ResponseTimeEstimate(6, 60, ResponseTimeEstimate.Kind.ALL_RESPONDED);

        assertEquals(four, ResponseTimeEstimator.forGroupSize(5, Arrays.asList(six, four)));
        assertEquals(six, ResponseTimeEstimator.forGroupSize(6, Arrays.asList(four, six)));
    }

    @Test
    public void hasNoEstimateWithoutAValidGroupSizeOrRows() {
        assertNull(ResponseTimeEstimator.forGroupSize(0, Collections.emptyList()));
        assertNull(ResponseTimeEstimator.forGroupSize(5, null));
    }
}
