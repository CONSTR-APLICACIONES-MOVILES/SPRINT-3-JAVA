package com.example.parchapp.domain;

// rules of the create group form, they are the same rules the backend uses when it creates a group
public final class GroupForm {
// the backend accepts names between 2 and 80 characters
    public static final int MIN_NAME_LENGTH = 2;
    public static final int MAX_NAME_LENGTH = 80;
// the firestore rules accept 50 pending invites, we stop before that
    public static final int MAX_INVITES = 30;
    public static final int MAX_EMAIL_LENGTH = 100;

    private GroupForm() {
    }

// one line with single spaces and no spaces at the start or the end
    public static String cleanName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ");
    }

// a good name has the right length and at least 2 letters or numbers, so a name like !! is not accepted
    public static boolean isValidName(String name) {
        String clean = cleanName(name);
        if (clean.length() < MIN_NAME_LENGTH || clean.length() > MAX_NAME_LENGTH) {
            return false;
        }
        int lettersOrNumbers = 0;
        for (int i = 0; i < clean.length(); ) {
            int c = clean.codePointAt(i);
            if (Character.isISOControl(c)) {
                return false;
            }
            if (Character.isLetterOrDigit(c)) {
                lettersOrNumbers++;
            }
            i += Character.charCount(c);
        }
        return lettersOrNumbers >= MIN_NAME_LENGTH;
    }

// emails are saved in lower case without spaces so the same email is not added two times
    public static String cleanEmail(String email) {
        return email == null ? "" : email.replaceAll("\\s+", "").toLowerCase();
    }
}
