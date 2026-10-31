package com.example.parchapp.data;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.example.parchapp.domain.model.Group;

import org.junit.Test;

import java.util.Collections;

// tests for the groups kept in memory
public class GroupMemoryCacheTest {

    private final GroupMemoryCache cache = new GroupMemoryCache();

    private static Group group(String id) {
        return new Group(id, "Group " + id, "", "", Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
    }

// a saved group comes back the same
    @Test
    public void savedGroupComesBack() {
        Group group = group("a");
        cache.put("me", group);
        assertSame(group, cache.get("me", "a"));
    }

// another account on the phone never sees it
    @Test
    public void groupsAreKeptPerAccount() {
        cache.put("me", group("a"));
        assertNull(cache.get("other", "a"));
    }

// when it is full the group not used for the longest time goes first, using a group keeps it
    @Test
    public void leastRecentlyUsedGroupGoesFirst() {
        for (int i = 0; i < GroupMemoryCache.MAX_GROUPS; i++) {
            cache.put("me", group("g" + i));
        }
        cache.get("me", "g0");
        cache.put("me", group("new"));
        assertNotNull(cache.get("me", "g0"));
        assertNull(cache.get("me", "g1"));
    }

// a group that is not available anymore must not be drawn again
    @Test
    public void removedGroupIsGone() {
        cache.put("me", group("a"));
        cache.remove("me", "a");
        assertNull(cache.get("me", "a"));
    }
}
