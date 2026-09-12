package ml.docilealligator.infinityforreddit.shadowbox

import android.graphics.RectF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.databinding.ShadowboxMediaPreviewBinding

/**
 * A page that stands in for content the pager does not render itself: a link post, or a video the
 * full player has to resolve first. Shows the post's preview; a video gets a play badge over it,
 * and the panel's fullscreen button is what opens the link or hands the video to the player.
 */
class ShadowboxPreviewPageFragment : ShadowboxPageFragment() {

    private var _binding: ShadowboxMediaPreviewBinding? = null
    private val binding: ShadowboxMediaPreviewBinding
        get() = _binding!!
    private var kind = KIND_LINK
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kind = requireArguments().getInt(ARG_KIND, KIND_LINK)
    }

    override fun onCreateMediaView(inflater: LayoutInflater, container: ViewGroup) {
        val binding = ShadowboxMediaPreviewBinding.inflate(inflater, container, true)
        _binding = binding
        if (kind == KIND_VIDEO) {
            // A play badge, because nothing on the page says it is a video otherwise.
            binding.badgeImageViewShadowboxMediaPreview.setImageResource(R.drawable.ic_play_circle_36dp)
            binding.badgeImageViewShadowboxMediaPreview.visibility = View.VISIBLE
        }
        // Pressing the picture opens the link, the way pressing a link post's preview does in the
        // feed. Pressing the black around it toggles the chrome, so the bar can still be hidden
        // and brought back on a page whose whole content is one press target.
        binding.root.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                lastTouchX = event.x
                lastTouchY = event.y
            }
            false
        }
        binding.root.setOnClickListener {
            if (pressWasOnImage()) {
                openFullViewer()
            } else {
                toggleChrome()
            }
        }
    }

    /**
     * Whether the last press landed on the preview itself rather than on the letterboxing around
     * it. The view fills the page and the bitmap is fitCenter'd inside it, so the drawn rectangle
     * has to come from the image matrix; with no preview loaded there is nothing to press.
     */
    private fun pressWasOnImage(): Boolean {
        val imageView = _binding?.imageViewShadowboxMediaPreview ?: return false
        val drawable = imageView.drawable ?: return false
        val drawn = RectF(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        imageView.imageMatrix.mapRect(drawn)
        drawn.offset(imageView.left.toFloat(), imageView.top.toFloat())
        return drawn.contains(lastTouchX, lastTouchY)
    }

    override fun loadMedia() {
        val preview = ShadowboxPreviews.bestPreview(post, maxResolution, dataSavingMode) ?: return
        glide.load(preview.previewUrl).into(binding.imageViewShadowboxMediaPreview)
    }

    override fun openFullViewer() {
        if (kind == KIND_VIDEO) {
            ShadowboxMediaIntents.openVideo(host, post, 0L)
        } else {
            ShadowboxMediaIntents.openLink(host, post)
        }
    }

    override fun onDestroyView() {
        _binding?.let { glide.clear(it.imageViewShadowboxMediaPreview) }
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val KIND_LINK = 0
        const val KIND_VIDEO = 1
        private const val ARG_KIND = "AK"

        fun newInstance(position: Int, blur: Boolean, kind: Int): ShadowboxPreviewPageFragment {
            val fragment = ShadowboxPreviewPageFragment()
            val args = baseArguments(position, blur)
            args.putInt(ARG_KIND, kind)
            fragment.arguments = args
            return fragment
        }
    }
}
