package ml.docilealligator.infinityforreddit.shadowbox

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.text.Spanned
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.graphics.Insets
import androidx.core.view.updatePadding
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import com.bumptech.glide.RequestManager
import ml.docilealligator.infinityforreddit.activities.BaseActivity
import ml.docilealligator.infinityforreddit.activities.LinkResolverActivity
import ml.docilealligator.infinityforreddit.activities.ViewImageOrGifActivity
import ml.docilealligator.infinityforreddit.customviews.LinearLayoutManagerBugFixed
import ml.docilealligator.infinityforreddit.databinding.ShadowboxMediaTextBinding
import ml.docilealligator.infinityforreddit.markdown.MarkdownUtils
import ml.docilealligator.infinityforreddit.markdown.emote.EmoteCloseBracketInlineProcessor
import ml.docilealligator.infinityforreddit.markdown.emote.EmotePlugin
import ml.docilealligator.infinityforreddit.markdown.imageandgif.ImageAndGifEntry
import ml.docilealligator.infinityforreddit.markdown.imageandgif.ImageAndGifPlugin
import ml.docilealligator.infinityforreddit.thing.MediaMetadata
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils

/**
 * A text post: the title over its selftext, rendered white on black with the same Markwon recipe
 * the post-detail screen uses, so inline images, gifs and emotes are pictures here rather than
 * bare links. Inline videos are still links: the comments screen is one tap away for those.
 */
class ShadowboxTextPageFragment : ShadowboxPageFragment() {

    private var _binding: ShadowboxMediaTextBinding? = null
    private val binding: ShadowboxMediaTextBinding
        get() = _binding!!
    private var panelHeight = 0
    private var insets = Insets.NONE

    /** Where the body starts: clear of the status bar, plus a margin. */
    private var topInsetPadding = 0

    override fun onCreateMediaView(inflater: LayoutInflater, container: ViewGroup) {
        val binding = ShadowboxMediaTextBinding.inflate(inflater, container, true)
        _binding = binding
        host.titleTypeface?.let { binding.titleTextViewShadowboxMediaText.typeface = it }
        // The info panel already shows the title. Repeating it above the selftext put the same
        // sentence on screen twice; it is kept only when there is no body to show, where the
        // page would otherwise be blank.
        if (!hasBody()) {
            binding.titleTextViewShadowboxMediaText.text = post.title
            binding.titleTextViewShadowboxMediaText.visibility = View.VISIBLE
            binding.contentRecyclerViewShadowboxMediaText.visibility = View.GONE
        } else {
            binding.titleTextViewShadowboxMediaText.visibility = View.GONE
            binding.contentRecyclerViewShadowboxMediaText.visibility = View.VISIBLE
        }
        binding.contentRecyclerViewShadowboxMediaText.layoutManager = LinearLayoutManagerBugFixed(host)
        binding.root.setOnClickListener { toggleChrome() }
        addTapToToggleChrome(binding.contentRecyclerViewShadowboxMediaText)
    }

    /**
     * Whether the post has a body worth rendering. A selftext that only repeats the title is not
     * one: the panel and the title already say it, and a page of the same sentence twice over
     * is worse than the title alone.
     */
    private fun hasBody(): Boolean {
        val selfText = post.selfText
        if (selfText.isNullOrBlank()) {
            return false
        }
        val plain = post.selfTextPlainTrimmed ?: post.selfTextPlain ?: selfText
        return !plain.trim().equals(post.title.trim(), ignoreCase = true)
    }

