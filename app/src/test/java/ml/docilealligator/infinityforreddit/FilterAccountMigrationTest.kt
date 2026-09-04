package ml.docilealligator.infinityforreddit

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The 40 → 41 migration, which gives every filter an owner.
 *
 * Both filter tables were keyed by name alone, so one list of filters served every account. Getting
 * this wrong is not a crash but a silent loss of somebody's filters on upgrade, which is why it is
 * exercised against a real SQLite database rather than reasoned about.
 */
@RunWith(RobolectricTestRunner::class)
class FilterAccountMigrationTest {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val configuration = SupportSQLiteOpenHelper.Configuration
            .builder(ApplicationProvider.getApplicationContext<Context>())
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
        db = helper.writableDatabase
        createVersion40Schema()
    }

    @After
    fun tearDown() {
        helper.close()
    }

    /** The tables as version 40 had them: filters keyed by name, with no owner. */
    private fun createVersion40Schema() {
        db.execSQL("CREATE TABLE accounts (username TEXT NOT NULL PRIMARY KEY)")
        db.execSQL(
            "CREATE TABLE post_filter (name TEXT NOT NULL, max_vote INTEGER NOT NULL, " +
                "min_vote INTEGER NOT NULL, max_comments INTEGER NOT NULL, min_comments INTEGER NOT NULL, " +
                "max_awards INTEGER NOT NULL, min_awards INTEGER NOT NULL, only_nsfw INTEGER NOT NULL, " +
                "only_spoiler INTEGER NOT NULL, post_title_excludes_regex TEXT, post_title_contains_regex TEXT, " +
                "post_title_excludes_strings TEXT, post_title_contains_strings TEXT, exclude_subreddits TEXT, " +
                "contain_subreddits TEXT, exclude_users TEXT, contain_users TEXT, contain_flairs TEXT, " +
                "exclude_flairs TEXT, exclude_domains TEXT, contain_domains TEXT, contain_text_type INTEGER NOT NULL, " +
                "contain_link_type INTEGER NOT NULL, contain_image_type INTEGER NOT NULL, " +
                "contain_gif_type INTEGER NOT NULL, contain_video_type INTEGER NOT NULL, " +
                "contain_gallery_type INTEGER NOT NULL, PRIMARY KEY(name))",
        )
        db.execSQL(
            "CREATE TABLE post_filter_usage (name TEXT NOT NULL, usage INTEGER NOT NULL, " +
                "name_of_usage TEXT NOT NULL, PRIMARY KEY(name, usage, name_of_usage))",
        )
        db.execSQL(
            "CREATE TABLE post_filter_blocked_subreddit (filter_name TEXT NOT NULL, rule_value TEXT NOT NULL, " +
                "subreddit_name TEXT NOT NULL, first_blocked INTEGER NOT NULL, block_count INTEGER NOT NULL, " +
                "excepted INTEGER NOT NULL, PRIMARY KEY(filter_name, rule_value, subreddit_name))",
        )
        db.execSQL(
            "CREATE TABLE comment_filter (name TEXT NOT NULL, display_mode INTEGER NOT NULL, " +
                "max_vote INTEGER NOT NULL, min_vote INTEGER NOT NULL, exclude_strings TEXT, " +
                "exclude_users TEXT, PRIMARY KEY(name))",
        )
        db.execSQL(
            "CREATE TABLE comment_filter_usage (name TEXT NOT NULL, usage INTEGER NOT NULL, " +
                "name_of_usage TEXT NOT NULL, PRIMARY KEY(name, usage, name_of_usage))",
        )
    }

    private fun insertPostFilter(name: String) {
        db.execSQL(
            "INSERT INTO post_filter (name, max_vote, min_vote, max_comments, min_comments, max_awards, " +
                "min_awards, only_nsfw, only_spoiler, contain_text_type, contain_link_type, contain_image_type, " +
                "contain_gif_type, contain_video_type, contain_gallery_type) " +
                "VALUES (?, -1, -1, -1, -1, -1, -1, 0, 0, 1, 1, 1, 1, 1, 1)",
            arrayOf(name),
        )
    }

    private fun migrate() = RedditDataRoomDatabase.MIGRATION_40_41.migrate(db)

    private fun usernamesOf(table: String, where: String = "1"): List<String> {
        val names = mutableListOf<String>()
        db.query("SELECT username FROM $table WHERE $where ORDER BY username").use { cursor ->
            while (cursor.moveToNext()) {
                names.add(cursor.getString(0))
            }
        }
        return names
    }

    @Test
    fun `every account and anonymous take a copy of each filter`() {
        db.execSQL("INSERT INTO accounts (username) VALUES ('alice'), ('bob')")
        insertPostFilter("NSFW")

        migrate()

        assertEquals(listOf("-", "alice", "bob"), usernamesOf("post_filter", "name = 'NSFW'"))
    }

    @Test
    fun `an install that has never signed in keeps its filters under anonymous`() {
        insertPostFilter("NSFW")

        migrate()

        assertEquals(listOf("-"), usernamesOf("post_filter"))
    }

    @Test
    fun `usages follow the filter they belong to`() {
        db.execSQL("INSERT INTO accounts (username) VALUES ('alice')")
        insertPostFilter("NSFW")
        db.execSQL("INSERT INTO post_filter_usage (name, usage, name_of_usage) VALUES ('NSFW', 1, '--')")

        migrate()

        assertEquals(listOf("-", "alice"), usernamesOf("post_filter_usage", "name = 'NSFW'"))
    }

    @Test
    fun `comment filters and their usages travel the same way`() {
        db.execSQL("INSERT INTO accounts (username) VALUES ('alice')")
        db.execSQL(
            "INSERT INTO comment_filter (name, display_mode, max_vote, min_vote) VALUES ('Rude', 0, -1, -1)",
        )
        db.execSQL("INSERT INTO comment_filter_usage (name, usage, name_of_usage) VALUES ('Rude', 1, 'pics')")

        migrate()

        assertEquals(listOf("-", "alice"), usernamesOf("comment_filter", "name = 'Rude'"))
        assertEquals(listOf("-", "alice"), usernamesOf("comment_filter_usage", "name = 'Rude'"))
    }

    @Test
    fun `what a rule was seen blocking is kept, per account`() {
        db.execSQL("INSERT INTO accounts (username) VALUES ('alice')")
        insertPostFilter("NSFW")
        db.execSQL(
            "INSERT INTO post_filter_blocked_subreddit " +
                "(filter_name, rule_value, subreddit_name, first_blocked, block_count, excepted) " +
                "VALUES ('NSFW', '*irl*', 'hairloss', 1, 3, 0)",
        )

        migrate()

        assertEquals(listOf("-", "alice"), usernamesOf("post_filter_blocked_subreddit", "filter_name = 'NSFW'"))
    }

    @Test
    fun `the old tables are gone afterwards`() {
        insertPostFilter("NSFW")

        migrate()

        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name LIKE '%_old'",
        ).use { cursor ->
            assertEquals("left behind: leftovers would be copied again by a re-run", 0, cursor.count)
        }
    }
}
