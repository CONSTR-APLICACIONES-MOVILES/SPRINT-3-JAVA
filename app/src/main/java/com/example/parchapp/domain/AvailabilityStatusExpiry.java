package com.example.parchapp.domain;

import java.util.Calendar;
import java.util.TimeZone;

/** Converts the status duration choices into backend-safe absolute expiries. */
public final class AvailabilityStatusExpiry {
    public static final long MAX_DURATION_MS = 24 * 60 * 60 * 1000L;
    private static final TimeZone BOGOTA = TimeZone.getTimeZone("America/Bogota");

    private AvailabilityStatusExpiry() { }

    public static long expiryFor(String duration, long nowMillis) {
        if ("Until tomorrow".equals(duration)) return nowMillis + MAX_DURATION_MS;
        if ("Until 6:00 PM".equals(duration)) {
            Calendar expiry = Calendar.getInstance(BOGOTA);
            expiry.setTimeInMillis(nowMillis);
            expiry.set(Calendar.HOUR_OF_DAY, 18);
            expiry.set(Calendar.MINUTE, 0);
            expiry.set(Calendar.SECOND, 0);
            expiry.set(Calendar.MILLISECOND, 0);
            if (expiry.getTimeInMillis() <= nowMillis) expiry.add(Calendar.DAY_OF_YEAR, 1);
            return expiry.getTimeInMillis();
        }
        return nowMillis + 60 * 60 * 1000L;
    }

    /** Creates the next local occurrence of a selected time, constrained to the next 24 hours. */
    public static long manualTime(int hour, int minute, long nowMillis) {
        Calendar expiry = Calendar.getInstance(BOGOTA);
        expiry.setTimeInMillis(nowMillis);
        expiry.set(Calendar.HOUR_OF_DAY, hour);
        expiry.set(Calendar.MINUTE, minute);
        expiry.set(Calendar.SECOND, 0);
        expiry.set(Calendar.MILLISECOND, 0);
        if (expiry.getTimeInMillis() <= nowMillis) expiry.add(Calendar.DAY_OF_YEAR, 1);
        return expiry.getTimeInMillis();
    }

    public static boolean isValid(long expiresAtMillis, long nowMillis) {
        long remaining = expiresAtMillis - nowMillis;
        return remaining > 0 && remaining <= MAX_DURATION_MS;
    }
}
