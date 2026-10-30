package com.example.parchapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.parchapp.domain.model.AlertItem;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

// eventual connectivity: the alert list saved on the phone comes back the same when there is no network
public class OfflineAlertsRepositoryTest {

    @Test
    public void savedAlertsComeBackTheSame() {
        AlertItem invitation = new AlertItem("a1", "invitation", "Study session", "ML Building", "2026-10-29T21:01:00Z",
                false, null, "act1", "choir", 19);
        AlertItem reminder = new AlertItem("a2", "reminder", "Starts soon", "In 30 minutes", "2026-10-29T22:00:00Z",
                true, "accepted", "act1", "choir", 19, "r1", "rsvp", "fixed", 2, 30, "2026-10-29T22:00:05Z");
        List<AlertItem> back = OfflineAlertsRepository.fromJson(OfflineAlertsRepository.toJson(Arrays.asList(invitation, reminder)));

        assertEquals(2, back.size());
        assertEquals("Study session", back.get(0).getTitle());
        assertTrue(back.get(0).isActivityInvitation());
        assertFalse(back.get(0).isRead());
        assertNull(back.get(0).getResponse());
        assertNull(back.get(0).getReminderDelayMinutes());
        assertEquals("accepted", back.get(1).getResponse());
        assertEquals(Integer.valueOf(30), back.get(1).getReminderDelayMinutes());
        assertEquals(2, back.get(1).getReminderSequence());
    }

}
