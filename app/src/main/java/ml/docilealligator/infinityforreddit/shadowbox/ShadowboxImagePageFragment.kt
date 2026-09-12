package ml.docilealligator.infinityforreddit.shadowbox

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.github.piasy.biv.loader.ImageLoader
import ml.docilealligator.infinityforreddit.SaveMemoryCenterInisdeDownsampleStrategy
import ml.docilealligator.infinityforreddit.activities.ViewImageOrGifActivity
import ml.docilealligator.infinityforreddit.customviews.GlideGifImageViewFactory
import ml.docilealligator.infinityforreddit.databinding.ShadowboxMediaImageBinding
import java.io.File

/** An image or GIF page: BigImageView with the same loader setup as ViewImageOrGifActivity. */
class ShadowboxImagePageFragment : ShadowboxPageFragment() {

    private var _binding: ShadowboxMediaImageBinding? = null
    private val binding: ShadowboxMediaImageBinding
        get() = _binding!!
    private lateinit var url: String
    private var isGif = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        url = requireArguments().getString(ARG_URL) ?: ""
        isGif = requireArguments().getBoolean(ARG_IS_GIF, false)
    }

    override fun onCreateMediaView(inflater: LayoutInflater, container: ViewGroup) {
        val binding = ShadowboxMediaImageBinding.inflate(inflater, container, true)
        _binding = binding
        binding.imageViewShadowboxMediaImage.setImageViewFactory(
            GlideGifImageViewFactory(SaveMemoryCenterInisdeDownsampleStrategy(maxResolution))
        )
        binding.imageViewShadowboxMediaImage.setImageLoaderCallback(object : ImageLoader.Callback {
            override fun onCacheHit(imageType: Int, image: File) {}

            override fun onCacheMiss(imageType: Int, image: File) {}

            override fun onStart() {}

            override fun onProgress(progress: Int) {}

            override fun onFinish() {}

            override fun onSuccess(image: File) {
                val binding = _binding ?: return
                binding.progressBarShadowboxMediaImage.visibility = View.GONE
                val view = binding.imageViewShadowboxMediaImage.ssiv ?: return
                view.setOnImageEventListener(object : SubsamplingScaleImageView.DefaultOnImageEventListener() {
                    override fun onImageLoaded() {
                        view.setMinimumDpi(80)
                        view.setDoubleTapZoomDpi(240)
                        view.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_FIXED)
                        view.isQuickScaleEnabled = true
                        view.resetScaleAndCenter()
                    }
                })
            }

            override fun onFail(error: Exception) {
                val binding = _binding ?: return
                binding.progressBarShadowboxMediaImage.visibility = View.GONE
                binding.loadImageErrorLinearLayoutShadowboxMediaImage.visibility = View.VISIBLE
            }
        })
        binding.imageViewShadowboxMediaImage.setOnClickListener { toggleChrome() }
        binding.loadImageErrorLinearLayoutShadowboxMediaImage.setOnClickListener {
            binding.progressBarShadowboxMediaImage.visibility = View.VISIBLE
            binding.loadImageErrorLinearLayoutShadowboxMediaImage.visibility = View.GONE
            loadMedia()
        }
        binding.progressBarShadowboxMediaImage.visibility = View.INVISIBLE
    }

    override fun loadMedia() {
        binding.progressBarShadowboxMediaImage.visibility = View.VISIBLE
        binding.imageViewShadowboxMediaImage.showImage(Uri.parse(url))
    }

    override fun openFullViewer() {
        val intent = Intent(host, ViewImageOrGifActivity::class.java)
        if (isGif) {
            intent.putExtra(ViewImageOrGifActivity.EXTRA_FILE_NAME_KEY, post.subredditName + "-" + post.id + ".gif")
            intent.putExtra(ViewImageOrGifActivity.EXTRA_GIF_URL_KEY, url)
        } else {
            intent.putExtra(ViewImageOrGifActivity.EXTRA_IMAGE_URL_KEY, url)
            intent.putExtra(ViewImageOrGifActivity.EXTRA_FILE_NAME_KEY, post.subredditName + "-" + post.id + ".jpg")
        }
        intent.putExtra(ViewImageOrGifActivity.EXTRA_POST_TITLE_KEY, post.title)
        intent.putExtra(ViewImageOrGifActivity.EXTRA_POST_ID_KEY, post.id)
        intent.putExtra(ViewImageOrGifActivity.EXTRA_SUBREDDIT_OR_USERNAME_KEY, post.subredditName)
        intent.putExtra(ViewImageOrGifActivity.EXTRA_IS_NSFW, post.isNSFW)
        host.startActivity(intent)
    }

    override fun onDestroyView() {
        _binding?.imageViewShadowboxMediaImage?.cancel()
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val ARG_URL = "AU"
        private const val ARG_IS_GIF = "AIG"

        fun newInstance(position: Int, blur: Boolean, url: String, isGif: Boolean): ShadowboxImagePageFragment {
            val fragment = ShadowboxImagePageFragment()
            val args = baseArguments(position, blur)
            args.putString(ARG_URL, url)
            args.putBoolean(ARG_IS_GIF, isGif)
            fragment.arguments = args
            return fragment
        }
    }
}
