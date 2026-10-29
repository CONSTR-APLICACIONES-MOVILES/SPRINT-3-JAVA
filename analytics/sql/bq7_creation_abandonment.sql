/* BQ7 (Type 3, Feature Analysis)
   In which step do users abandon the creation of an activity most often?

   Source events, logged through AnalyticsTracker by CreationFlowTracker in the new activity form:
     creation_started      (flow_id, entry_point)
     creation_step_viewed  (flow_id, step_name, step_index)
     creation_finished     (flow_id, total_ms)
   Steps: 0 opened, 1 details, 2 group, 3 duration, 4 date_time, 5 place, 6 review.
   A flow with creation_started and without creation_finished is an abandon, its highest step says where.
   Same logic as buildCreationAbandonment in Backend/functions/src/teamInsights.js, which the team dashboard uses today.
   Replace `parchapp.analytics_PROPERTY_ID` with the Firebase Analytics export dataset. */

WITH events AS (
  SELECT
    event_name,
    (SELECT value.string_value FROM UNNEST(event_params) WHERE key = 'flow_id') AS flow_id,
    (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'step_index') AS step_index
  FROM `parchapp.analytics_PROPERTY_ID.events_*`
  WHERE event_name IN ('creation_started', 'creation_step_viewed', 'creation_finished')
    AND _TABLE_SUFFIX >= FORMAT_DATE('%Y%m%d', DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY))
),
flows AS (
  SELECT
    flow_id,
    COALESCE(MAX(IF(event_name = 'creation_step_viewed', step_index, NULL)), 0) AS last_step,
    LOGICAL_OR(event_name = 'creation_finished') AS finished
  FROM events
  WHERE flow_id IS NOT NULL
  GROUP BY flow_id
  HAVING LOGICAL_OR(event_name = 'creation_started')
),
steps AS (
  SELECT 0 AS step_index, 'opened' AS step_name UNION ALL
  SELECT 1, 'details' UNION ALL
  SELECT 2, 'group' UNION ALL
  SELECT 3, 'duration' UNION ALL
  SELECT 4, 'date_time' UNION ALL
  SELECT 5, 'place' UNION ALL
  SELECT 6, 'review'
),
totals AS (
  SELECT COUNT(*) AS total_flows, COUNTIF(finished) AS total_finished FROM flows
)
SELECT
  s.step_index,
  s.step_name,
  COUNTIF(f.flow_id IS NOT NULL) AS abandoned,
  ROUND(100 * SAFE_DIVIDE(COUNTIF(f.flow_id IS NOT NULL), ANY_VALUE(t.total_flows)), 1) AS percent,
  ANY_VALUE(t.total_flows) AS total_flows,
  ANY_VALUE(t.total_finished) AS total_finished,
  ROUND(100 * SAFE_DIVIDE(ANY_VALUE(t.total_flows) - ANY_VALUE(t.total_finished), ANY_VALUE(t.total_flows)), 1) AS abandon_rate
FROM steps s
CROSS JOIN totals t
LEFT JOIN flows f ON f.last_step = s.step_index AND NOT f.finished
GROUP BY s.step_index, s.step_name
ORDER BY s.step_index;
