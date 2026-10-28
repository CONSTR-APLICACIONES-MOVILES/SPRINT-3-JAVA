package com.example.parchapp.domain;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import com.example.parchapp.domain.model.PlannedActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* F6: reads the date and time of the plans saved in Firestore, so the Dashboard can count the plans of today and pick the next one. Everything is in Bogota time. */
public final class PlanContext {
    private static final Pattern TIME = Pattern.compile("(\\d{1,2}):(\\d{2})\\s*([AaPp][Mm])?");
    private static final String[] DATE_FORMATS = {"EEE, MMM d, yyyy", "yyyy-MM-dd", "MMM d, yyyy", "EEE, MMM d"};

    private PlanContext() {
    }

    /* Start minute of "5:00 PM – 6:00 PM", "7:30 PM" or "17:30", or -1 if there is no time. */
    public static int startMinute(String time) {
        return nthMinute(time, 0);
    }

    /* End minute of a range, or -1 when the time has no end. */
    public static int endMinute(String time) {
        return nthMinute(time, 1);
    }

    private static int nthMinute(String time, int index) {
        if (time == null) {
            return -1;
        }
        Matcher matcher = TIME.matcher(time);
        for (int i = 0; matcher.find(); i++) {
            if (i == index) {
                int hour = Integer.parseInt(matcher.group(1));
                int minute = Integer.parseInt(matcher.group(2));
                String period = matcher.group(3);
                if (period != null) {
                    boolean pm = period.equalsIgnoreCase("pm");
                    hour = hour % 12 + (pm ? 12 : 0);
                }
                return hour * 60 + minute;
            }
        }
        return -1;
    }

    /* True when the plan's date is today ("Today", "Tonight" and empty dates count as today). */
    public static boolean isToday(String date, Calendar now) {
        if (date == null || date.trim().isEmpty()) {
            return true;
        }
        String text = date.trim().toLowerCase(Locale.ROOT);
        if (text.startsWith("today") || text.startsWith("tonight")) {
            return true;
        }
        for (String pattern : DATE_FORMATS) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setTimeZone(now.getTimeZone());
            format.setLenient(false);
            try {
                Calendar parsed = Calendar.getInstance(now.getTimeZone());
                parsed.setTime(format.parse(date.trim()));
                boolean hasYear = pattern.contains("yyyy");
                return parsed.get(Calendar.DAY_OF_MONTH) == now.get(Calendar.DAY_OF_MONTH)
                        && parsed.get(Calendar.MONTH) == now.get(Calendar.MONTH)
                        && (!hasYear || parsed.get(Calendar.YEAR) == now.get(Calendar.YEAR));
            } catch (ParseException ignored) {
                // try the next format
            }
        }
        return false;
    }

    /* days from today to the plan date (0 today, 1 tomorrow, -1 yesterday), or null when there is no date we can read */
    public static Integer dayOffset(String date, Calendar now) {
        if (date == null || date.trim().isEmpty()) {
            return null;
        }
        String text = date.trim().toLowerCase(Locale.ROOT);
        if (text.startsWith("today") || text.startsWith("tonight")) {
            return 0;
        }
        for (String pattern : DATE_FORMATS) {
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
            format.setTimeZone(now.getTimeZone());
            format.setLenient(false);
            try {
                Calendar parsed = Calendar.getInstance(now.getTimeZone());
                parsed.setTime(format.parse(date.trim()));
                if (!pattern.contains("yyyy")) {
                    parsed.set(Calendar.YEAR, now.get(Calendar.YEAR));
                }
                Calendar today = (Calendar) now.clone();
                for (Calendar c : new Calendar[]{parsed, today}) {
                    c.set(Calendar.HOUR_OF_DAY, 12);
                    c.set(Calendar.MINUTE, 0);
                    c.set(Calendar.SECOND, 0);
                    c.set(Calendar.MILLISECOND, 0);
                }
                return (int) Math.round((parsed.getTimeInMillis() - today.getTimeInMillis()) / 86_400_000.0);
            } catch (ParseException ignored) {
                // try the next format
            }
        }
        return null;
    }

    /* plans dated today, for "You have N active plans today" */
    public static int countToday(List<PlannedActivity> plans, Calendar now) {
        int count = 0;
        for (PlannedActivity plan : plans) {
            Integer offset = dayOffset(plan.getDate(), now);
            if (offset != null && offset == 0) {
                count++;
            }
        }
        return count;
    }

    /* the next plan that has not ended: today ones still running or coming, otherwise the closest future day */
    public static PlannedActivity nextPlan(List<PlannedActivity> plans, Calendar now, int nowMinute) {
        PlannedActivity best = null;
        int bestDay = Integer.MAX_VALUE;
        int bestStart = Integer.MAX_VALUE;
        for (PlannedActivity plan : plans) {
            Integer offset = dayOffset(plan.getDate(), now);
            if (offset == null || offset < 0) {
                continue;
            }
            int start = startMinute(plan.getTime());
            if (offset == 0) {
                int end = endMinute(plan.getTime());
                int finish = end > start ? end : start >= 0 ? start + 60 : 24 * 60;
                if (finish <= nowMinute) {
                    continue;
                }
            }
            int sortStart = start < 0 ? 24 * 60 : start;
            if (offset < bestDay || (offset == bestDay && sortStart < bestStart)) {
                best = plan;
                bestDay = offset;
                bestStart = sortStart;
            }
        }
        return best;
    }

    public static TimeZone bogota() {
        return TimeZone.getTimeZone("America/Bogota");
    }
}
