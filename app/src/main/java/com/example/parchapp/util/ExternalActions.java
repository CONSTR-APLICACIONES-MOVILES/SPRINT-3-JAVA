package com.example.parchapp.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.CalendarContract;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Real device integrations used by the activity and plan flows. */
public final class ExternalActions {
    private ExternalActions() {
    }

    public static boolean openCalendar(Context context, String title, String date, String time,
                                       String location) {
        Calendar start = parseDateTime(date, time);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.HOUR_OF_DAY, 1);
        Intent intent = new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, title == null ? "ParchApp activity" : title)
                .putExtra(CalendarContract.Events.EVENT_LOCATION, location == null ? "" : location)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.getTimeInMillis())
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.getTimeInMillis());
        if (!canResolve(context, intent)) {
            return false;
        }
        context.startActivity(intent);
        return true;
    }

    private static Calendar parseDateTime(String date, String time) {
        String value = ((date == null ? "" : date.trim()) + " "
                + (time == null ? "" : time.trim())).trim();
        String[] patterns = {"yyyy-MM-dd h:mm a", "yyyy-MM-dd HH:mm", "MM/dd/yyyy h:mm a", "h:mm a", "HH:mm"};
        for (String pattern : patterns) {
            ParsePosition position = new ParsePosition(0);
            Date parsed = new SimpleDateFormat(pattern, Locale.US).parse(value, position);
            if (parsed != null && position.getIndex() == value.length()) {
                Calendar result = Calendar.getInstance();
                result.setTime(parsed);
                return result;
            }
        }
        Calendar fallback = Calendar.getInstance();
        fallback.add(Calendar.MINUTE, 5);
        fallback.set(Calendar.SECOND, 0);
        fallback.set(Calendar.MILLISECOND, 0);
        return fallback;
    }

    private static boolean canResolve(Context context, Intent intent) {
        return intent.resolveActivity(context.getPackageManager()) != null;
    }
}