    override fun loadMedia() {
        val selfText = post.selfText
        if (!hasBody() || selfText == null) {
            return
        }
        val markdownColor = Color.WHITE
        val isNsfw = post.isNSFW
        val miscPlugin = object : AbstractMarkwonPlugin() {
            override fun beforeSetText(textView: TextView, markdown: Spanned) {
                host.contentTypeface?.let { textView.setTypeface(it) }
                textView.setTextColor(markdownColor)
            }

            override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                builder.linkResolver { _, link ->
                    val intent = Intent(host, LinkResolverActivity::class.java)
                    intent.data = Uri.parse(link)
                    intent.putExtra(LinkResolverActivity.EXTRA_IS_NSFW, isNsfw)
                    host.startActivity(intent)
                }
            }

            override fun configureTheme(builder: MarkwonTheme.Builder) {
                builder.linkColor(customThemeWrapper.linkColor)
            }
        }
        // The same two settings the post-detail screen reads for its own inline media, so a text
        // post renders here the way it renders there.
        val embeddedMediaType = SharedPreferencesUtils.getInt(
            sharedPreferences, SharedPreferencesUtils.EMBEDDED_MEDIA_TYPE, "15"
        )
        val disableImagePreview = sharedPreferences.getBoolean(
            SharedPreferencesUtils.DISABLE_IMAGE_PREVIEW, false
        )
        val emotePlugin = EmotePlugin.create(
            host, embeddedMediaType, dataSavingMode, disableImagePreview, ::openMarkdownMedia
        )
        val emoteCloseBracketInlineProcessor = EmoteCloseBracketInlineProcessor()
        val imageAndGifPlugin = ImageAndGifPlugin()
        val markwon = MarkdownUtils.createFullRedditMarkwon(
            host, miscPlugin, emoteCloseBracketInlineProcessor, emotePlugin, imageAndGifPlugin,
            markdownColor, customThemeWrapper.spoilerBackgroundColor, null
        )
        // Never blurred: the page's own overlay has already been tapped away by the time anything
        // is rendered, so blurring the pictures inside it again would leave no way to see them.
        val imageAndGifEntry = ShadowboxImageAndGifEntry(
            host, glide, embeddedMediaType, dataSavingMode, disableImagePreview, false,
            // Written out rather than passed as a lambda: the constructor is Kotlin's, and Kotlin
            // converts a lambda to a Java interface only where the callee is Java's.
            ImageAndGifEntry.OnItemClickListener { mediaMetadata, _, _, _ ->
                openMarkdownMedia(mediaMetadata)
            }
        )
        // An inline image is written as a reference into the post's media metadata, so without
        // the map the parser does not recognise one at all and the body renders it as text. Set
        // before the markdown is parsed, which is what setMarkdown does.
        emoteCloseBracketInlineProcessor.setMediaMetadataMap(post.mediaMetadataMap)
        imageAndGifPlugin.setMediaMetadataMap(post.mediaMetadataMap)
        // A post body embed has no comment id; the saved name is the title plus the post id.
        imageAndGifEntry.setCurrentCommentId(null)
        imageAndGifEntry.setCurrentPostId(post.id)
        imageAndGifEntry.setCurrentPostTitle(post.title)
        val adapter = MarkdownUtils.createCustomTablesAndImagesAdapter(host, imageAndGifEntry)
        binding.contentRecyclerViewShadowboxMediaText.adapter = adapter
        adapter.setMarkdown(markwon, selfText)
        @Suppress("NotifyDataSetChanged")
        adapter.notifyDataSetChanged()
    }

    /** Opens an image, gif or emote from the body in the viewer the rest of the app opens it in. */
    private fun openMarkdownMedia(mediaMetadata: MediaMetadata) {
        val intent = Intent(host, ViewImageOrGifActivity::class.java)
        if (mediaMetadata.isGIF) {
            intent.putExtra(ViewImageOrGifActivity.EXTRA_GIF_URL_KEY, mediaMetadata.original.url)
        } else {
            intent.putExtra(ViewImageOrGifActivity.EXTRA_IMAGE_URL_KEY, mediaMetadata.original.url)
        }
        intent.putExtra(ViewImageOrGifActivity.EXTRA_IS_NSFW, post.isNSFW)
        intent.putExtra(ViewImageOrGifActivity.EXTRA_SUBREDDIT_OR_USERNAME_KEY, post.subredditName)
        intent.putExtra(ViewImageOrGifActivity.EXTRA_FILE_NAME_KEY, mediaMetadata.fileName)
        // Without these the saved name falls back to a bare "reddit_image", which collides across
        // every post.
        intent.putExtra(ViewImageOrGifActivity.EXTRA_POST_TITLE_KEY, post.title)
        intent.putExtra(ViewImageOrGifActivity.EXTRA_POST_ID_KEY, post.id)
        host.startActivity(intent)
    }

    override fun openFullViewer() {
        openComments()
    }

    override fun onInsetsChanged(insets: Insets) {
        this.insets = insets
        applyPadding()
    }

    override fun onPanelHeightChanged(height: Int) {
        panelHeight = height
        applyPadding()
    }

    private fun applyPadding() {
        val binding = _binding ?: return
        binding.root.updatePadding(left = insets.left, right = insets.right)
        topInsetPadding = insets.top + (16 * resources.displayMetrics.density).toInt()
        binding.contentRecyclerViewShadowboxMediaText.updatePadding(
            top = topInsetPadding, bottom = maxOf(panelHeight, insets.bottom)
        )
        // The title stands in for the body on a post that has none, so it starts where the body
        // would and has to clear the status bar by itself.
        binding.titleTextViewShadowboxMediaText.updatePadding(top = topInsetPadding)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        fun newInstance(position: Int, blur: Boolean): ShadowboxTextPageFragment {
            val fragment = ShadowboxTextPageFragment()
            fragment.arguments = baseArguments(position, blur)
            return fragment
        }
    }
}

/**
 * The post-detail screen's inline image block, in this page's colours.
 *
 * The entry paints its caption and its error line in the theme's text colours, which on a light
 * theme are dark; this page is white on black throughout, so those lines would be drawn in a
 * colour that cannot be read on it -- and with image previews off the caption line is not a label
 * but the whole of what the block shows. Nothing else about the block changes.
 */
private class ShadowboxImageAndGifEntry(
    baseActivity: BaseActivity,
    glide: RequestManager,
    embeddedMediaType: Int,
    dataSavingMode: Boolean,
    disableImagePreview: Boolean,
    blurImage: Boolean,
    onItemClickListener: ImageAndGifEntry.OnItemClickListener
) : ImageAndGifEntry(
    baseActivity, glide, embeddedMediaType, dataSavingMode, disableImagePreview, blurImage,
    onItemClickListener
) {
    override fun createHolder(inflater: LayoutInflater, parent: ViewGroup): ImageAndGifEntry.Holder {
        val holder = super.createHolder(inflater, parent)
        holder.binding.captionTextViewMarkdownImageAndGifBlock.setTextColor(Color.WHITE)
        holder.binding.loadImageErrorTextViewMarkdownImageAndGifBlock.setTextColor(Color.WHITE)
        return holder
    }
}
