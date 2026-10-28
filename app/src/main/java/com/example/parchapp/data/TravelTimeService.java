package com.example.parchapp.data;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.parchapp.domain.PlanContext;
import com.example.parchapp.domain.model.CampusPlace;
import com.example.parchapp.domain.model.PlannedActivity;
import com.example.parchapp.util.Callback;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.util.Calendar;
import java.util.List;

/* F3: travel time from the user's location to the place of a plan, for the Today's Plans card. The place and its coordinates come from campusAreas and the plan time from the activity document. For now it is a walking estimate; the Google Maps time (F7, DistanceMatrixClient) plugs in at measure(). */
public class TravelTimeService {

    public enum Problem {NO_PERMISSION, NO_LOCATION, NO_PLACE, PLACES_UNAVAILABLE}

    public static final class Result {
        public final int minutes;
        /* "Google Maps", the cache, or null for the walking estimate. */
        @Nullable
        public final String source;
        public final CampusPlace place;
        public final boolean late;

        Result(int minutes, @Nullable String source, CampusPlace place, boolean late) {
            this.minutes = minutes;
            this.source = source;
            this.place = place;
            this.late = late;
        }
    }

    public interface Listener {
        void onTravel(Result result);

        void onProblem(Problem problem);
    }

    /* F7: source shown when the minutes come from the ActivityRepository cache. */
    static final String CACHED_SOURCE = "saved travel time";

    private final Context context;

    public TravelTimeService(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public void travelTo(PlannedActivity activity, Listener listener) {
        if (!hasLocationPermission()) {
            listener.onProblem(Problem.NO_PERMISSION);
            return;
        }
        Repositories.places().getPlaces(new Callback<List<CampusPlace>>() {
            @Override
            public void onSuccess(List<CampusPlace> places) {
                CampusPlace place = CampusPlaceRepository.placeOf(activity, places);
                if (place == null || !place.hasCoordinates()) {
                    listener.onProblem(Problem.NO_PLACE);
                    return;
                }
                readLocation(location -> {
                    if (location == null) {
                        listener.onProblem(Problem.NO_LOCATION);
                    } else {
                        measure(activity, place, location, listener);
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                listener.onProblem(Problem.PLACES_UNAVAILABLE);
            }
        });
    }

    private void measure(PlannedActivity activity, CampusPlace place, Location from, Listener listener) {
        // f7: first the cache, so reopening the dashboard does not measure (or call google maps) again
        Integer cached = activity.getId() == null ? null
                : Repositories.activities().getCachedTravelMinutes(activity.getId());
        if (cached != null) {
            listener.onTravel(result(activity, cached, CACHED_SOURCE, place));
            return;
        }
        int walking = PlanContext.walkingMinutes(from.getLatitude(), from.getLongitude(),
                place.getLat(), place.getLng());
        if (activity.getId() != null) {
            Repositories.activities().cacheTravelMinutes(activity.getId(), walking);
        }
        listener.onTravel(result(activity, walking, null, place));
    }

    static Result result(PlannedActivity activity, int minutes, @Nullable String source, CampusPlace place) {
        Calendar now = Calendar.getInstance(PlanContext.bogota());
        int nowMinute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        // only plans of today can make me late, the start and end come from the time text of the activity
        boolean late = PlanContext.isToday(activity.getDate(), now)
                && PlanContext.isLate(nowMinute, minutes, PlanContext.startMinute(activity.getTime()),
                PlanContext.endMinute(activity.getTime()));
        return new Result(minutes, source, place, late);
    }

    private interface LocationCallback {
        void onLocation(@Nullable Location location);
    }

    /* A fresh fix, or the last known position when there is none (indoors, GPS warming up). */
    @SuppressLint("MissingPermission")
    private void readLocation(LocationCallback callback) {
        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(context);
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, new CancellationTokenSource().getToken())
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        callback.onLocation(location);
                    } else {
                        client.getLastLocation()
                                .addOnSuccessListener(callback::onLocation)
                                .addOnFailureListener(e -> callback.onLocation(null));
                    }
                })
                .addOnFailureListener(e -> client.getLastLocation()
                        .addOnSuccessListener(callback::onLocation)
                        .addOnFailureListener(e2 -> callback.onLocation(null)));
    }

    /* The text a plan card shows for a result. */
    public static String describe(Result result) {
        return result.source != null
                ? "➜  " + result.minutes + " min to " + result.place.getName() + " · " + result.source
                : "➜  ≈ " + result.minutes + " min walking to " + result.place.getName() + " · estimate";
    }

    public static String describe(Problem problem) {
        switch (problem) {
            case NO_PERMISSION:
                return "➜  Allow location to see your travel time";
            case NO_PLACE:
                return "➜  No campus place set for this plan";
            default:
                return "➜  Travel time not available right now";
        }
    }
}
