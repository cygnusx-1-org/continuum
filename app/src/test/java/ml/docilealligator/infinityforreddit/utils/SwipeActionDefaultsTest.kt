package ml.docilealligator.infinityforreddit.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.XmlRes
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.account.AccountScope
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

/**
 * Which side each vote lands on before the user has chosen anything, and the three ways that
 * answer is arrived at agreeing with one another.
 *
 * This app has always drawn **downvote on the left and upvote on the right**. That came out of
 * the old gesture naming: `swipe_left_action` meant the action a swipe *to the left* ran, and a
 * swipe to the left drags the row left and bares its *right* edge -- so `swipe_left_action`'s
 * default of upvote put upvote on the right. The keys name the side now, which flips what every
 * value has to say to keep drawing the same picture.
 *
 * Three places have to give that same picture, and the bug these tests exist for is any two of
 * them disagreeing:
 *   - the code fallback, which is what an install with nothing stored actually swipes by;
 *   - the `app:defaultValue` in the preference XML, which is what the picker *shows* that install;
 *   - [SwipeActionSideMigration], which is where an install that *did* store the old values ends up.
 *
 * The first two disagreed once already: the stored values were turned over and the defaults were
 * left reading in the old sense, so anyone who had never opened the screen -- which on comments is
 * nearly everyone -- had their votes swap sides while the settings screen claimed otherwise.
 */
