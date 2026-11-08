package ml.docilealligator.infinityforreddit.markdown.video

import android.content.Intent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import io.noties.markwon.Markwon
import io.noties.markwon.recycler.MarkwonAdapter
import me.saket.bettermovementmethod.BetterLinkMovementMethod
import ml.docilealligator.infinityforreddit.SaveMemoryCenterInisdeDownsampleStrategy
import ml.docilealligator.infinityforreddit.activities.BaseActivity
import ml.docilealligator.infinityforreddit.activities.LinkResolverActivity
import ml.docilealligator.infinityforreddit.bottomsheetfragments.UrlMenuBottomSheetFragment
import ml.docilealligator.infinityforreddit.databinding.MarkdownVideoBlockBinding
import ml.docilealligator.infinityforreddit.managers.VideoMuteManager
import ml.docilealligator.infinityforreddit.thing.MediaMetadata
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils
import ml.docilealligator.infinityforreddit.videoautoplay.ExoCreator

class VideoEntry(
    private val baseActivity: BaseActivity,
    embeddedMediaType: Int,
    private val onItemClickListener: OnItemClickListener
): MarkwonAdapter.Entry<VideoBlock, VideoEntry.Holder>() {
    private val saveMemoryCenterInsideDownsampleStrategy: SaveMemoryCenterInisdeDownsampleStrategy
    private val colorAccent: Int
    private val primaryTextColor: Int
    private val postContentColor: Int
    private val linkColor: Int
    private val canShowImage: Boolean
    private val canShowGif: Boolean

    // Set as each comment/post body is bound, then captured into the holder below. A click fires
    // long after bindHolder returned, by which time these have moved on to another row, so the
    // holder has to keep its own copy -- same arrangement as ImageAndGifEntry.
    private var currentCommentId: String? = null
    private var currentPostId: String? = null
    private var currentPostTitle: String? = null

    init {
        val sharedPreferences = baseActivity.getDefaultSharedPreferences()
        this.saveMemoryCenterInsideDownsampleStrategy = SaveMemoryCenterInisdeDownsampleStrategy(
            sharedPreferences.getString(
                SharedPreferencesUtils.POST_FEED_MAX_RESOLUTION,
                "5000000"
            )!!.toInt()
        )
        colorAccent = baseActivity.getCustomThemeWrapper().getColorAccent()
        primaryTextColor = baseActivity.getCustomThemeWrapper().getPrimaryTextColor()
        postContentColor = baseActivity.getCustomThemeWrapper().getPostContentColor()
        linkColor = baseActivity.getCustomThemeWrapper().getLinkColor()
        canShowImage = SharedPreferencesUtils.canShowImage(embeddedMediaType)
        canShowGif = SharedPreferencesUtils.canShowGif(embeddedMediaType)
    }

    override fun createHolder(inflater: LayoutInflater, parent: ViewGroup): Holder {
        return Holder(MarkdownVideoBlockBinding.inflate(inflater, parent, false))
    }

    override fun bindHolder(markwon: Markwon, holder: Holder, node: VideoBlock) {
        holder.videoBlock = node
        holder.commentId = currentCommentId
        holder.postId = currentPostId
        holder.postTitle = currentPostTitle

        if (node.mediaMetadata.caption != null) {
            holder.binding.captionTextViewMarkdownVideoBlock.visibility = View.VISIBLE
            holder.binding.captionTextViewMarkdownVideoBlock.text = node.mediaMetadata.caption
        }

    }

    /*private fun showVideoAsUrl(holder: Holder, node: VideoBlock) {
        holder.binding.playerViewMarkdownVideoBlock.visibility = View.GONE
        holder.binding.captionTextViewMarkdownVideoBlock.visibility = View.VISIBLE
        holder.binding.captionTextViewMarkdownVideoBlock.setGravity(Gravity.NO_GRAVITY)
        val spannableString = SpannableString(node.mediaMetadata.caption ?: node.mediaMetadata.original.url)
        spannableString.setSpan(
            URLSpan(node.mediaMetadata.original.url),
            0,
            spannableString.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        holder.binding.captionTextViewMarkdownVideoBlock.text = spannableString
    }*/

    override fun onViewRecycled(holder: Holder) {
        super.onViewRecycled(holder)
        //holder.binding.errorLoadingVideoImageViewMarkdownVideoBlock.setVisibility(View.GONE)
        holder.binding.captionTextViewMarkdownVideoBlock.visibility = View.GONE
        holder.binding.captionTextViewMarkdownVideoBlock.setGravity(Gravity.CENTER_HORIZONTAL)
    }

    /*fun setDataSavingMode(dataSavingMode: Boolean) {
        this.dataSavingMode = dataSavingMode
    }*/

    inner class Holder(
        val binding: MarkdownVideoBlockBinding
    ) : MarkwonAdapter.Holder(binding.getRoot()) {
        var videoBlock: VideoBlock? = null
        var commentId: String? = null
        var postId: String? = null
        var postTitle: String? = null

        init {
            /*playDrawable = AppCompatResources.getDrawable(baseActivity, R.drawable.ic_play_arrow_24dp)!!
            pauseDrawable = AppCompatResources.getDrawable(baseActivity, R.drawable.ic_pause_24dp)!!*/

            binding.captionTextViewMarkdownVideoBlock.setTextColor(postContentColor)
            binding.captionTextViewMarkdownVideoBlock.setLinkTextColor(linkColor)

            if (baseActivity.contentTypeface != null) {
                binding.captionTextViewMarkdownVideoBlock.setTypeface(baseActivity.contentTypeface)
            }

            binding.frameLayoutMarkdownVideoBlock.setOnClickListener {
                onItemClickListener.onItemClick(
                    videoBlock?.mediaMetadata, commentId, postId, postTitle
                )
            }

            binding.captionTextViewMarkdownVideoBlock.movementMethod = BetterLinkMovementMethod.newInstance()
                .setOnLinkClickListener { _, url: String ->
                    val intent = Intent(baseActivity, LinkResolverActivity::class.java)
                    intent.setData(url.toUri())
                    baseActivity.startActivity(intent)
                    true
                }
                .setOnLinkLongClickListener { _, url: String ->
                    val urlMenuBottomSheetFragment = UrlMenuBottomSheetFragment.newInstance(url)
                    urlMenuBottomSheetFragment.show(
                        baseActivity.supportFragmentManager,
                        urlMenuBottomSheetFragment.tag
                    )
                    true
                }
        }

    }

    fun setCurrentCommentId(commentId: String?) {
        this.currentCommentId = commentId
    }

    fun setCurrentPostId(postId: String?) {
        this.currentPostId = postId
    }

    fun setCurrentPostTitle(postTitle: String?) {
        this.currentPostTitle = postTitle
    }

    interface OnItemClickListener {
        fun onItemClick(
            mediaMetadata: MediaMetadata?,
            commentId: String?,
            postId: String?,
            postTitle: String?,
        )
    }
}