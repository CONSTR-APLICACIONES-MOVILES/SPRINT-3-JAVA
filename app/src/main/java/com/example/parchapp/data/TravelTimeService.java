package com.example.parchapp.data;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.example.parchapp.BuildConfig;
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

/* Uses Google Maps for travel time when available, with a local walking estimate as fallback. */
/* Travel time from the user's location to a campus place, with a local walking estimate as fallback. */
public class TravelTimeService {

    public enum Problem {NO_PERMISSION, NO_LOCATION, NO_PLACE, PLACES_UNAVAILABLE}

    public static final class Result {
        public final int minutes;
        /* Cache/Maps source label, or null when this result is the local walking estimate. */
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
// f7: source shown when the minutes come from google maps
    static final String MAPS_SOURCE = "Google Maps";
// f7: one client for the whole app, so all the screens share the same background thread
    private static final DistanceMatrixClient MAPS = new DistanceMatrixClient(BuildConfig.MAPS_API_KEY);
    /* F7: a saved time is only reused while the user is still near the place where it was measured. */
    static final double CACHE_RADIUS_METERS = 300;

    private final Context context;

    public TravelTimeService(Context context) {
        this.context = context.getApplicationContext();
    }

    public boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public void travelTo(PlannedActivity activity, Listener listener) {
        Repositories.places().getPlaces(new Callback<List<CampusPlace>>() {
            @Override
            public void onSuccess(List<CampusPlace> places) {
                CampusPlace place = CampusPlaceRepository.placeOf(activity, places);
                if (place == null || !place.hasCoordinates()) {
                    listener.onProblem(Problem.NO_PLACE);
                    return;
                }
                Integer cachedForPlace = activity.getId() == null ? null
                        : Repositories.activities().getCachedTravelMinutesForDestination(
                                activity.getId(), place.getId());
                if (!hasLocationPermission()) {
                    if (cachedForPlace != null) {
                        listener.onTravel(result(activity, cachedForPlace, CACHED_SOURCE, place));
                    } else {
                        listener.onProblem(Problem.NO_PERMISSION);
                    }
                    return;
                }
                readLocation(location -> {
                    if (location == null) {
                        if (cachedForPlace != null) {
                            listener.onTravel(result(activity, cachedForPlace, CACHED_SOURCE, place));
                        } else {
                            listener.onProblem(Problem.NO_LOCATION);
                        }
                    } else {
                        Integer cachedNearby = activity.getId() == null ? null
                                : Repositories.activities().getCachedTravelMinutesNear(activity.getId(),
                                        location.getLatitude(), location.getLongitude(), place.getId(),
                                        CACHE_RADIUS_METERS);
                        if (cachedNearby != null) {
                            // Reuse only for the same campus destination and a nearby origin.
                            listener.onTravel(result(activity, cachedNearby, CACHED_SOURCE, place));
                        } else {
                            measure(activity, place, location, listener);
                        }
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                listener.onProblem(Problem.PLACES_UNAVAILABLE);
            }
        });
    }

// f7: first we ask google maps, if it fails or takes more than 8 seconds we use the walking estimate
    private void measure(PlannedActivity activity, CampusPlace place, Location from, Listener listener) {
        if (!MAPS.isConfigured()) {
            useWalkingEstimate(activity, place, from, listener);
            return;
        }
        MAPS.getTravelMinutes(from.getLatitude(), from.getLongitude(), place.getLat(), place.getLng(),
                new Callback<Integer>() {
                    @Override
                    public void onSuccess(Integer minutes) {
                        if (activity.getId() != null) {
                            Repositories.activities().cacheTravelMinutesForPlace(activity.getId(), minutes,
                                    from.getLatitude(), from.getLongitude(), place.getId());
                        }
                        listener.onTravel(result(activity, minutes, MAPS_SOURCE, place));
                    }

// f7: fallback, the card still shows a time and the user can continue
                    @Override
                    public void onError(Exception error) {
                        useWalkingEstimate(activity, place, from, listener);
                    }
                });
    }

    private void useWalkingEstimate(PlannedActivity activity, CampusPlace place, Location from, Listener listener) {
        int walking = PlanContext.walkingMinutes(from.getLatitude(), from.getLongitude(),
                place.getLat(), place.getLng());
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

    /* F7: true when the user is within CACHE_RADIUS_METERS of where the saved time was measured. */
    static boolean isNearOrigin(@Nullable double[] origin, double lat, double lng) {
        return origin != null
                && PlanContext.distanceMeters(origin[0], origin[1], lat, lng) <= CACHE_RADIUS_METERS;
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
