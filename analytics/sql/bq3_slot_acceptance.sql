-- bq3: what percentage of the suggested slots are accepted by the organizer without changes
-- slot_selected is saved when the organizer picks a suggested slot in compare availability
-- slot_accepted is saved when the activity is confirmed, modified 0 means no changes
-- suggested_slot_acceptance is the percentage of selected slots accepted without changes for each position
-- the position with is_recommended true goes to insights/bq3_slot_acceptance, field recommended_position
-- change parchapp.analytics_PROPERTY_ID for the real analytics dataset

WITH selected AS (
  SELECT
    (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'slot_position') AS slot_position
  FROM `parchapp.analytics_PROPERTY_ID.events_*`
  WHERE event_name = 'slot_selected'
    AND _TABLE_SUFFIX BETWEEN FORMAT_DATE('%Y%m%d', DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY))
                          AND FORMAT_DATE('%Y%m%d', CURRENT_DATE())
),
accepted AS (
  SELECT
    (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'slot_position') AS slot_position,
    (SELECT value.int_value FROM UNNEST(event_params) WHERE key = 'modified') AS modified
  FROM `parchapp.analytics_PROPERTY_ID.events_*`
  WHERE event_name = 'slot_accepted'
    AND _TABLE_SUFFIX BETWEEN FORMAT_DATE('%Y%m%d', DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY))
                          AND FORMAT_DATE('%Y%m%d', CURRENT_DATE())
),
-- how many times each position was selected and how many were accepted without changes
by_position AS (
  SELECT
    s.slot_position,
    s.selected,
    IFNULL(a.accepted_unchanged, 0) AS accepted_unchanged
  FROM (
    SELECT slot_position, COUNT(*) AS selected
    FROM selected
    WHERE slot_position IS NOT NULL
    GROUP BY slot_position
  ) s
  LEFT JOIN (
    SELECT slot_position, COUNT(*) AS accepted_unchanged
    FROM accepted
    WHERE modified = 0 AND slot_position IS NOT NULL
    GROUP BY slot_position
  ) a USING (slot_position)
)
SELECT
  slot_position,
  selected,
  accepted_unchanged,
  ROUND(100 * accepted_unchanged / selected, 1) AS suggested_slot_acceptance,
  accepted_unchanged = MAX(accepted_unchanged) OVER () AS is_recommended
FROM by_position
ORDER BY slot_position;
