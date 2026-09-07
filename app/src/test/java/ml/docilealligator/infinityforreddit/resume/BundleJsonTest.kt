package ml.docilealligator.infinityforreddit.resume

import android.os.Bundle
import ml.docilealligator.infinityforreddit.TestInfinity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The codec is where a resume snapshot can go quietly wrong.
 *
 * A `Bundle` is untyped once it reaches JSON, so the risk is not that a value is lost -- that would
 * be visible -- but that it comes back as the wrong type: an `Int` read as a `Long`, a `Float` as a
 * `Double`. The launch extras it encodes are compared key by key to decide which screen a snapshot
 * entry belongs to, and a type that shifts on the way through makes that comparison fail against a
 * bundle that is, to the user, identical.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = TestInfinity::class)
class BundleJsonTest {

    private fun roundTrip(bundle: Bundle, lenient: Boolean = false): Bundle {
        val json = BundleJson.toJson(bundle, lenient)
        assertNotNull("bundle was refused", json)
        return BundleJson.toBundle(json!!)
    }

    @Test
    fun `every supported type comes back as itself`() {
        val original = Bundle().apply {
            putString("s", "hello")
            putInt("i", 42)
            putLong("l", 1234567890123L)
            putBoolean("b", true)
            putFloat("f", 1.5f)
            putDouble("d", 2.25)
            putStringArrayList("sl", arrayListOf("one", "two"))
        }

        val restored = roundTrip(original)

        assertEquals("hello", restored.getString("s"))
        assertEquals(42, restored.getInt("i"))
        assertEquals(1234567890123L, restored.getLong("l"))
        assertTrue(restored.getBoolean("b"))
        assertEquals(1.5f, restored.getFloat("f"), 0f)
        assertEquals(2.25, restored.getDouble("d"), 0.0)
        assertEquals(arrayListOf("one", "two"), restored.getStringArrayList("sl"))
    }

    @Test
    fun `an int does not come back as a long`() {
        // The failure this guards: read back untyped, every JSON number is a Double, and an extra
        // written with putInt but read with getLong -- or the reverse -- reads as 0 with no error.
        val restored = roundTrip(Bundle().apply { putInt("page", 3) })

        assertEquals(3, restored.getInt("page"))
        assertEquals("a long read finds nothing under an int key", 0L, restored.getLong("page"))
    }

    @Test
    fun `a large long survives the double it would become untyped`() {
        // 2^53 + 1 is the first integer a double cannot hold, which is what an untyped decode would
        // round it to.
        val exact = 9007199254740993L
        assertEquals(exact, roundTrip(Bundle().apply { putLong("t", exact) }).getLong("t"))
    }

    @Test
    fun `an unsupported value refuses the whole bundle when strict`() {
        // What makes an activity unrelaunchable: an intent replayed without one of its extras is a
        // different intent, so the entry is refused rather than silently reduced.
        val bundle = Bundle().apply {
            putString("kept", "yes")
            putIntArray("unsupported", intArrayOf(1, 2))
        }

        assertNull(BundleJson.toJson(bundle, lenient = false))
    }

    @Test
    fun `an unsupported value is dropped when lenient`() {
        // Recorded state is advisory: a screen that cannot restore one field of it restores the
        // rest, which is better than losing its place entirely over something it did not need.
        val bundle = Bundle().apply {
            putString("kept", "yes")
            putIntArray("unsupported", intArrayOf(1, 2))
        }

        val restored = roundTrip(bundle, lenient = true)

        assertEquals("yes", restored.getString("kept"))
        assertFalse(restored.containsKey("unsupported"))
    }

    @Test
    fun `an explicitly null extra does not refuse the bundle`() {
        val bundle = Bundle().apply {
            putString("present", "yes")
            putString("absent", null)
        }

        val restored = roundTrip(bundle)

        assertEquals("yes", restored.getString("present"))
        assertFalse("a null carries nothing to restore", restored.containsKey("absent"))
    }

    @Test
    fun `an empty bundle encodes to an empty document`() {
        assertEquals(0, BundleJson.toJson(Bundle(), lenient = false)!!.length())
    }

    @Test
    fun `contents are compared regardless of key order`() {
        // The bug this exists for: a JSONObject enumerates keys in the order they were inserted, so
        // two bundles holding the same extras can serialize to different documents. Comparing those
        // documents as strings made a screen fail to claim its own recorded state.
        val one = Bundle().apply {
            putString("a", "1")
            putInt("b", 2)
        }
        val other = Bundle().apply {
            putInt("b", 2)
            putString("a", "1")
        }

        assertTrue(BundleJson.sameContents(one, other))
    }

    @Test
    fun `a differing value is not the same contents`() {
        val one = Bundle().apply { putString("subreddit", "pics") }
        val other = Bundle().apply { putString("subreddit", "aww") }

        assertFalse(BundleJson.sameContents(one, other))
    }

    @Test
    fun `an extra key is not the same contents`() {
        val one = Bundle().apply { putString("subreddit", "pics") }
        val other = Bundle().apply {
            putString("subreddit", "pics")
            putBoolean("sidebar", true)
        }

        assertFalse(BundleJson.sameContents(one, other))
    }

    @Test
    fun `a value of the wrong type is not the same contents`() {
        // Same key, same digits, different type. The tag is what separates them.
        val one = Bundle().apply { putInt("tab", 1) }
        val other = Bundle().apply { putLong("tab", 1L) }

        assertFalse(BundleJson.sameContents(one, other))
    }
}
