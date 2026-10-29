package com.example.parchapp.domain.model;

/** Historical BQ5 response-time estimate for a group of a given size. */
public final class ResponseTimeEstimate {
    public enum Kind { ALL_RESPONDED, PER_RESPONSE }

    private final int groupSize;
    private final double minutes;
    private final Kind kind;

    public ResponseTimeEstimate(int groupSize, double minutes, Kind kind) {
        if (groupSize < 1 || minutes <= 0 || kind == null) {
            throw new IllegalArgumentException("Invalid response-time estimate.");
        }
        this.groupSize = groupSize;
        this.minutes = minutes;
        this.kind = kind;
    }

    public int getGroupSize() { return groupSize; }
    public double getMinutes() { return minutes; }
    public Kind getKind() { return kind; }
    public long getDurationMillis() { return Math.round(minutes * 60_000d); }

    public String getDurationLabel() {
        if (minutes < 60) return Math.max(1, Math.min(59, Math.round(minutes))) + " min";
        if (minutes < 24 * 60) return halfLabel(minutes / 60) + " h";
        String days = halfLabel(minutes / (24 * 60));
        return "1".equals(days) ? "1 day" : days + " days";
    }

    private static String halfLabel(double value) {
        double rounded = Math.round(value * 2) / 2d;
        return rounded == Math.rint(rounded)
                ? Long.toString(Math.round(rounded)) : Double.toString(rounded);
    }
}
