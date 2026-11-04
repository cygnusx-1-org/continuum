package ml.docilealligator.infinityforreddit.postfilter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The 46 -> 47 migration, which moves saved post filters from the all-feed's old name to its new
 * one. Opened through [RedditDataRoomDatabase.create], so Room runs it as an upgrading install
 * would.
 */
@RunWith(RobolectricTestRunner::class)
class AllFeedRenameMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: RedditDataRoomDatabase? = null

    @After
    fun tearDown() {
        database?.close()
    }

    @Test
    fun `a filter on the old all-feed name is moved to the new name and other feeds are left alone`() {
        val old = RedditDataRoomDatabase.create(context)
        old.openHelper.writableDatabase.apply {
            execSQL("INSERT INTO accounts (username, karma, is_current_user, is_mod) VALUES ('alice', 0, 1, 0)")
            insertFilter()
            execSQL("INSERT INTO post_filter_usage (name, username, usage, name_of_usage)"
                + " VALUES ('f', 'alice', 2, 'ContinuumAll')")
            execSQL("INSERT INTO post_filter_usage (name, username, usage, name_of_usage)"
                + " VALUES ('f', 'alice', 2, 'pics')")
            // Not a subreddit usage, so not this feed even if it happens to share the name.
            execSQL("INSERT INTO post_filter_usage (name, username, usage, name_of_usage)"
                + " VALUES ('f', 'alice', 3, 'ContinuumAll')")
            version = 46
        }
        old.close()

        val upgraded = RedditDataRoomDatabase.create(context).also { database = it }

        val db = upgraded.openHelper.writableDatabase
        assertEquals(47, db.version)
        assertEquals(listOf("2:RedditJrAll", "2:pics", "3:ContinuumAll"), usages(db))
    }

    @Test
    fun `a filter that already names both keeps one row under the new name`() {
        val old = RedditDataRoomDatabase.create(context)
        old.openHelper.writableDatabase.apply {
            execSQL("INSERT INTO accounts (username, karma, is_current_user, is_mod) VALUES ('alice', 0, 1, 0)")
            insertFilter()
            execSQL("INSERT INTO post_filter_usage (name, username, usage, name_of_usage)"
                + " VALUES ('f', 'alice', 2, 'ContinuumAll')")
            execSQL("INSERT INTO post_filter_usage (name, username, usage, name_of_usage)"
                + " VALUES ('f', 'alice', 2, 'RedditJrAll')")
            version = 46
        }
        old.close()

        val upgraded = RedditDataRoomDatabase.create(context).also { database = it }

        assertEquals(listOf("2:RedditJrAll"), usages(upgraded.openHelper.writableDatabase))
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.insertFilter() = execSQL(
        "INSERT INTO post_filter (username, name, max_vote, min_vote, max_comments, min_comments,"
            + " max_awards, min_awards, only_nsfw, only_spoiler, contain_text_type, contain_link_type,"
            + " contain_image_type, contain_gif_type, contain_video_type, contain_gallery_type)"
            + " VALUES ('alice', 'f', -1, -1, -1, -1, -1, -1, 0, 0, 1, 1, 1, 1, 1, 1)")

    private fun usages(db: androidx.sqlite.db.SupportSQLiteDatabase): List<String> =
        db.query("SELECT usage, name_of_usage FROM post_filter_usage ORDER BY usage, name_of_usage").use {
            val rows = mutableListOf<String>()
            while (it.moveToNext()) rows += "${it.getInt(0)}:${it.getString(1)}"
            rows
        }
}
