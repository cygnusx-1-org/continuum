package ml.docilealligator.infinityforreddit.markdown.commentface

import androidx.lifecycle.LifecycleOwner
import com.bumptech.glide.Glide
import ml.docilealligator.infinityforreddit.Infinity
import ml.docilealligator.infinityforreddit.activities.BaseActivity
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import ml.docilealligator.infinityforreddit.utils.Utils

/**
 * Comment faces for one screen: the [plugin] its Markwon renders them with, and the [lookup] its
 * inline parser turns links into them with, both governed by the screen's Embedded Media Type.
 *
 * Faces load lazily. The first link that could be one in a subreddit whose stylesheet is not loaded
 * yet requests it and stays an ordinary link for now; when the stylesheet arrives the screen hears
 * about it through [observe] and rebinds the markdown that may contain faces. Screens that know
 * their subreddit up front call [prefetch] so the stylesheet is usually there before the markdown.
 *
 * A screen keeps its instance for as long as it shows markdown: the repository holds the listener
 * weakly, and this object is what holds it strongly.
 */
class CommentFaces private constructor(
    private val repository: CommentFaceRepository?,
    private val canShowCommentFace: Boolean,
    private var dataSavingMode: Boolean,
    private val disableImagePreview: Boolean,
    val plugin: CommentFacePlugin,
) {
    private val enabled: Boolean
        get() = canShowCommentFace && !(dataSavingMode && disableImagePreview)

    /** Only held, never read: it is what keeps the weakly registered listener alive. */
    private var listener: CommentFaceRepository.Listener? = null

    /**
     * Resolves links in markdown from [subreddit], rendered where [context] says; null when faces
     * are off here or there is no subreddit to take a stylesheet from.
     */
    fun lookup(subreddit: String?, context: CommentFaceContext): CommentFaceLookup? {
        val repository = repository ?: return null
        if (!enabled) {
            return null
        }
        val key = CommentFaceRepository.normalize(subreddit) ?: return null
        return CommentFaceLookup { destination, title, hasText ->
            // Faces are relative links; a full URL is never one.
            if (!destination.startsWith("#") && !destination.startsWith("/")) {
                return@CommentFaceLookup null
            }
            val stylesheet = repository.peek(key)
            if (stylesheet == null) {
                // `[](/r/foo)`-style faces are empty; a `/r/foo` link with text is just a link and
                // is no reason to download a stylesheet.
                if (destination.startsWith("#") || !hasText) {
                    repository.request(key)
                }
                null
            } else {
                stylesheet.resolve(destination, title, context)
            }
        }
    }

    /** Starts loading [subreddit]'s stylesheet before any markdown from it is parsed. */
    fun prefetch(subreddit: String?) {
        val repository = repository ?: return
        if (!enabled || dataSavingMode) {
            return
        }
        CommentFaceRepository.normalize(subreddit)?.let { repository.request(it) }
    }

    /**
     * Calls [listener] with each subreddit whose faces change, until [owner] is destroyed or this
     * object is no longer reachable. One listener per instance; a second call replaces the first.
     */
    fun observe(owner: LifecycleOwner, listener: CommentFaceRepository.Listener) {
        this.listener = listener
        repository?.observe(owner, listener)
    }

    /** True when the change turns faces on or off, so markdown already shown needs rebinding. */
    fun setDataSavingMode(dataSavingMode: Boolean): Boolean {
        val wasEnabled = enabled
        this.dataSavingMode = dataSavingMode
        return wasEnabled != enabled
    }

    companion object {
        /** Reads data saving and image preview from [activity]'s settings, as [EmotePlugin] does. */
        @JvmStatic
        fun create(activity: BaseActivity, embeddedMediaType: Int): CommentFaces {
            val preferences = activity.defaultSharedPreferences
            val dataSavingModeString = preferences.getString(
                SharedPreferencesUtils.DATA_SAVING_MODE, SharedPreferencesUtils.DATA_SAVING_MODE_OFF
            )
            val dataSavingMode = when (dataSavingModeString) {
                SharedPreferencesUtils.DATA_SAVING_MODE_ALWAYS -> true
                SharedPreferencesUtils.DATA_SAVING_MODE_ONLY_ON_CELLULAR_DATA ->
                    Utils.getConnectedNetwork(activity) == Utils.NETWORK_TYPE_CELLULAR
                else -> false
            }
            val disableImagePreview = preferences.getBoolean(SharedPreferencesUtils.DISABLE_IMAGE_PREVIEW, false)
            return create(activity, embeddedMediaType, dataSavingMode, disableImagePreview)
        }

        @JvmStatic
        fun create(
            activity: BaseActivity,
            embeddedMediaType: Int,
            dataSavingMode: Boolean,
            disableImagePreview: Boolean,
        ): CommentFaces {
            val canShow = SharedPreferencesUtils.canShowCommentFace(embeddedMediaType)
            // Glide.with throws for a destroyed activity, which a late adapter build can meet.
            if (activity.isFinishing || activity.isDestroyed) {
                return CommentFaces(null, false, dataSavingMode, disableImagePreview,
                    CommentFacePlugin(null, activity.resources))
            }
            val repository = (activity.application as Infinity).appComponent.commentFaceRepository()
            return CommentFaces(repository, canShow, dataSavingMode, disableImagePreview,
                CommentFacePlugin(Glide.with(activity), activity.resources))
        }

        /** Whether [markdown] has a link that could be a face, for picking which rows to rebind. */
        @JvmStatic
        fun mayContainFace(markdown: String?): Boolean =
            markdown != null && (markdown.contains("](#") || markdown.contains("](/"))
    }
}
