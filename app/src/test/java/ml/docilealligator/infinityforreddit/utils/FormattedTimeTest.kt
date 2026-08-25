package ml.docilealligator.infinityforreddit.utils

import ml.docilealligator.infinityforreddit.TestInfinity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import java.util.TimeZone

/**
 * Reddit timestamps arrive as a UTC epoch and are shown as a wall-clock time, so
 * [Utils.getFormattedTime] has to render them on the reader's own clock: a post made at 22:13 UTC
 * reads as the evening before in New York and the next morning in Tokyo. Nothing else in the suite
 * moves the JVM's timezone, so a formatter pinned to a fixed zone would look correct everywhere
 * except on a device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestInfinity::class)
class FormattedTimeTest {

    /** 2023-11-14T22:13:20Z. */
    private val postedAt = 1_700_000_000_000L

    private lateinit var defaultZone: TimeZone

    @Before
    fun setUp() {
        // Captured first: one JVM runs the whole suite, and an escaped timezone is contagious.
        defaultZone = TimeZone.getDefault()
    }

    @After
    fun tearDown() = TimeZone.setDefault(defaultZone)

    @Test
    fun `a post's time is shown on the reader's own clock`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))

        assertEquals("2023-11-15 07:13", Utils.getFormattedTime(Locale.US, postedAt, "yyyy-MM-dd HH:mm"))
    }

    @Test
    fun `the same moment reads as a different day for a reader further west`() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))

        assertEquals("2023-11-14 17:13", Utils.getFormattedTime(Locale.US, postedAt, "yyyy-MM-dd HH:mm"))
    }
}
