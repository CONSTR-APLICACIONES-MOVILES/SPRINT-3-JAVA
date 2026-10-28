package com.example.parchapp.domain;

/** Small, deterministic accelerometer classifier for foreground commute suggestions. */
public final class SustainedMotionDetector {
    private static final double GRAVITY_EARTH = 9.80665;
    private static final double MOTION_THRESHOLD = 1.8;
    private static final double LINEAR_MOTION_THRESHOLD = 1.2;
    private static final int REQUIRED_SAMPLES = 6;

    private int motionSamples;

    /** Uses acceleration including gravity; useful when the linear-acceleration sensor is absent. */
    public boolean addAccelerometerSample(float x, float y, float z) {
        double magnitude = Math.sqrt(x * x + y * y + z * z);
        return addMotionSample(Math.abs(magnitude - GRAVITY_EARTH), MOTION_THRESHOLD);
    }

    /** Uses gravity-compensated acceleration from TYPE_LINEAR_ACCELERATION. */
    public boolean addLinearAccelerationSample(float x, float y, float z) {
        double magnitude = Math.sqrt(x * x + y * y + z * z);
        return addMotionSample(magnitude, LINEAR_MOTION_THRESHOLD);
    }

    public void reset() {
        motionSamples = 0;
    }

    private boolean addMotionSample(double magnitude, double threshold) {
        if (magnitude >= threshold) {
            motionSamples++;
        } else {
            motionSamples = Math.max(0, motionSamples - 1);
        }
        if (motionSamples < REQUIRED_SAMPLES) {
            return false;
        }
        reset();
        return true;
    }
}
