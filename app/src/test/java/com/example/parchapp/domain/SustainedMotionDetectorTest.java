package com.example.parchapp.domain;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SustainedMotionDetectorTest {
    @Test
    public void stationaryAccelerometerSamplesDoNotSuggestCommute() {
        SustainedMotionDetector detector = new SustainedMotionDetector();
        for (int i = 0; i < 20; i++) {
            assertFalse(detector.addAccelerometerSample(0f, 0f, 9.80665f));
        }
    }

    @Test
    public void oneMotionSpikeIsNotEnoughToSuggestCommute() {
        SustainedMotionDetector detector = new SustainedMotionDetector();
        assertFalse(detector.addLinearAccelerationSample(1.5f, 0f, 0f));
        for (int i = 0; i < 4; i++) {
            assertFalse(detector.addLinearAccelerationSample(0f, 0f, 0f));
        }
    }

    @Test
    public void sustainedLinearAccelerationSuggestsOnceAfterThresholdSamples() {
        SustainedMotionDetector detector = new SustainedMotionDetector();
        for (int i = 0; i < 5; i++) {
            assertFalse(detector.addLinearAccelerationSample(1.5f, 0f, 0f));
        }
        assertTrue(detector.addLinearAccelerationSample(1.5f, 0f, 0f));
        assertFalse(detector.addLinearAccelerationSample(0f, 0f, 0f));
    }
}
