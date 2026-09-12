package ml.docilealligator.infinityforreddit.shadowbox

import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import ml.docilealligator.infinityforreddit.customviews.LinearLayoutManagerBugFixed
import ml.docilealligator.infinityforreddit.databinding.ItemShadowboxGalleryBinding
import ml.docilealligator.infinityforreddit.databinding.ShadowboxMediaGalleryBinding
import ml.docilealligator.infinityforreddit.post.Post

/**
 * A Reddit gallery page: every item in a vertical list, like Slide's album pages. Tapping an item
 * opens the gallery viewer on it; the list is padded so the last item can scroll clear of the panel.
 */
class ShadowboxGalleryPageFragment : ShadowboxPageFragment() {

    private var _binding: ShadowboxMediaGalleryBinding? = null
    private val binding: ShadowboxMediaGalleryBinding
        get() = _binding!!
    private var adapter: GalleryAdapter? = null
    private var panelHeight = 0
    private var bottomInset = 0

    /** Height-to-width ratio the tiles are measured at before their image arrives. */
    private var tileRatio = 1f

    /**
     * The size a tile is measured to, and so the size its picture is asked for.
     *
     * The list is the page, edge to edge, so the tile width is the window's; the height follows
     * from the ratio under the same cap the tiles are given. Naming it on the request is what lets
     * a tile be decoded as it binds, instead of a frame later once the list has been laid out and
     * Glide has a view size to work from.
     */
    private var tileWidth = 0
    private var tileHeight = 0

    /** Whether this page's gallery may animate its gifs, under the app's autoplay rule. */
    private var autoplayGif = false

    /**
     * The post's own preview, which stands in for the first tile until that item's still arrives.
     *
     * Reddit builds a gallery post's preview from its first item, which is the same assumption
     * [tileRatio] already rests on. On the rare post where it is some other item, the first tile
     * shows the wrong picture for as long as its own still takes to arrive and no longer.
     */
    private var postPreviewUrl: String? = null

    override fun onCreateMediaView(inflater: LayoutInflater, container: ViewGroup) {
        val binding = ShadowboxMediaGalleryBinding.inflate(inflater, container, true)
        _binding = binding
        binding.recyclerViewShadowboxMediaGallery.layoutManager = LinearLayoutManagerBugFixed(host)
        // Tapping anywhere in the list toggles the chrome, exactly as tapping an image page does;
        // the panel's fullscreen button opens the gallery viewer on whichever item is in view.
        addTapToToggleChrome(binding.recyclerViewShadowboxMediaGallery)
    }

    override fun loadMedia() {
        val preview = ShadowboxPreviews.bestPreview(post, maxResolution, dataSavingMode)
        postPreviewUrl = preview?.previewUrl
        tileRatio = ShadowboxPreviews.galleryTileRatio(preview)
        val metrics = resources.displayMetrics
        tileWidth = metrics.widthPixels
        tileHeight = ShadowboxPreviews.galleryTileHeight(tileRatio, tileWidth, metrics.heightPixels)
        autoplayGif = shouldAutoplay()
        val adapter = GalleryAdapter(post.gallery ?: emptyList())
        this.adapter = adapter
        binding.recyclerViewShadowboxMediaGallery.adapter = adapter
    }

    override fun openFullViewer() {
        val layoutManager = _binding?.recyclerViewShadowboxMediaGallery?.layoutManager as? LinearLayoutManagerBugFixed
        val index = layoutManager?.findFirstVisibleItemPosition()?.takeIf { it >= 0 } ?: 0
        ShadowboxMediaIntents.openGallery(host, post, index)
    }

    override fun onInsetsChanged(insets: Insets) {
        bottomInset = insets.bottom
        applyListPadding()
    }

    override fun onPanelHeightChanged(height: Int) {
        panelHeight = height
        applyListPadding()
    }

    private fun applyListPadding() {
        // The panel's own height already includes the bottom inset it is padded by.
        _binding?.recyclerViewShadowboxMediaGallery?.updatePadding(bottom = maxOf(panelHeight, bottomInset))
    }

    override fun onDestroyView() {
        adapter = null
        _binding = null
        super.onDestroyView()
    }

    private inner class GalleryAdapter(private val items: List<Post.Gallery>) : RecyclerView.Adapter<GalleryViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
            return GalleryViewHolder(ItemShadowboxGalleryBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) {
            holder.bind(items[position], position)
        }

        override fun getItemCount(): Int = items.size

