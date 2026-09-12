package ml.docilealligator.infinityforreddit.shadowbox

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.databinding.FragmentShadowboxEndPageBinding
import ml.docilealligator.infinityforreddit.post.LoadingMorePostsStatus

/**
 * The page after the last post: shows whether more posts are loading, failed (tap to retry) or
 * ran out. Once a load succeeds the pager slides past it onto the first new post.
 */
class ShadowboxEndPageFragment : Fragment() {

    private var _binding: FragmentShadowboxEndPageBinding? = null
    private val binding: FragmentShadowboxEndPageBinding
        get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentShadowboxEndPageBinding.inflate(inflater, container, false)
        val activity = requireActivity() as ShadowboxActivity
        activity.typeface?.let { binding.statusTextViewShadowboxEndPageFragment.typeface = it }
        binding.root.setOnClickListener {
            // Only a failed load is worth retrying; the model refuses while loading or when done.
            activity.fetchMorePosts()
        }
        // The activity's own instance: asking ViewModelProvider for it without the factory it was
        // created with would try to construct one, and this model has no no-arg constructor.
        activity.viewModel.loadMorePostsState.observe(viewLifecycleOwner) { state -> render(state.status) }
        return binding.root
    }

    private fun render(@LoadingMorePostsStatus status: Int) {
        val binding = _binding ?: return
        when (status) {
            LoadingMorePostsStatus.LOADING -> {
                binding.progressBarShadowboxEndPageFragment.visibility = View.VISIBLE
                binding.statusTextViewShadowboxEndPageFragment.setText(R.string.loading)
            }
            LoadingMorePostsStatus.FAILED -> {
                binding.progressBarShadowboxEndPageFragment.visibility = View.INVISIBLE
                binding.statusTextViewShadowboxEndPageFragment.setText(R.string.load_more_posts_failed)
            }
            LoadingMorePostsStatus.NO_MORE_POSTS -> {
                binding.progressBarShadowboxEndPageFragment.visibility = View.INVISIBLE
                binding.statusTextViewShadowboxEndPageFragment.setText(R.string.no_more_posts)
            }
            else -> {
                // NOT_LOADING / LOADED: nothing in flight and nothing to say yet.
                binding.progressBarShadowboxEndPageFragment.visibility = View.INVISIBLE
                binding.statusTextViewShadowboxEndPageFragment.text = ""
            }
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
