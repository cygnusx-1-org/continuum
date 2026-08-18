package ml.docilealligator.infinityforreddit.adapters

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.activities.BaseActivity
import ml.docilealligator.infinityforreddit.adapters.CommentsFooterRecyclerViewAdapter.Constants.VIEW_TYPE_IS_LOADING_MORE_COMMENTS
import ml.docilealligator.infinityforreddit.adapters.CommentsFooterRecyclerViewAdapter.Constants.VIEW_TYPE_LOAD_MORE_COMMENTS_FAILED
import ml.docilealligator.infinityforreddit.databinding.ItemCommentFooterErrorBinding
import ml.docilealligator.infinityforreddit.databinding.ItemCommentFooterLoadingBinding

class CommentsFooterRecyclerViewAdapter(
    private val activity: BaseActivity,
    private val onRetryFetchingMoreComments: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private object Constants {
        const val VIEW_TYPE_IS_LOADING_MORE_COMMENTS: Int = 15
        const val VIEW_TYPE_LOAD_MORE_COMMENTS_FAILED: Int = 16
    }

    // See CommentsStatusRecyclerViewAdapter: notifying unconditionally would invalidate the whole
    // ConcatAdapter on every view model emission, collapse and vote included.
    private var stateChanged = false

    var hasMoreChildren: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                stateChanged = true
            }
        }
    // Deliberately not tracked: nothing here renders from it. getItemCount() reads hasMoreChildren
    // and getItemViewType() reads loadMoreChildrenSuccess. It flips on every fetch-more round trip,
    // and the fetch starts from onScrolled, so marking it dirty would rebind every visible comment
    // mid-scroll for a footer that has not changed.
    var isLoadingMoreChildren: Boolean = true
    var loadMoreChildrenSuccess: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                stateChanged = true
            }
        }

    @SuppressLint("NotifyDataSetChanged")
    fun notifyIfStateChanged() {
        if (stateChanged) {
            stateChanged = false
            notifyDataSetChanged()
        }
    }

    override fun getItemViewType(position: Int): Int {
        if (!loadMoreChildrenSuccess) {
            return VIEW_TYPE_LOAD_MORE_COMMENTS_FAILED
        } else {
            return VIEW_TYPE_IS_LOADING_MORE_COMMENTS
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): RecyclerView.ViewHolder {
        if (viewType == VIEW_TYPE_IS_LOADING_MORE_COMMENTS) {
            return IsLoadingMoreCommentsViewHolder(
                ItemCommentFooterLoadingBinding.inflate(
                    LayoutInflater.from(parent.getContext()),
                    parent,
                    false
                ),
                activity.customThemeWrapper.colorAccent
            )
        } else {
            return LoadMoreCommentsFailedViewHolder(
                ItemCommentFooterErrorBinding.inflate(
                    LayoutInflater.from(parent.getContext()),
                    parent,
                    false
                ),
                activity.customThemeWrapper.secondaryTextColor,
                activity.customThemeWrapper.colorPrimaryLightTheme,
                activity.customThemeWrapper.buttonTextColor,
                activity.typeface,
                onRetryFetchingMoreComments
            )
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int
    ) {

    }

    override fun getItemCount(): Int {
        return if (hasMoreChildren) 1 else 0
    }

    class IsLoadingMoreCommentsViewHolder internal constructor(
        binding: ItemCommentFooterLoadingBinding,
        colorAccent: Int
    ) :
        RecyclerView.ViewHolder(binding.getRoot()) {
        var binding: ItemCommentFooterLoadingBinding?

        init {
            this.binding = binding
            binding.progressBarItemCommentFooterLoading.setIndicatorColor(colorAccent)
        }
    }

    class LoadMoreCommentsFailedViewHolder internal constructor(
        binding: ItemCommentFooterErrorBinding,
        secondaryTextColor: Int,
        colorPrimaryLightTheme: Int,
        buttonTextColor: Int,
        typeface: Typeface?,
        onRetryFetchingMoreComments: () -> Unit
    ) : RecyclerView.ViewHolder(binding.getRoot()) {
        var binding: ItemCommentFooterErrorBinding?

        init {
            this.binding = binding
            typeface?.let {
                binding.errorTextViewItemCommentFooterError.setTypeface(it)
                binding.retryButtonItemCommentFooterError.setTypeface(it)
            }
            binding.errorTextViewItemCommentFooterError.setText(R.string.load_comments_failed)
            binding.retryButtonItemCommentFooterError.setOnClickListener {
                onRetryFetchingMoreComments()
            }
            binding.errorTextViewItemCommentFooterError.setTextColor(secondaryTextColor)
            binding.retryButtonItemCommentFooterError.setBackgroundTintList(
                ColorStateList.valueOf(
                    colorPrimaryLightTheme
                )
            )
            binding.retryButtonItemCommentFooterError.setTextColor(buttonTextColor)
        }
    }
}