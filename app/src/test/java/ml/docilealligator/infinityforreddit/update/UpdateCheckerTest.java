package ml.docilealligator.infinityforreddit.update;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UpdateCheckerTest {
    @Test
    public void newerTagIsGreater() {
        assertTrue(UpdateChecker.compareVersions("v8.3.3.9.1.2", "8.3.3.9.1.1") > 0);
    }

    @Test
    public void suffixIsIgnored() {
        assertEquals(0, UpdateChecker.compareVersions("v8.3.3.9.1.1", "8.3.3.9.1.1-debug"));
    }

    @Test
    public void missingPartsCountAsZero() {
        assertEquals(0, UpdateChecker.compareVersions("8.3.3.9.1", "8.3.3.9.1.0"));
        assertTrue(UpdateChecker.compareVersions("8.3.3.10", "8.3.3.9.1.1") > 0);
    }
}
