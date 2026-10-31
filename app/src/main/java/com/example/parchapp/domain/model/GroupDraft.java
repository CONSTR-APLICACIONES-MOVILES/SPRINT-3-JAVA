package com.example.parchapp.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// what the user wrote in create group and did not send yet, it is saved on the phone
// no android code here, so the tests can run without a phone
public final class GroupDraft {
// none means the user did not pick a date or a time
    public static final long NONE = -1;

    private final String name;
    private final long pickedDate;
    private final int pickedMinute;
    private final String dateText;
    private final String timeText;
    private final List<String> emails;

    public GroupDraft(String name, long pickedDate, int pickedMinute, String dateText, String timeText,
                      List<String> emails) {
        this.name = name == null ? "" : name;
        this.pickedDate = pickedDate;
        this.pickedMinute = pickedMinute;
        this.dateText = dateText == null ? "" : dateText;
        this.timeText = timeText == null ? "" : timeText;
        this.emails = emails == null ? new ArrayList<>() : new ArrayList<>(emails);
    }

    public String getName() {
        return name;
    }

    public long getPickedDate() {
        return pickedDate;
    }

    public int getPickedMinute() {
        return pickedMinute;
    }

    public String getDateText() {
        return dateText;
    }

    public String getTimeText() {
        return timeText;
    }

    public List<String> getEmails() {
        return Collections.unmodifiableList(emails);
    }

// a draft with nothing written is not worth saving or bringing back
    public boolean isEmpty() {
        return name.trim().isEmpty() && pickedDate == NONE && pickedMinute == NONE && emails.isEmpty();
    }
}
