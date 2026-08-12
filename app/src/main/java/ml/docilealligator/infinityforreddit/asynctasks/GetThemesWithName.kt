package ml.docilealligator.infinityforreddit.asynctasks

import android.os.Handler
import java.util.concurrent.Executor
import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase
import ml.docilealligator.infinityforreddit.customtheme.CustomTheme

/**
 * Every theme the name reaches case-insensitively, for callers that have to tell "this theme under a
 * different capitalisation" from "a second theme whose name differs from it in case alone".
 */
object GetThemesWithName {
    @JvmStatic
    fun getThemesWithName(
        executor: Executor,
        handler: Handler,
        redditDataRoomDatabase: RedditDataRoomDatabase,
        themeName: String,
        listener: GetThemesWithNameListener,
    ) {
        executor.execute {
            val themes = redditDataRoomDatabase.customThemeDao().getCustomThemesWithName(themeName)
            handler.post { listener.success(themes) }
        }
    }

    fun interface GetThemesWithNameListener {
        fun success(themes: List<CustomTheme>)
    }
}
