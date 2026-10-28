package com.example.parchapp.analytics;

/* Event and parameter names shared by the app and the BigQuery queries in {@code analytics/sql}.
Changing a name here i this case breaks the matching query. <p>Privacy: parameters never include calendar titles, places or attendees. Only group sizes,
    slot positions, durations, feature names and timestamps are logged.
 */
public final class AnalyticsEvents {
    private AnalyticsEvents() {
    }

    /* BQ1: one calculation of common slots by the availability engine. */
    public static final String AVAILABILITY_CALCULATED = "availability_calc";
    /* BQ8: a core coordination feature was opened. */
    public static final String FEATURE_USED = "feature_used";
    public static final String ACTIVITY_CREATED = "activity_created";
    /* BQ2: activity creation reliability and latency. */
    public static final String ACTIVITY_CREATE_ATTEMPT = "activity_create_attempt";
    public static final String ACTIVITY_CREATE_RESULT = "activity_create_result";

    public static final String PARAM_GROUP_SIZE = "group_size";
    public static final String PARAM_DURATION_MS = "duration_ms";
    public static final String PARAM_SLOT_COUNT = "slot_count";
    public static final String PARAM_RANKER = "ranker";
    public static final String PARAM_SOURCE = "source";
    public static final String PARAM_FEATURE = "feature";
    public static final String PARAM_SCREEN = "screen";
    public static final String PARAM_CATEGORY = "category";
    public static final String PARAM_CLIENT_TS = "client_ts";
    public static final String PARAM_ACTIVITY_ID = "activity_id";
    public static final String PARAM_CREATION_SESSION_ID = "creation_session_id";
    public static final String PARAM_RESULT = "result";
    public static final String PARAM_ERROR_CODE = "error_code";

    // BQ8 feature names.
    public static final String FEATURE_GROUPS = "groups";
    public static final String FEATURE_COMPARE_AVAILABILITY = "compare_availability";
    public static final String FEATURE_SCHEDULE = "schedule";
    public static final String FEATURE_INVITATIONS = "invitations";
    public static final String FEATURE_ALERTS = "alerts";
}
