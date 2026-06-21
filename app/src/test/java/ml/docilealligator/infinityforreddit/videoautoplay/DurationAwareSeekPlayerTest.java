package ml.docilealligator.infinityforreddit.videoautoplay;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DurationAwareSeekPlayerTest {

    @Test
    void shortVideoSeekIncrementRoundsDurationToNearestSecondBeforeCalculatingPercent() {
        assertEquals(1000, DurationAwareSeekPlayer.getShortVideoSeekIncrement(5100));
        assertEquals(1000, DurationAwareSeekPlayer.getShortVideoSeekIncrement(5400));
        assertEquals(1000, DurationAwareSeekPlayer.getShortVideoSeekIncrement(5500));
        assertEquals(1000, DurationAwareSeekPlayer.getShortVideoSeekIncrement(5900));
        assertEquals(2000, DurationAwareSeekPlayer.getShortVideoSeekIncrement(7500));
        assertEquals(2000, DurationAwareSeekPlayer.getShortVideoSeekIncrement(8400));
    }
}
