package ml.docilealligator.infinityforreddit.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Pins the two helpers that replaced fixed-offset {@code substring} calls on API-supplied strings.
 * Both used to throw StringIndexOutOfBoundsException on the empty values Reddit sometimes sends
 * (issue #352), so the cases that matter here are the empty and unexpected shapes.
 */
public class FullnameAndCapitalizationTest {

    @Test
    public void idFromFullnameStripsAKnownPrefix() {
        assertEquals("abc123", Utils.idFromFullname("t3_abc123"));
        assertEquals("def456", Utils.idFromFullname("t1_def456"));
        assertEquals("ghi789", Utils.idFromFullname("t5_ghi789"));
    }

    @Test
    public void idFromFullnamePassesThroughAnythingElse() {
        assertEquals("", Utils.idFromFullname(""));
        assertEquals("", Utils.idFromFullname(null));
        assertEquals("abc123", Utils.idFromFullname("abc123"));
        assertEquals("t3_", Utils.idFromFullname("t3_"));
        assertEquals("tx_abc", Utils.idFromFullname("tx_abc"));
    }

    @Test
    public void capitalizeFirstLetterUpperCasesTheFirstCharacter() {
        assertEquals("That comment is archived", Utils.capitalizeFirstLetter("that comment is archived"));
        assertEquals("A", Utils.capitalizeFirstLetter("a"));
        assertEquals("RATELIMIT", Utils.capitalizeFirstLetter("RATELIMIT"));
    }

    @Test
    public void capitalizeFirstLetterIsNullForNothingToShow() {
        assertNull(Utils.capitalizeFirstLetter(""));
        assertNull(Utils.capitalizeFirstLetter(null));
    }
}
