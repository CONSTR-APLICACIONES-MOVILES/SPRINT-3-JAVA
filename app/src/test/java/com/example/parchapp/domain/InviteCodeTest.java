package com.example.parchapp.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

// tests for reading the invitation code from what the user pastes
public class InviteCodeTest {

    private static final String CODE = "Ab3_-Ab3_-Ab3_-Ab3_-Ab3_-Ab3_-Ab3_-Ab3_-Ab3";

// only the code works
    @Test
    public void findsTheCodeAlone() {
        assertEquals(CODE, InviteCode.find(CODE));
    }

// the friend usually pastes the whole message
    @Test
    public void findsTheCodeInsideTheMessage() {
        assertEquals(CODE, InviteCode.find("Join my group on ParchApp.\nCode: " + CODE + "\nValid for 7 days."));
    }

// spaces from copying do not matter
    @Test
    public void ignoresSpacesAround() {
        assertEquals(CODE, InviteCode.find("  " + CODE + "\n"));
    }

// a shorter or longer text is not a code
    @Test
    public void wrongLengthIsNotACode() {
        assertNull(InviteCode.find(CODE.substring(1)));
        assertNull(InviteCode.find(CODE + "A"));
    }

    @Test
    public void emptyTextIsNotACode() {
        assertNull(InviteCode.find(""));
        assertNull(InviteCode.find(null));
    }
}
