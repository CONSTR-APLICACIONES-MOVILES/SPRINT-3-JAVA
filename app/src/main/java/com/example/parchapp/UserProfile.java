package com.example.parchapp;

import java.util.Locale;

/*
 * Visible Data to the User. They are created from the authenticated app or, without Firebase, from the demo data. The UserProfile is not a domain model, it is a view model for the UI.
 */
public class UserProfile {
    private final String id;
    private final String email;
    private final String firstName;
    private final String initials;
    private final String fullName;
    private final String inviteLink;
    private final int activePlans;

    public UserProfile(String id, String email, String firstName, String initials, String fullName,
                       String inviteLink, int activePlans) {
        this.id = id;
        this.email = email;
        this.firstName = firstName;
        this.initials = initials;
        this.fullName = fullName;
        this.inviteLink = inviteLink;
        this.activePlans = activePlans;
    }

    /* Builds a profile from an account the following code. Without a display name, the name comes from the email, for example {@code maria.lopez@uni.edu} becomes "Maria Lopez". */
    public static UserProfile fromAccount(String id, String email, String displayName) {
        String name = displayName;
        if (name == null || name.trim().isEmpty()) {
            String localPart = email == null ? "user" : email.split("@")[0];
            StringBuilder builder = new StringBuilder();
            for (String part : localPart.split("[._\\-]+")) {
                if (part.isEmpty()) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
            }
            name = builder.length() == 0 ? "User" : builder.toString();
        }
        String[] words = name.trim().split("\\s+");
        String initials = words[0].substring(0, 1)
                + (words.length > 1 ? words[words.length - 1].substring(0, 1) : "");
        String slug = id.length() > 8 ? id.substring(0, 8) : id;
        // TODO: ajustar segun usuario (necesitamos log in normal sin create account ese): planes activos deben venir de ActivityRepository.
        return new UserProfile(id, email, words[0], initials.toUpperCase(Locale.ROOT), name,
                "parchapp.me/u/" + slug.toLowerCase(Locale.ROOT), 2);
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getInitials() {
        return initials;
    }

    public String getFullName() {
        return fullName;
    }

    public String getInviteLink() {
        return inviteLink;
    }

    public int getActivePlans() {
        return activePlans;
    }
}
