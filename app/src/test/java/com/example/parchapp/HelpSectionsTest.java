package com.example.parchapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.parchapp.data.HelpFaqEntity;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// the help shows the screen the user came from first and "general" last
public class HelpSectionsTest {

    private static HelpFaqEntity faq(String screen, String question, int position) {
        HelpFaqEntity row = new HelpFaqEntity();
        row.questionId = screen + "-" + position;
        row.screen = screen;
        row.question = question;
        row.answer = "answer";
        row.position = position;
        return row;
    }

    private static List<String> screens(List<HelpSection> sections) {
        List<String> names = new ArrayList<>();
        for (HelpSection section : sections) names.add(section.screen);
        return names;
    }

    @Test
    public void originScreenGoesFirst() {
        List<HelpSection> sections = HelpSections.originFirst(Arrays.asList(
                faq("groups", "a", 1), faq("schedule", "b", 1), faq("chat", "c", 1)), "schedule");
        assertEquals(Arrays.asList("schedule", "groups", "chat"), screens(sections));
        assertTrue(sections.get(0).origin);
        assertFalse(sections.get(1).origin);
    }

    @Test
    public void generalGoesLastUnlessItIsTheOrigin() {
        List<HelpFaqEntity> rows = Arrays.asList(faq("general", "a", 1), faq("groups", "b", 1), faq("chat", "c", 1));
        assertEquals(Arrays.asList("groups", "chat", "general"), screens(HelpSections.originFirst(rows, "groups")));
        assertEquals(Arrays.asList("general", "groups", "chat"), screens(HelpSections.originFirst(rows, "general")));
    }

    @Test
    public void questionsOfASectionFollowTheirPosition() {
        List<HelpSection> sections = HelpSections.originFirst(Arrays.asList(
                faq("groups", "second", 2), faq("groups", "first", 1), faq("groups", "third", 3)), "groups");
        assertEquals(1, sections.size());
        assertEquals("first", sections.get(0).questions.get(0).question);
        assertEquals("third", sections.get(0).questions.get(2).question);
    }

    @Test
    public void unknownOriginMarksNoSection() {
        List<HelpSection> sections = HelpSections.originFirst(Arrays.asList(
                faq("groups", "a", 1), faq("chat", "b", 1)), "nowhere");
        for (HelpSection section : sections) assertFalse(section.origin);
        assertEquals(Arrays.asList("groups", "chat"), screens(sections));
    }

    @Test
    public void noQuestionsMeansNoSections() {
        assertTrue(HelpSections.originFirst(Collections.<HelpFaqEntity>emptyList(), "groups").isEmpty());
    }

    @Test
    public void titlesAreReadable() {
        assertEquals("Groups", HelpSections.titleOf("groups"));
        assertEquals("Andes Events", HelpSections.titleOf("andes"));
        assertEquals("Newscreen", HelpSections.titleOf("newscreen"));
    }
}
