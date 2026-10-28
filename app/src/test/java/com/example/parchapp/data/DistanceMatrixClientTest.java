package com.example.parchapp.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import org.json.JSONException;
import org.junit.Test;

// f7: tests for how we read the answer of google maps
public class DistanceMatrixClientTest {

// f7: 750 seconds are 12.5 minutes, so we show 13
    @Test
    public void readsTheMinutesFromTheAnswer() throws JSONException {
        String json = "{\"rows\":[{\"elements\":[{\"status\":\"OK\",\"duration\":{\"value\":750}}]}]}";
        assertEquals(13, DistanceMatrixClient.minutesFrom(json));
    }

// f7: a very short trip is at least 1 minute
    @Test
    public void shortTripIsAtLeastOneMinute() throws JSONException {
        String json = "{\"rows\":[{\"elements\":[{\"status\":\"OK\",\"duration\":{\"value\":10}}]}]}";
        assertEquals(1, DistanceMatrixClient.minutesFrom(json));
    }

// f7: when google has no route we throw, so the screen uses the walking estimate
    @Test
    public void noRouteThrows() {
        String json = "{\"rows\":[{\"elements\":[{\"status\":\"ZERO_RESULTS\"}]}]}";
        try {
            DistanceMatrixClient.minutesFrom(json);
            fail("expected an error when there is no route");
        } catch (JSONException expected) {
            // the backup message is used
        }
    }

// f7: without a key the client does not call google
    @Test
    public void withoutKeyIsNotConfigured() {
        assertFalse(new DistanceMatrixClient("").isConfigured());
        assertFalse(new DistanceMatrixClient(null).isConfigured());
    }
}