        override fun onViewRecycled(holder: GalleryViewHolder) {
            glide.clear(holder.binding.imageViewItemShadowboxGallery)
        }
    }

    private inner class GalleryViewHolder(val binding: ItemShadowboxGalleryBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            host.typeface?.let { binding.captionTextViewItemShadowboxGallery.typeface = it }
        }

        fun bind(item: Post.Gallery, position: Int) {
            binding.imageViewItemShadowboxGallery.setRatio(tileRatio)
            // A portrait ratio on a full-width tile can work out taller than the screen; cap it so
            // one image cannot fill a whole scroll of the page.
            binding.imageViewItemShadowboxGallery.setRatioMaxHeight(resources.displayMetrics.heightPixels)
            binding.errorImageViewItemShadowboxGallery.visibility = View.GONE
            binding.playBadgeImageViewItemShadowboxGallery.visibility =
                if (item.mediaType == Post.Gallery.TYPE_VIDEO) View.VISIBLE else View.GONE
            // The resolution-bounded still Reddit publishes for the item, which is what a tile
            // shows for every type: a fraction of the source, and what the feed's own inline
            // gallery loads.
            val still = item.feedPreviewUrl
            // What the tile ends up displaying, and the cheaper pictures it shows on the way
            // there, in the order it prefers them.
            val main: String?
            val standIns: MutableList<String> = mutableListOf()
            when (item.mediaType) {
                Post.Gallery.TYPE_GIF -> {
                    // The tile animates only where the app's autoplay rule allows it, the way the
                    // feed's inline gallery does; otherwise it shows the still, and the source --
                    // often tens of megabytes -- is never fetched at all. A gif with no still is
                    // the one exception, since there is nothing else it could show.
                    if (autoplayGif || still == null) {
                        // Over the still rather than instead of it: the tile used to stay empty
                        // for the whole of that download.
                        main = item.url
                        still?.let { standIns.add(it) }
                    } else {
                        main = still
                    }
                }
                Post.Gallery.TYPE_VIDEO -> {
                    // Never item.url for a video item: that is the mp4, which Glide cannot decode
                    // into an ImageView, and asking it to left the tile blank under the badge for
                    // good. Without a still there is nothing to show, so the tile says so.
                    main = still
                }
                else -> {
                    // The source is the full-size image, several thousand pixels wide; it is the
                    // fallback only because an item without a still has nothing else to show.
                    main = still ?: item.url
                }
            }
            if (position == 0) {
                postPreviewUrl?.let { standIns.add(it) }
            }
            if (main == null) {
                showTileError()
            } else {
                loadTile(main, standIns.filter { it != main })
            }
            if (TextUtils.isEmpty(item.caption)) {
                binding.captionTextViewItemShadowboxGallery.visibility = View.GONE
            } else {
                binding.captionTextViewItemShadowboxGallery.visibility = View.VISIBLE
                binding.captionTextViewItemShadowboxGallery.text = item.caption
            }
            // No per-item click: it would fight the tap-to-toggle rule, and the fullscreen button
            // already opens the item the user is looking at.
            binding.root.isClickable = false
        }

        /**
         * A tile fades in rather than appearing: the row is already measured to the post's ratio,
         * so the picture is the only thing that arrives, and it should not arrive with a snap. An
         * image Glide already holds is still set straight away, without any animation.
         */
        private fun tileRequest(url: String): RequestBuilder<Drawable> =
            ShadowboxPreviews.previewRequest(glide, url).override(tileWidth, tileHeight)

        /**
         * Loads [url] into the tile, with [standIns] showing in turn until it arrives: the item's
         * own still under an animating gif, and under the first tile the post's preview, which is
         * the picture the feed already showed. Glide takes one thumbnail per request, so a second
         * stand-in hangs off the first rather than replacing it.
         */
        private fun loadTile(url: String, standIns: List<String>) {
            var thumbnail: RequestBuilder<Drawable>? = null
            for (standIn in standIns.reversed()) {
                val request = tileRequest(standIn)
                thumbnail = if (thumbnail == null) request else request.thumbnail(thumbnail)
            }
            val main = tileRequest(url)
            val request = if (thumbnail == null) main else main.thumbnail(thumbnail)
            request.listener(tileListener).into(binding.imageViewItemShadowboxGallery)
        }

        /**
         * Marks the tile as having nothing to show, in the space the ratio already reserved for
         * it. Only from [bind], where there is no request of this tile's to get in the way of.
         */
        private fun showTileError() {
            glide.clear(binding.imageViewItemShadowboxGallery)
            binding.imageViewItemShadowboxGallery.setImageDrawable(null)
            markTileFailed()
        }

        private fun markTileFailed() {
            binding.playBadgeImageViewItemShadowboxGallery.visibility = View.GONE
            binding.errorImageViewItemShadowboxGallery.visibility = View.VISIBLE
        }

        /**
         * Puts the error mark up when a tile's own load fails and leaves nothing behind. Only the
         * tile's request reports here, and a stand-in that got there first is a picture: a gif
         * whose source failed over its still is a tile that has something to show, not one to
         * mark as empty.
         */
        private val tileListener = object : RequestListener<Drawable> {
            override fun onLoadFailed(
                e: GlideException?, model: Any?, target: Target<Drawable>, isFirstResource: Boolean
            ): Boolean {
                if (binding.imageViewItemShadowboxGallery.drawable == null) {
                    markTileFailed()
                }
                return false
            }

            override fun onResourceReady(
                resource: Drawable, model: Any, target: Target<Drawable>,
                dataSource: DataSource, isFirstResource: Boolean
            ): Boolean {
                binding.errorImageViewItemShadowboxGallery.visibility = View.GONE
                if (!autoplayGif && resource is Animatable) {
                    // The one gif that is loaded with autoplay off: the item had no still. Glide
                    // starts it after this callback returns, so stop it on the next loop, on the
                    // frame it reached, exactly as the feed does.
                    binding.imageViewItemShadowboxGallery.post {
                        val drawable = binding.imageViewItemShadowboxGallery.drawable
                        if (drawable is Animatable && drawable.isRunning) {
                            drawable.stop()
                        }
                    }
                }
                return false
            }
        }
    }

    companion object {
        fun newInstance(position: Int, blur: Boolean): ShadowboxGalleryPageFragment {
            val fragment = ShadowboxGalleryPageFragment()
            fragment.arguments = baseArguments(position, blur)
            return fragment
        }
    }
}
