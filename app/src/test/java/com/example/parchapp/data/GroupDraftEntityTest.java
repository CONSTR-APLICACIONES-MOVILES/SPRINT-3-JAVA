package com.example.parchapp.data;

import static org.junit.Assert.assertEquals;

import com.example.parchapp.domain.model.GroupDraft;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

// tests for the draft rows of room, the queries themselves run in the real database of the app
public class GroupDraftEntityTest {

// the draft goes to its row and comes back the same
    @Test
    public void draftComesBackTheSame() {
        List<String> emails = Arrays.asList("ana@uni.edu", "luis@uni.edu");
        GroupDraft draft = new GroupDraft("Study", 1000L, 90, "Oct 30", "1:30 AM", emails);
        GroupDraftEntity entity = GroupDraftEntity.from("me", draft);
        GroupDraft back = entity.toDraft(emails);
        assertEquals("me", entity.owner);
        assertEquals("Study", back.getName());
        assertEquals(1000L, back.getPickedDate());
        assertEquals(90, back.getPickedMinute());
        assertEquals("Oct 30", back.getDateText());
        assertEquals("1:30 AM", back.getTimeText());
        assertEquals(emails, back.getEmails());
    }

// each email has its own row and keeps its place in the form
    @Test
    public void invitesKeepTheOrderOfTheForm() {
        List<DraftInviteEntity> invites = DraftInviteEntity.listOf("me",
                Arrays.asList("ana@uni.edu", "luis@uni.edu"));
        assertEquals(2, invites.size());
        assertEquals("me", invites.get(1).owner);
        assertEquals(1, invites.get(1).position);
        assertEquals("luis@uni.edu", invites.get(1).email);
    }
}
