package com.example.parchapp;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class UserProfileTest {

    @Test
    public void nameComesFromEmailWhenAccountHasNoDisplayName() {
        UserProfile user = UserProfile.fromAccount("AbCdEfGhIjK", "maria.lopez@uni.edu", null);

        assertEquals("Maria Lopez", user.getFullName());
        assertEquals("Maria", user.getFirstName());
        assertEquals("ML", user.getInitials());
        assertEquals("parchapp.me/u/abcdefgh", user.getInviteLink());
    }

    @Test
    public void displayNameWins() {
        UserProfile user = UserProfile.fromAccount("u1", "x@uni.edu", "Juan David Guzman");

        assertEquals("Juan", user.getFirstName());
        assertEquals("JG", user.getInitials());
    }
}
