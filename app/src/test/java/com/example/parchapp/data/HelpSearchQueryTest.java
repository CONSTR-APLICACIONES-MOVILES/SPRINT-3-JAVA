package com.example.parchapp.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

// local storage: what the user types must never break the MATCH of the full-text table
public class HelpSearchQueryTest {

    @Test
    public void normalizeLowersCaseAndCollapsesSpaces() {
        assertEquals("create group", HelpSearchQuery.normalize("  Create   GROUP "));
    }

    @Test
    public void normalizeOfNullIsEmpty() {
        assertEquals("", HelpSearchQuery.normalize(null));
    }

    @Test
    public void everyWordBecomesAPrefix() {
        assertEquals("create* group*", HelpSearchQuery.toMatch("create group"));
    }

    @Test
    public void quotesHyphensAndAsterisksAreRemoved() {
        assertEquals("co* op*", HelpSearchQuery.toMatch("\"co-op*\""));
        assertEquals("chat*", HelpSearchQuery.toMatch("***chat***"));
    }

    @Test
    public void onlySymbolsMeansNothingToSearch() {
        assertEquals("", HelpSearchQuery.toMatch("\"-*\" ()"));
        assertEquals("", HelpSearchQuery.toMatch("   "));
    }

    @Test
    public void accentedLettersAndDigitsAreKept() {
        assertEquals("calendario* 2026*", HelpSearchQuery.toMatch("Calendario, 2026!"));
    }

    @Test
    public void operatorWordsAreSearchedAsPlainWords() {
        assertEquals("or* not*", HelpSearchQuery.toMatch("OR NOT"));
    }
}
