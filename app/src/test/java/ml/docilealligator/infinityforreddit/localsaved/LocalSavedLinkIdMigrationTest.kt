package ml.docilealligator.infinityforreddit.localsaved

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The 44 -> 45 migration, which gives a Local Saved comment the post it is on: the only way left to
 * fetch a comment by id once `/api/info` is not used is its focused thread, addressed by post (issue
 * #476). Opened through [RedditDataRoomDatabase.create], so Room runs it as it would on an upgrading
 * install and then checks the result against [LocalSavedThing].
 */
@RunWith(RobolectricTestRunner::class)
class LocalSavedLinkIdMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: RedditDataRoomDatabase? = null

    @After
    fun tearDown() {
        database?.close()
    }

    @Test
    fun `rows saved before the post was recorded keep everything else and have no post`() {
        val old = RedditDataRoomDatabase.create(context)
        old.openHelper.writableDatabase.apply {
            // local_saved as version 44 had it, created by MIGRATION_35_36.
            execSQL("DROP TABLE local_saved")
            execSQL("CREATE TABLE IF NOT EXISTS `local_saved` "
                + "(`username` TEXT NOT NULL, `full_name` TEXT NOT NULL, "
                + "`state` INTEGER NOT NULL, `time` INTEGER NOT NULL, "
                + "PRIMARY KEY(`username`, `full_name`), "
                + "FOREIGN KEY(`username`) REFERENCES `accounts`(`username`) "
                + "ON UPDATE NO ACTION ON DELETE CASCADE)")
            execSQL("CREATE INDEX IF NOT EXISTS `index_local_saved_username` ON `local_saved` (`username`)")
            execSQL("INSERT INTO accounts (username, karma, is_current_user, is_mod) VALUES ('alice', 0, 1, 0)")
            execSQL("INSERT INTO local_saved (username, full_name, state, time) VALUES ('alice', 't1_abc', 1, 7)")
            version = 44
        }
        old.close()

        val upgraded = RedditDataRoomDatabase.create(context).also { database = it }

        val db = upgraded.openHelper.writableDatabase
        assertEquals(47, db.version)
        db.query("SELECT full_name, state, time, link_id FROM local_saved WHERE username = 'alice'").use {
            assertEquals(1, it.count)
            it.moveToFirst()
            assertEquals("t1_abc", it.getString(0))
            assertEquals(LocalSavedState.PROMOTED, it.getInt(1))
            assertEquals(7L, it.getLong(2))
            assertTrue(it.isNull(3))
        }
    }

    @Test
    fun `a database that already has the column opens rather than failing`() {
        val current = RedditDataRoomDatabase.create(context)
        current.openHelper.writableDatabase.version = 44
        current.close()

        val reopened = RedditDataRoomDatabase.create(context).also { database = it }

        assertEquals(47, reopened.openHelper.writableDatabase.version)
    }

    @Test
    fun `a comment's post is stored and read back`() {
        val db = RedditDataRoomDatabase.createInMemoryForTest(context).also { database = it }
        db.openHelper.writableDatabase
            .execSQL("INSERT INTO accounts (username, karma, is_current_user, is_mod) VALUES ('alice', 0, 1, 0)")

        db.localSavedThingDao().insert(
            LocalSavedThing("alice", "t1_abc", LocalSavedState.PROMOTED, 7).apply { linkId = "post1" }
        )

        assertEquals("post1", db.localSavedThingDao().getPromotedCommentsSync("alice", null).single().linkId)
    }
}
