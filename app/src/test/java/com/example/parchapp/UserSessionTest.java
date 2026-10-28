package com.example.parchapp;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.After;
import org.junit.Test;

public class UserSessionTest {
    @After
    public void clearSessionAfterTest() {
        UserSession.clear();
    }

    @Test
    public void clearRemovesTheCurrentUser() {
        UserProfile user = UserProfile.fromAccount("uid-1", "david@example.edu", "David Perez");
        UserSession.setCurrentUser(user);

        UserSession.clear();

        assertNull(UserSession.getCurrentUser());
    }

    @Test
    public void setCurrentUserRetainsTheAuthenticatedProfile() {
        UserProfile user = UserProfile.fromAccount("uid-2", "david@example.edu", "David Perez");

        UserSession.setCurrentUser(user);

        assertSame(user, UserSession.getCurrentUser());
    }
}