@RunWith(RobolectricTestRunner::class)
class SwipeActionDefaultsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var preferences: SharedPreferences

    @Before
    fun setUp() {
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.edit().clear().commit()
    }

    private val postUpvote = SharedPreferencesUtils.SWIPE_ACITON_UPVOTE
    private val postDownvote = SharedPreferencesUtils.SWIPE_ACITON_DOWNVOTE
    private val commentUpvote = SharedPreferencesUtils.COMMENT_SWIPE_ACITON_UPVOTE
    private val commentDownvote = SharedPreferencesUtils.COMMENT_SWIPE_ACITON_DOWNVOTE

    // ---------------------------------------------------------------- the defaults themselves

    @Test
    fun `a post swipe defaults to downvote on the left and upvote on the right`() {
        assertEquals(postDownvote, SwipeActionPreferences.postLeftLevels(preferences)[0])
        assertEquals(postUpvote, SwipeActionPreferences.postRightLevels(preferences)[0])
    }

    @Test
    fun `a comment swipe defaults to the same sides as a post`() {
        // Same picture, and the constants happen to be the same two numbers -- but they are
        // separate namespaces, so this is asserted through each surface's own.
        assertEquals(commentDownvote, SwipeActionPreferences.commentLeftLevels(preferences)[0])
        assertEquals(commentUpvote, SwipeActionPreferences.commentRightLevels(preferences)[0])
    }

    @Test
    fun `only the first level of a side is filled in by default`() {
        // The ladder stays invisible until someone fills a slot; that is what makes a fresh
        // install a plain one-step swipe.
        for (levels in listOf(
            SwipeActionPreferences.postLeftLevels(preferences),
            SwipeActionPreferences.postRightLevels(preferences),
            SwipeActionPreferences.commentLeftLevels(preferences),
            SwipeActionPreferences.commentRightLevels(preferences),
        )) {
            assertEquals("every level past the first",
                List(levels.size - 1) { SwipeActionLevels.NONE }, levels.drop(1))
        }
    }

    // ------------------------------------------------- the picker agreeing with the swipe

    @Test
    fun `the post screen shows the side each vote will really be on`() {
        assertEquals(
            mapOf(
                SharedPreferencesUtils.SWIPE_LEFT_ACTION to "1",
                SharedPreferencesUtils.SWIPE_RIGHT_ACTION to "0",
            ),
            level1DefaultsIn(
                R.xml.post_swipe_action_preferences,
                SharedPreferencesUtils.SWIPE_LEFT_ACTION,
                SharedPreferencesUtils.SWIPE_RIGHT_ACTION,
            ),
        )
    }

    @Test
    fun `the comment screen shows the side each vote will really be on`() {
        assertEquals(
            mapOf(
                SharedPreferencesUtils.COMMENT_SWIPE_LEFT_ACTION to "1",
                SharedPreferencesUtils.COMMENT_SWIPE_RIGHT_ACTION to "0",
            ),
            level1DefaultsIn(
                R.xml.comment_swipe_action_preferences,
                SharedPreferencesUtils.COMMENT_SWIPE_LEFT_ACTION,
                SharedPreferencesUtils.COMMENT_SWIPE_RIGHT_ACTION,
            ),
        )
    }

    @Test
    fun `every level-1 default in the XML is the one the code falls back to`() {
        // The literal expectations above say what the picture is; this says the two sources can
        // never drift apart, whatever it is changed to.
        val declared = level1DefaultsIn(
            R.xml.post_swipe_action_preferences,
            SharedPreferencesUtils.SWIPE_LEFT_ACTION,
            SharedPreferencesUtils.SWIPE_RIGHT_ACTION,
        ) + level1DefaultsIn(
            R.xml.comment_swipe_action_preferences,
            SharedPreferencesUtils.COMMENT_SWIPE_LEFT_ACTION,
            SharedPreferencesUtils.COMMENT_SWIPE_RIGHT_ACTION,
        )

        assertEquals(4, declared.size)
        for ((key, xmlDefault) in declared) {
            assertEquals("app:defaultValue for $key", SwipeActionPreferences.defaultLevel1(key), xmlDefault)
        }
    }

    // ------------------------------------------- an upgrade landing where a fresh install is

    @Test
    fun `an install that stored the old defaults ends up where a fresh one starts`() {
        // The whole point of the swap. This file is what an install looked like with the old
        // gesture-named keys at their shipped values, for a signed-in account and for anonymous.
        for (namespace in listOf("alice", null)) {
            preferences.edit()
                .putString(AccountScope.key(namespace, SharedPreferencesUtils.SWIPE_LEFT_ACTION), "0")
                .putString(AccountScope.key(namespace, SharedPreferencesUtils.SWIPE_RIGHT_ACTION), "1")
                .putString(AccountScope.key(namespace, SharedPreferencesUtils.COMMENT_SWIPE_LEFT_ACTION), "0")
                .putString(AccountScope.key(namespace, SharedPreferencesUtils.COMMENT_SWIPE_RIGHT_ACTION), "1")
                .commit()
        }

        SwipeActionSideMigration.migrate(preferences)

        for (namespace in listOf("alice", null)) {
            assertEquals("post left side, $namespace",
                SwipeActionPreferences.defaultLevel1(SharedPreferencesUtils.SWIPE_LEFT_ACTION),
                preferences.getString(AccountScope.key(namespace, SharedPreferencesUtils.SWIPE_LEFT_ACTION), null))
            assertEquals("post right side, $namespace",
                SwipeActionPreferences.defaultLevel1(SharedPreferencesUtils.SWIPE_RIGHT_ACTION),
                preferences.getString(AccountScope.key(namespace, SharedPreferencesUtils.SWIPE_RIGHT_ACTION), null))
            assertEquals("comment left side, $namespace",
                SwipeActionPreferences.defaultLevel1(SharedPreferencesUtils.COMMENT_SWIPE_LEFT_ACTION),
                preferences.getString(AccountScope.key(namespace, SharedPreferencesUtils.COMMENT_SWIPE_LEFT_ACTION), null))
            assertEquals("comment right side, $namespace",
                SwipeActionPreferences.defaultLevel1(SharedPreferencesUtils.COMMENT_SWIPE_RIGHT_ACTION),
                preferences.getString(AccountScope.key(namespace, SharedPreferencesUtils.COMMENT_SWIPE_RIGHT_ACTION), null))
        }
    }

    @Test
    fun `an install that never opened the screen is not moved by the migration`() {
        // Nothing stored means nothing to turn over, so this install swipes by the defaults --
        // and they have to already be the migrated picture, or its votes change sides on upgrade
        // while everyone else's stay put. This is the case that went wrong.
        SwipeActionSideMigration.migrate(preferences)

        assertEquals(postDownvote, SwipeActionPreferences.postLeftLevels(preferences)[0])
        assertEquals(postUpvote, SwipeActionPreferences.postRightLevels(preferences)[0])
        assertEquals(commentDownvote, SwipeActionPreferences.commentLeftLevels(preferences)[0])
        assertEquals(commentUpvote, SwipeActionPreferences.commentRightLevels(preferences)[0])
    }

    // ------------------------------------------------ comments seeding from the post screen

    @Test
    fun `comments start on the post screen's choice, side for side`() {
        // Comments used to read the post keys outright, so a comment side with nothing of its own
        // follows the post side of the same name rather than resetting to the default.
        preferences.edit()
            .putString(SharedPreferencesUtils.SWIPE_LEFT_ACTION, "0")
            .putString(SharedPreferencesUtils.SWIPE_RIGHT_ACTION, "1")
            .commit()

        assertEquals(commentUpvote, SwipeActionPreferences.commentLeftLevels(preferences)[0])
        assertEquals(commentDownvote, SwipeActionPreferences.commentRightLevels(preferences)[0])
    }

    @Test
    fun `a post action comments do not have falls back to this side's default`() {
        // Hide is 3 on the post list and Reply on the comment list. Carrying the number across
        // would bind a comment swipe to an action the user never picked.
        preferences.edit()
            .putString(SharedPreferencesUtils.SWIPE_LEFT_ACTION,
                SharedPreferencesUtils.SWIPE_ACITON_HIDE.toString())
            .commit()

        assertEquals(commentDownvote, SwipeActionPreferences.commentLeftLevels(preferences)[0])
    }

    // -------------------------------------------------------------------------------- helpers

    /** The `app:defaultValue` each of [keys] declares on the preference screen [xmlResId]. */
    private fun level1DefaultsIn(@XmlRes xmlResId: Int, vararg keys: String): Map<String, String> {
        val wanted = keys.toSet()
        val found = mutableMapOf<String, String>()
        context.resources.getXml(xmlResId).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                var key: String? = null
                var default: String? = null
                for (i in 0 until parser.attributeCount) {
                    when (parser.getAttributeName(i)) {
                        "key" -> key = parser.getAttributeValue(i)
                        "defaultValue" -> default = parser.getAttributeValue(i)
                    }
                }
                if (key != null && default != null && key in wanted) {
                    found[key] = default
                }
            }
        }
        return found
    }
}
