package ml.docilealligator.infinityforreddit.shadowbox

import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.Insets
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
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

    override fun onCreateMediaView(inflater: LayoutInflater, container: ViewGroup) {
        val binding = ShadowboxMediaGalleryBinding.inflate(inflater, container, true)
        _binding = binding
        binding.recyclerViewShadowboxMediaGallery.layoutManager = LinearLayoutManagerBugFixed(host)
        // Tapping anywhere in the list toggles the chrome, exactly as tapping an image page does;
        // the panel's fullscreen button opens the gallery viewer on whichever item is in view.
        addTapToToggleChrome(binding.recyclerViewShadowboxMediaGallery)
    }

    override fun loadMedia() {
        // The gallery items carry no dimensions of their own, so the post's preview stands in for
        // all of them, exactly as the feed's inline gallery does; square when there is none.
        val preview = ShadowboxPreviews.bestPreview(post, maxResolution, dataSavingMode)
        tileRatio = if (preview != null && preview.previewWidth > 0 && preview.previewHeight > 0) {
            preview.previewHeight.toFloat() / preview.previewWidth
        } else {
            1f
        }
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
            holder.bind(items[position])
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

        fun bind(item: Post.Gallery) {
            binding.imageViewItemShadowboxGallery.setRatio(tileRatio)
            // A portrait ratio on a full-width tile can work out taller than the screen; cap it so
            // one image cannot fill a whole scroll of the page.
            binding.imageViewItemShadowboxGallery.setRatioMaxHeight(resources.displayMetrics.heightPixels)
            when (item.mediaType) {
                Post.Gallery.TYPE_GIF -> {
                    // The source url animates; the feed preview is a still.
                    glide.load(item.url).into(binding.imageViewItemShadowboxGallery)
                    binding.playBadgeImageViewItemShadowboxGallery.visibility = View.GONE
                }
                Post.Gallery.TYPE_VIDEO -> {
                    glide.load(item.feedPreviewUrl ?: item.url).into(binding.imageViewItemShadowboxGallery)
                    binding.playBadgeImageViewItemShadowboxGallery.visibility = View.VISIBLE
                }
                else -> {
                    glide.load(item.feedPreviewUrl ?: item.url).into(binding.imageViewItemShadowboxGallery)
                    binding.playBadgeImageViewItemShadowboxGallery.visibility = View.GONE
                }
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
    }

    companion object {
        fun newInstance(position: Int, blur: Boolean): ShadowboxGalleryPageFragment {
            val fragment = ShadowboxGalleryPageFragment()
            fragment.arguments = baseArguments(position, blur)
            return fragment
        }
    }
}
