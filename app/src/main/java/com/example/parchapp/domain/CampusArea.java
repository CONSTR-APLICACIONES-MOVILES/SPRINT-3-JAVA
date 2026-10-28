package com.example.parchapp.domain;

import android.location.Location;

// f1: this class tells if a location is inside the campus
// the campus is a circle around the center of the university
public final class CampusArea {
// center of the campus in bogota
    private static final double CAMPUS_LAT = 4.6015;
    private static final double CAMPUS_LNG = -74.0661;
// size of the circle in meters
    private static final float CAMPUS_RADIUS_METERS = 600;

    private CampusArea() {
    }

// returns true when the distance to the center is smaller than the radius
    public static boolean isOnCampus(double lat, double lng) {
        float[] distance = new float[1];
        Location.distanceBetween(lat, lng, CAMPUS_LAT, CAMPUS_LNG, distance);
        return distance[0] <= CAMPUS_RADIUS_METERS;
    }
}
