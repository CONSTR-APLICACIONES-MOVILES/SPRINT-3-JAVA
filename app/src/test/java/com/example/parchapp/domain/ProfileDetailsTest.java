package com.example.parchapp.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.parchapp.domain.model.ProfileDetails;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public class ProfileDetailsTest {
    @Test
    public void mapsServerProfileFieldsWithoutInventingAttendanceData() {
        Map<String, Object> preferences = new HashMap<>();
        preferences.put("statusVisibility", "closeFriends");
        Map<String, Object> data = new HashMap<>();
        data.put("id", "user-1");
        data.put("name", "Ada Lovelace");
        data.put("program", "Computer Science");
        data.put("verified", true);
        data.put("semester", 4);
        data.put("year", 2026);
        data.put("academicLevel", "undergraduate");
        data.put("campus", "Main campus");
        data.put("university", "University");
        data.put("studentId", "12345");
        data.put("status", "focused");
        data.put("statusExpiresAt", "2026-10-30T14:00:00.000Z");
        data.put("preferences", preferences);
        data.put("plansCompleted", 7);
        data.put("activeGroups", 3);

        ProfileDetails profile = ProfileDetails.fromMap(data, 100L);

        assertEquals("user-1", profile.getUserId());
        assertEquals("Ada Lovelace", profile.getName());
        assertEquals(4, profile.getSemester());
        assertEquals(2026, profile.getYear());
        assertEquals("closeFriends", profile.getStatusVisibility());
        assertEquals(7, profile.getPlansCompleted());
        assertEquals(3, profile.getActiveGroups());
        assertNull(profile.getOnTimeRate());
        assertFalse(profile.isFromCache());
    }

    @Test
    public void expiredServerStatusIsDisplayedAsOfflineAndCacheKeepsFreshness() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", "user-1");
        data.put("status", "focused");
        data.put("statusExpiresAt", "2020-01-01T00:00:00.000Z");
        ProfileDetails live = ProfileDetails.fromMap(data, 100L);
        ProfileDetails cached = live.asCached(100L);

        assertEquals("offline", cached.getEffectiveStatus());
        assertEquals("Offline", cached.getStatusLabel());
        assertTrue(cached.isFromCache());
        assertEquals(100L, cached.getSavedAtMillis());
    }
}
