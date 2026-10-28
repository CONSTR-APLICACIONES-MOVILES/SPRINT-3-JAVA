package com.example.parchapp.domain.model;

/* Someone the user shares a group with, so their busy blocks may be compared. */
public class ScheduleFriend {
    private final String id;
    private final String name;
    private final String initials;

    public ScheduleFriend(String id, String name, String initials) {
        this.id = id;
        this.name = name;
        this.initials = initials;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /* Short label for chips and lanes: the full name if it fits, otherwise the first name. */
    public String getShortName() {
        String trimmed = name.trim();
        return trimmed.length() <= 12 ? trimmed : getFirstName();
    }

    public String getFirstName() {
        String trimmed = name.trim();
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    public String getInitials() {
        return initials.isEmpty() && !name.isEmpty() ? name.substring(0, 1).toUpperCase() : initials;
    }
}
