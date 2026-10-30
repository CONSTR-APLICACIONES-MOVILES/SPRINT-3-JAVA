package com.example.parchapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

import com.example.parchapp.domain.model.SlotAcceptanceInsight;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

// tests for the bq3 result saved in a file
public class SlotAcceptanceFileTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File file() {
        return new File(folder.getRoot(), "bq3.json");
    }

// the result comes back the same
    @Test
    public void savedResultComesBack() throws IOException {
        SlotAcceptanceFile saved = new SlotAcceptanceFile(file());
        saved.save(new SlotAcceptanceInsight(2, 25.5, 27));
        SlotAcceptanceInsight insight = saved.read();
        assertEquals(2, insight.getRecommendedPosition());
        assertEquals(25.5, insight.getPercentUnchanged(), 0.001);
        assertEquals(27, insight.getSampleSize());
    }

// a result without percent keeps having no percent, it does not become 0
    @Test
    public void noPercentStaysEmpty() throws IOException {
        SlotAcceptanceFile saved = new SlotAcceptanceFile(file());
        saved.save(SlotAcceptanceInsight.withoutData(0));
        assertNull(saved.read().getPercentUnchanged());
    }

// a new result replaces the old one and does not leave the temporary file
    @Test
    public void newResultReplacesTheOld() throws IOException {
        SlotAcceptanceFile saved = new SlotAcceptanceFile(file());
        saved.save(new SlotAcceptanceInsight(1, 10.0, 20));
        saved.save(new SlotAcceptanceInsight(3, 40.0, 30));
        assertEquals(3, saved.read().getRecommendedPosition());
        assertFalse(new File(file().getPath() + ".tmp").exists());
    }

    @Test
    public void noFileGivesNothing() {
        assertNull(new SlotAcceptanceFile(file()).read());
    }

// a broken file is the same as no file, it does not close the app
    @Test
    public void brokenFileGivesNothing() throws IOException {
        try (FileOutputStream out = new FileOutputStream(file())) {
            out.write("not json".getBytes());
        }
        assertNull(new SlotAcceptanceFile(file()).read());
    }
}
