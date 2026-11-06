package ml.docilealligator.infinityforreddit.customtheme

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The 47 -> 48 migration, which rebuilds the custom theme table without the two bottom app bar
 * colours. Opened through [RedditDataRoomDatabase.create], so Room runs it, and then checks the
 * rebuilt table against the entity, as an upgrading install would.
 */
@RunWith(RobolectricTestRunner::class)
class CustomThemeBarColoursMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var database: RedditDataRoomDatabase? = null

    @After
    fun tearDown() {
        database?.close()
    }

    @Test
    fun `the two bottom app bar columns go and every other colour is kept`() {
        val old = RedditDataRoomDatabase.create(context)
        old.openHelper.writableDatabase.apply {
            val columns = columnNames(this)
            execSQL(
                "INSERT INTO custom_themes (" + columns.joinToString(",") { "`$it`" } + ") VALUES (" +
                    columns.joinToString(",") {
                        when (it) {
                            "name" -> "'Mine'"
                            "color_primary" -> "1193046"
                            else -> "0"
                        }
                    } + ")"
            )
            // The table as version 47 had it.
            execSQL("ALTER TABLE custom_themes ADD COLUMN bottom_app_bar_background_color INTEGER NOT NULL DEFAULT 7")
            execSQL("ALTER TABLE custom_themes ADD COLUMN bottom_app_bar_icon_color INTEGER NOT NULL DEFAULT 8")
            version = 47
        }
        old.close()

        val upgraded = RedditDataRoomDatabase.create(context).also { database = it }

        val db = upgraded.openHelper.writableDatabase
        assertEquals(48, db.version)
        val columns = columnNames(db)
        assertFalse(columns.contains("bottom_app_bar_background_color"))
        assertFalse(columns.contains("bottom_app_bar_icon_color"))
        assertTrue(columns.contains("color_primary"))
        db.query("SELECT name, color_primary FROM custom_themes").use {
            assertTrue(it.moveToFirst())
            assertEquals("Mine", it.getString(0))
            assertEquals(1193046, it.getInt(1))
            assertEquals("one row, not a copy per column", 1, it.count)
        }
    }

    private fun columnNames(db: SupportSQLiteDatabase): List<String> =
        db.query("PRAGMA table_info(custom_themes)").use {
            val names = mutableListOf<String>()
            while (it.moveToNext()) names += it.getString(1)
            names
        }
}
