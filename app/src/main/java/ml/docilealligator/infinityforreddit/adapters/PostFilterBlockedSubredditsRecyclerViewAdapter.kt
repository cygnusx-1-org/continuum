package ml.docilealligator.infinityforreddit.adapters

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.activities.BaseActivity
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper
import ml.docilealligator.infinityforreddit.databinding.ItemPostFilterBlockedSubredditBinding
import ml.docilealligator.infinityforreddit.postfilter.PostFilterBlockedSubreddit

/**
 * The subreddits one wildcard rule has hidden posts from, with a per-row toggle that stops the rule
 * hiding that one subreddit.
 */
class PostFilterBlockedSubredditsRecyclerViewAdapter(
    private val activity: BaseActivity,
    customThemeWrapper: CustomThemeWrapper,
    private val onToggleExcepted: (PostFilterBlockedSubreddit) -> Unit,
) : RecyclerView.Adapter<PostFilterBlockedSubredditsRecyclerViewAdapter.BlockedViewHolder>() {

    private val primaryTextColor = customThemeWrapper.primaryTextColor
    private val secondaryTextColor = customThemeWrapper.secondaryTextColor
    private val primaryIconColor = customThemeWrapper.primaryIconColor
    private val accentColor = customThemeWrapper.colorAccent

    private var blockedSubreddits: List<PostFilterBlockedSubreddit> = emptyList()

    fun setBlockedSubreddits(newBlockedSubreddits: List<PostFilterBlockedSubreddit>) {
        blockedSubreddits = newBlockedSubreddits.toList()
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = blockedSubreddits.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlockedViewHolder =
        BlockedViewHolder(
            ItemPostFilterBlockedSubredditBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
        )

    override fun onBindViewHolder(holder: BlockedViewHolder, position: Int) {
        holder.bind(blockedSubreddits[position])
    }

    inner class BlockedViewHolder(private val binding: ItemPostFilterBlockedSubredditBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.nameTextViewItemPostFilterBlockedSubreddit.setTextColor(primaryTextColor)
            binding.summaryTextViewItemPostFilterBlockedSubreddit.setTextColor(secondaryTextColor)
            activity.typeface?.let {
                binding.nameTextViewItemPostFilterBlockedSubreddit.typeface = it
                binding.summaryTextViewItemPostFilterBlockedSubreddit.typeface = it
            }
        }

        fun bind(blocked: PostFilterBlockedSubreddit) {
            binding.nameTextViewItemPostFilterBlockedSubreddit.text =
                activity.getString(R.string.subreddit_name_prefixed, blocked.subredditName)
            binding.summaryTextViewItemPostFilterBlockedSubreddit.text = if (blocked.excepted) {
                activity.getString(R.string.post_filter_blocked_excepted)
            } else {
                activity.resources.getQuantityString(
                    R.plurals.post_filter_blocked_post_count, blocked.blockCount, blocked.blockCount
                )
            }
            // The tick is filled in for a subreddit this rule no longer hides, so the state of every
            // row is readable without reading the summary line.
            binding.allowImageViewItemPostFilterBlockedSubreddit.imageTintList =
                ColorStateList.valueOf(if (blocked.excepted) accentColor else primaryIconColor)
            binding.allowImageViewItemPostFilterBlockedSubreddit.contentDescription =
                activity.getString(
                    R.string.content_description_allow_blocked_subreddit, blocked.subredditName
                )
            binding.root.setOnClickListener { onToggleExcepted(blocked) }
            binding.allowImageViewItemPostFilterBlockedSubreddit.setOnClickListener {
                onToggleExcepted(blocked)
            }
        }
    }
}
