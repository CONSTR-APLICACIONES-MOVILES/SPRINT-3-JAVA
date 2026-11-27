package com.example.parchapp.data;

import java.util.Locale;

// cleans what the user types before it reaches the MATCH of the full-text table, so a strange search
// (quotes, hyphens, asterisks, a lone operator) can not break the MATCH syntax
public final class HelpSearchQuery {

    private HelpSearchQuery() {
    }

    // lower case and single spaces: "  Create   GROUP " and "create group" are the same search
    public static String normalize(String text) {
        if (text == null) return "";
        return text.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    // "create group" becomes "create* group*": every word is a prefix, all of them must appear.
    // only letters and digits are kept; an empty result means there is nothing to search
    public static String toMatch(String text) {
        StringBuilder match = new StringBuilder();
        for (String word : normalize(text).split("[^\\p{L}\\p{N}]+")) {
            if (word.isEmpty()) continue;
            if (match.length() > 0) match.append(' ');
            match.append(word).append('*');
        }
        return match.toString();
    }
}
