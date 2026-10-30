package com.example.parchapp.domain.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// tests for the create group draft that is saved on the phone
public class GroupDraftTest {

// the emails go in one text and come back the same and in the same order
    @Test
    public void emailsComeBackTheSame() {
        List<String> emails = Arrays.asList("ana@uni.edu", "luis@uni.edu");
        assertEquals(emails, GroupDraft.decodeEmails(GroupDraft.encodeEmails(emails)));
    }

// empty lines, spaces and repeated emails are not brought back
    @Test
    public void decodeSkipsEmptyAndRepeated() {
        assertEquals(Arrays.asList("ana@uni.edu", "luis@uni.edu"),
                GroupDraft.decodeEmails("ana@uni.edu\n\n  luis@uni.edu \nana@uni.edu"));
        assertTrue(GroupDraft.decodeEmails(null).isEmpty());
        assertTrue(GroupDraft.decodeEmails("").isEmpty());
    }

// a form with nothing written is an empty draft, any field makes it not empty
    @Test
    public void emptyOnlyWhenNothingWasWritten() {
        assertTrue(new GroupDraft("  ", GroupDraft.NONE, (int) GroupDraft.NONE, "", "",
                Collections.emptyList()).isEmpty());
        assertFalse(new GroupDraft("Study", GroupDraft.NONE, (int) GroupDraft.NONE, "", "",
                Collections.emptyList()).isEmpty());
        assertFalse(new GroupDraft("", 1000L, (int) GroupDraft.NONE, "Oct 30", "",
                Collections.emptyList()).isEmpty());
        assertFalse(new GroupDraft("", GroupDraft.NONE, (int) GroupDraft.NONE, "", "",
                Collections.singletonList("ana@uni.edu")).isEmpty());
    }

// null values become empty values, so the screen never gets a null
    @Test
    public void nullValuesBecomeEmpty() {
        GroupDraft draft = new GroupDraft(null, GroupDraft.NONE, (int) GroupDraft.NONE, null, null, null);
        assertEquals("", draft.getName());
        assertEquals("", draft.getDateText());
        assertTrue(draft.getEmails().isEmpty());
    }
}
