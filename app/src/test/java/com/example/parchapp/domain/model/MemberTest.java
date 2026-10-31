package com.example.parchapp.domain.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Collections;

// tests for the letters of a member's avatar
public class MemberTest {

    private static Member member(String name, String initials) {
        return new Member("id", name, initials, Collections.emptyList(), 0, 0);
    }

// the initials of the group are used when they exist
    @Test
    public void usesTheInitialsOfTheGroup() {
        assertEquals("TS", member("Tatiana", "TS").getDisplayInitials());
    }

// a group made by the backend can have empty initials, the avatar must not stay empty
    @Test
    public void emptyInitialsUseTheFirstLetterOfTheName() {
        assertEquals("T", member(" tatiana", "").getDisplayInitials());
        assertEquals("T", member("tatiana", null).getDisplayInitials());
    }

// without a name there is still something to show
    @Test
    public void withoutNameShowsAQuestionMark() {
        assertEquals("?", member("  ", "").getDisplayInitials());
        assertEquals("?", member(null, null).getDisplayInitials());
    }
}
