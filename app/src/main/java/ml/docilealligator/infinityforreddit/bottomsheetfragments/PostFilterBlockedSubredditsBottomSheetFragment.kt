package ml.docilealligator.infinityforreddit.bottomsheetfragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.BundleCompat
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.activities.BaseActivity
import ml.docilealligator.infinityforreddit.adapters.PostFilterBlockedSubredditsRecyclerViewAdapter
import ml.docilealligator.infinityforreddit.customviews.LandscapeExpandedRoundedBottomSheetDialogFragment
import ml.docilealligator.infinityforreddit.databinding.FragmentPostFilterBlockedSubredditsBottomSheetBinding
import ml.docilealligator.infinityforreddit.postfilter.FilterRule
import ml.docilealligator.infinityforreddit.postfilter.PostFilterBlockedSubreddit
import ml.docilealligator.infinityforreddit.utils.Utils

/**
 * Shows every subreddit one wildcard rule has actually hidden posts from, worst offender first.
 *
 * This is the only honest description of what a broad term like `*irl*` does: the set it will match
 * cannot be worked out when the rule is written, because it depends on subreddit names nobody has a
 * list of, including ones that do not exist yet. So it is not predicted — it is observed, as the
 * user browses, and shown here next to the rule that caused it.
 *
 * Each row can be excepted, which is the fix for a rule that is right in general and wrong in one
 * place: allowing r/EarthPorn leaves a `*porn*` rule hiding everything else it was written for.
 */
class PostFilterBlockedSubredditsBottomSheetFragment :
    LandscapeExpandedRoundedBottomSheetDialogFragment() {

    /** Implemented by the host activity, which owns the database and the rule list. */
    interface Host {
        /**
         * Rows for this rule, delivered whenever they change.
         *
         * [owner] is the sheet's own view lifecycle, not the host's: the callback closes over this
         * sheet's adapter and binding, so an observer scoped to the activity would keep firing into
         * a dismissed sheet's views and pile up one more subscription every time it is reopened.
         */
        fun observeBlockedSubreddits(
            owner: LifecycleOwner,
            rule: FilterRule,
            observer: (List<PostFilterBlockedSubreddit>) -> Unit,
        )

        /** Turn hiding of one subreddit by this rule on or off. */
        fun setBlockedSubredditExcepted(
            rule: FilterRule,
            subredditName: String,
            excepted: Boolean,
        )

        /** The user wants the rule itself, not its consequences. */
        fun onEditRuleRequested(rule: FilterRule)
    }

    companion object {
        const val EXTRA_RULE = "ER"

        fun newInstance(rule: FilterRule): PostFilterBlockedSubredditsBottomSheetFragment =
            PostFilterBlockedSubredditsBottomSheetFragment().apply {
                arguments = Bundle().apply { putParcelable(EXTRA_RULE, rule) }
            }
    }

    private lateinit var host: Host
    private lateinit var binding: FragmentPostFilterBlockedSubredditsBottomSheetBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentPostFilterBlockedSubredditsBottomSheetBinding.inflate(inflater, container, false)
        val activity = requireActivity() as BaseActivity
        val customThemeWrapper = activity.customThemeWrapper
        val rule = BundleCompat.getParcelable(requireArguments(), EXTRA_RULE, FilterRule::class.java)
            ?: return binding.root

        binding.titleTextViewPostFilterBlockedSubredditsBottomSheetFragment.text =
            getString(R.string.post_filter_blocked_subreddits_title, rule.value)
        binding.titleTextViewPostFilterBlockedSubredditsBottomSheetFragment
            .setTextColor(customThemeWrapper.primaryTextColor)
        binding.summaryTextViewPostFilterBlockedSubredditsBottomSheetFragment
            .setTextColor(customThemeWrapper.secondaryTextColor)
        binding.emptyTextViewPostFilterBlockedSubredditsBottomSheetFragment
            .setTextColor(customThemeWrapper.secondaryTextColor)
        binding.editRuleTextViewPostFilterBlockedSubredditsBottomSheetFragment
            .setTextColor(customThemeWrapper.colorAccent)

        val adapter = PostFilterBlockedSubredditsRecyclerViewAdapter(activity, customThemeWrapper) { blocked ->
            // Flip it and let the observer redraw, so the row's state always reflects what was
            // stored rather than what was tapped.
            host.setBlockedSubredditExcepted(rule, blocked.subredditName, !blocked.excepted)
        }
        binding.recyclerViewPostFilterBlockedSubredditsBottomSheetFragment.layoutManager =
            LinearLayoutManager(activity)
        binding.recyclerViewPostFilterBlockedSubredditsBottomSheetFragment.adapter = adapter

        host.observeBlockedSubreddits(viewLifecycleOwner, rule) { blocked ->
            adapter.setBlockedSubreddits(blocked)
            val empty = blocked.isEmpty()
            binding.emptyTextViewPostFilterBlockedSubredditsBottomSheetFragment.visibility =
                if (empty) View.VISIBLE else View.GONE
            binding.recyclerViewPostFilterBlockedSubredditsBottomSheetFragment.visibility =
                if (empty) View.GONE else View.VISIBLE
        }

        binding.editRuleTextViewPostFilterBlockedSubredditsBottomSheetFragment.setOnClickListener {
            dismiss()
            host.onEditRuleRequested(rule)
        }

        activity.typeface?.let { Utils.setFontToAllTextViews(binding.root, it) }
        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        host = context as Host
    }
}
