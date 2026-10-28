package com.example.parchapp;

public final class UserSession {
    private static UserProfile currentUser;

    private UserSession() {
    }

    public static void setCurrentUser(UserProfile user) {
        currentUser = user;
    }

    public static void clear() {
        currentUser = null;
    }

    public static UserProfile getCurrentUser() {
        return currentUser;
    }
}
