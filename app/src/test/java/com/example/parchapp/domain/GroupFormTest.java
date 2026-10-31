package com.example.parchapp.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

// tests for the rules of the create group form
public class GroupFormTest {

// spaces at the ends go away and double spaces become one
    @Test
    public void cleanNameUsesSingleSpaces() {
        assertEquals("Bowling Night", GroupForm.cleanName("  Bowling   Night "));
        assertEquals("", GroupForm.cleanName(null));
    }

// names need 2 letters or numbers and 80 characters or less
    @Test
    public void validNames() {
        assertTrue(GroupForm.isValidName("ab"));
        assertTrue(GroupForm.isValidName("Café ñu"));
        assertTrue(GroupForm.isValidName("Study 2"));
        assertFalse(GroupForm.isValidName("!!"));
        assertFalse(GroupForm.isValidName("   "));
        assertFalse(GroupForm.isValidName("a!"));
        assertFalse(GroupForm.isValidName("bad\u0000name"));
        assertFalse(GroupForm.isValidName(new String(new char[81]).replace('\0', 'x')));
        assertTrue(GroupForm.isValidName(new String(new char[80]).replace('\0', 'x')));
    }

// emails lose their spaces and use lower case
    @Test
    public void cleanEmailRemovesSpaces() {
        assertEquals("ana@uni.edu", GroupForm.cleanEmail(" Ana @Uni.edu "));
    }
}
