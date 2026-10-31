package com.example.parchapp.domain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// group invitations: the backend gives a code of 43 characters
// people paste the whole message they received, so the code is searched inside the text
public final class InviteCode {
    private static final Pattern CODE =
            Pattern.compile("(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{43}(?![A-Za-z0-9_-])");

    private InviteCode() {
    }

// the code inside the text, or null when there is none
    public static String find(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = CODE.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }
}
