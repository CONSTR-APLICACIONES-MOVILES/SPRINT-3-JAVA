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
// f2: the organizer picked one of the suggested slots in compare availability, bq3 uses it
    public static final String SLOT_SELECTED = "slot_selected";
    /* BQ8: a core coordination feature was opened. */
    public static final String FEATURE_USED = "feature_used";
    public static final String ACTIVITY_CREATED = "activity_created";
    public static final String SLOT_ACCEPTED = "slot_accepted";
    /* BQ9: an activity reached CONFIRMED; with activity_created it gives the confirmation rate with and without Compare Availability. */
    public static final String ACTIVITY_CONFIRMED = "activity_confirmed";
    /* BQ2: activity creation reliability and latency. */
    public static final String ACTIVITY_CREATE_ATTEMPT = "activity_create_attempt";
    public static final String ACTIVITY_CREATE_RESULT = "activity_create_result";
    /* BQ6: pair reminder delivery with a later invitation response. */
    public static final String REMINDER_SENT = "reminder_sent";
    public static final String RSVP_SUBMITTED = "rsvp_submitted";

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
    public static final String PARAM_RECOMMENDATION_ID = "recommendation_id";
    public static final String PARAM_SLOT_POSITION = "slot_position";
    public static final String PARAM_MODIFIED = "modified";
    public static final String PARAM_CREATION_SESSION_ID = "creation_session_id";
    public static final String PARAM_RESULT = "result";
    public static final String PARAM_ERROR_CODE = "error_code";
    public static final String PARAM_LOCATION_AREA_ID = "location_area_id";
    public static final String PARAM_REMINDER_ID = "reminder_id";
    public static final String PARAM_REMINDER_SENT_AT_MS = "reminder_sent_at_ms";
    public static final String PARAM_REMINDER_STRATEGY = "strategy";
    public static final String PARAM_REMINDER_SEQUENCE = "sequence";
    public static final String PARAM_RESPONSE = "response";

    // activity_created sources: bq9 counts suggested_slot as created after comparing availability
    public static final String SOURCE_SUGGESTED_SLOT = "suggested_slot";
    public static final String SOURCE_GROUP_ACTIVITY = "group_activity";

    // BQ8 feature names.
    public static final String FEATURE_GROUPS = "groups";
    public static final String FEATURE_COMPARE_AVAILABILITY = "compare_availability";
    public static final String FEATURE_SCHEDULE = "schedule";
    public static final String FEATURE_INVITATIONS = "invitations";
    public static final String FEATURE_ALERTS = "alerts";
}
