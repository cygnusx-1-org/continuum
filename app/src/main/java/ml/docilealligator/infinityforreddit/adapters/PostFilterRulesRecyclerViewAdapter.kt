package ml.docilealligator.infinityforreddit.adapters

import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import ml.docilealligator.infinityforreddit.R
import ml.docilealligator.infinityforreddit.activities.BaseActivity
import ml.docilealligator.infinityforreddit.customtheme.CustomThemeWrapper
import ml.docilealligator.infinityforreddit.customviews.FilterChipStyler
import ml.docilealligator.infinityforreddit.databinding.ItemPostFilterAppliesToBinding
import ml.docilealligator.infinityforreddit.databinding.ItemPostFilterLimitsBinding
import ml.docilealligator.infinityforreddit.databinding.ItemPostFilterNameBinding
import ml.docilealligator.infinityforreddit.databinding.ItemPostFilterPostTypesBinding
import ml.docilealligator.infinityforreddit.databinding.ItemPostFilterRuleBinding
import ml.docilealligator.infinityforreddit.databinding.ItemPostFilterRulesEmptyBinding
import ml.docilealligator.infinityforreddit.databinding.ItemPostFilterRulesHeaderBinding
import ml.docilealligator.infinityforreddit.databinding.ItemPostFilterShowOnlyBinding
import ml.docilealligator.infinityforreddit.postfilter.FilterRule
import ml.docilealligator.infinityforreddit.postfilter.PostFilter
import ml.docilealligator.infinityforreddit.postfilter.PostFilterRange
import ml.docilealligator.infinityforreddit.postfilter.PostFilterUsage
import ml.docilealligator.infinityforreddit.postfilter.RuleField

/**
 * The whole Customize Post Filter screen: the name and the rule list, then the sections that narrow
 * the feed further.
 *
 * The sections sit at fixed positions either side of the rules, so adding, removing or re-filtering
 * a rule only ever notifies the tail between them. That matters because the section rows hold
 * [TextInputEditText]s — rebinding them mid-edit would fight the user's cursor. For the same reason
 * the section rows write straight through into [postFilter] as they are edited, instead of being
 * scraped at save time.
 */
class PostFilterRulesRecyclerViewAdapter(
    private val activity: BaseActivity,
    customThemeWrapper: CustomThemeWrapper,
    private val postFilter: PostFilter,
    showAppliesTo: Boolean,
    private val spanCount: Int,
    private val callback: Callback,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface Callback {
        fun onAddUsageClicked()

        fun onUsageClicked(usage: PostFilterUsage)

        fun onUsageRemoved(usage: PostFilterUsage)

        fun onRuleClicked(rule: FilterRule)

        fun onRuleRemoved(rule: FilterRule)
    }

    private companion object {
        const val VIEW_TYPE_NAME = 0
        const val VIEW_TYPE_APPLIES_TO = 1
        const val VIEW_TYPE_POST_TYPES = 2
        const val VIEW_TYPE_LIMITS = 3
        const val VIEW_TYPE_RULES_HEADER = 4
        const val VIEW_TYPE_RULE = 5
        const val VIEW_TYPE_RULES_EMPTY = 6
        const val VIEW_TYPE_SHOW_ONLY = 7
    }

    private val primaryTextColor = customThemeWrapper.primaryTextColor
    private val secondaryTextColor = customThemeWrapper.secondaryTextColor
    private val primaryIconColor = customThemeWrapper.primaryIconColor
    private val filledCardViewBackgroundColor = customThemeWrapper.filledCardViewBackgroundColor
    private val accentColor = customThemeWrapper.colorAccent
    private val chipStyler = FilterChipStyler(customThemeWrapper, activity.typeface)

    /** Sections above the rule list. Their count never changes for the life of the adapter. */
    private val headerSections: List<Int> = listOf(VIEW_TYPE_NAME, VIEW_TYPE_RULES_HEADER)

    /** Sections below the rule list, in the order they appear on screen. */
    private val footerSections: List<Int> = buildList {
        if (showAppliesTo) add(VIEW_TYPE_APPLIES_TO)
        add(VIEW_TYPE_POST_TYPES)
        add(VIEW_TYPE_SHOW_ONLY)
        add(VIEW_TYPE_LIMITS)
    }

    private var rules: List<FilterRule> = emptyList()
    private var usages: List<PostFilterUsage> = emptyList()
    private var visibleRules: List<FilterRule> = emptyList()

    /** Display-only: which header chips the user has checked. Never affects what gets saved. */
    private val checkedPolarities = LinkedHashSet<Boolean>()
    private val checkedFields = LinkedHashSet<RuleField>()

    /** Chip listeners fire while the header is being (re)bound; ignore those. */
    private var bindingHeader = false

    // Held so a chip tap can refresh counts in place: rebinding through notifyItemChanged would
    // rebuild the chips under the user's finger.
    private var rulesHeaderBinding: ItemPostFilterRulesHeaderBinding? = null
    private var appliesToBinding: ItemPostFilterAppliesToBinding? = null

    /** The rule rows, or the single placeholder shown when none are visible. */
    private val tailSize: Int get() = maxOf(visibleRules.size, 1)

    /** Section rows always span the full width; only rule rows sit side by side. */
    val spanSizeLookup: GridLayoutManager.SpanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int =
            if (getItemViewType(position) == VIEW_TYPE_RULE) 1 else spanCount
    }

    fun setRules(newRules: List<FilterRule>) {
        rules = newRules.toList()
        // Drop display filters for types that no longer have any rules before filtering, not after:
        // deleting the last rule of a checked type would otherwise leave the list filtered by a chip
        // that is about to disappear, showing "no rules match" with nothing left to un-check.
        checkedFields.retainAll { field -> rules.any { it.field == field } }
        refreshTail()
        rulesHeaderBinding?.let { bindRulesHeaderChips(it) }
    }

    fun setUsages(newUsages: List<PostFilterUsage>) {
        usages = newUsages.toList()
        appliesToBinding?.let { bindUsageChips(it) }
    }

    override fun getItemCount(): Int = headerSections.size + tailSize + footerSections.size

    override fun getItemViewType(position: Int): Int = when {
        position < headerSections.size -> headerSections[position]
        position < headerSections.size + tailSize ->
            if (visibleRules.isEmpty()) VIEW_TYPE_RULES_EMPTY else VIEW_TYPE_RULE
        else -> footerSections[position - headerSections.size - tailSize]
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_NAME -> NameViewHolder(ItemPostFilterNameBinding.inflate(inflater, parent, false))
            VIEW_TYPE_APPLIES_TO -> AppliesToViewHolder(ItemPostFilterAppliesToBinding.inflate(inflater, parent, false))
            VIEW_TYPE_POST_TYPES -> PostTypesViewHolder(ItemPostFilterPostTypesBinding.inflate(inflater, parent, false))
            VIEW_TYPE_SHOW_ONLY -> ShowOnlyViewHolder(ItemPostFilterShowOnlyBinding.inflate(inflater, parent, false))
            VIEW_TYPE_LIMITS -> LimitsViewHolder(ItemPostFilterLimitsBinding.inflate(inflater, parent, false))
            VIEW_TYPE_RULES_HEADER ->
                RulesHeaderViewHolder(ItemPostFilterRulesHeaderBinding.inflate(inflater, parent, false))
            VIEW_TYPE_RULE -> RuleViewHolder(ItemPostFilterRuleBinding.inflate(inflater, parent, false))
            else -> EmptyViewHolder(ItemPostFilterRulesEmptyBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is RuleViewHolder -> holder.bind(visibleRules[position - headerSections.size])
            is EmptyViewHolder -> holder.bind(rules.isEmpty())
            is AppliesToViewHolder -> {
                appliesToBinding = holder.binding
                bindUsageChips(holder.binding)
            }
            is RulesHeaderViewHolder -> {
                rulesHeaderBinding = holder.binding
                bindRulesHeaderChips(holder.binding)
            }
        }
    }

    private fun refreshTail() {
        val previousTailSize = tailSize
        visibleRules = rules.filter { rule ->
            // OR inside a chip group, AND across the two groups; an empty group is no constraint.
            (checkedPolarities.isEmpty() || rule.exclude in checkedPolarities) &&
                (checkedFields.isEmpty() || rule.field in checkedFields)
        }
        notifyItemRangeRemoved(headerSections.size, previousTailSize)
        notifyItemRangeInserted(headerSections.size, tailSize)
    }

    // region Rules header

    private fun bindRulesHeaderChips(binding: ItemPostFilterRulesHeaderBinding) {
        bindingHeader = true
        binding.excludeChipItemPostFilterRulesHeader.isChecked = true in checkedPolarities
        binding.includeChipItemPostFilterRulesHeader.isChecked = false in checkedPolarities

        val fieldChipGroup = binding.fieldChipGroupItemPostFilterRulesHeader
        fieldChipGroup.removeAllViews()
        for (field in RuleField.entries) {
            val count = rules.count { it.field == field }
            // A type with nothing in it is not something to filter by, so it does not earn a chip.
            if (count == 0) {
                checkedFields.remove(field)
                continue
            }
            val chip = chipStyler.inflate(fieldChipGroup, R.layout.chip_post_filter_field)
            chip.text =
                activity.getString(R.string.post_filter_field_chip_with_count, fieldLabel(field), count)
            chip.isChecked = field in checkedFields
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (bindingHeader) return@setOnCheckedChangeListener
                if (isChecked) checkedFields.add(field) else checkedFields.remove(field)
                refreshTail()
                updateClearVisibility(binding)
            }
            fieldChipGroup.addView(chip)
        }
        fieldChipGroup.visibility = if (fieldChipGroup.childCount == 0) View.GONE else View.VISIBLE
        updateClearVisibility(binding)
        bindingHeader = false
    }

    private fun updateClearVisibility(binding: ItemPostFilterRulesHeaderBinding) {
        binding.clearTextViewItemPostFilterRulesHeader.visibility =
            if (checkedPolarities.isEmpty() && checkedFields.isEmpty()) View.GONE else View.VISIBLE
    }

    // endregion

    // region Applies to

    private fun bindUsageChips(binding: ItemPostFilterAppliesToBinding) {
        val chipGroup = binding.chipGroupItemPostFilterAppliesTo
        chipGroup.removeAllViews()
        for (usage in usages) {
            val label = usageLabel(usage)
            val chip = chipStyler.inflate(chipGroup, R.layout.chip_post_filter_usage)
            chip.text = label
            chip.setCloseIconContentDescription(
                activity.getString(R.string.content_description_remove_post_filter_rule, label)
            )
            chip.setOnClickListener { callback.onUsageClicked(usage) }
            chip.setOnCloseIconClickListener { callback.onUsageRemoved(usage) }
            chipGroup.addView(chip)
        }
        val addChip = chipStyler.inflate(chipGroup, R.layout.chip_post_filter_add)
        addChip.setText(R.string.post_filter_add_usage)
        addChip.chipIconTint = ColorStateList.valueOf(primaryIconColor)
        addChip.setOnClickListener { callback.onAddUsageClicked() }
        chipGroup.addView(addChip)
    }

    private fun usageLabel(usage: PostFilterUsage): String {
        val named = usage.nameOfUsage != PostFilterUsage.NO_USAGE
        return when (usage.usage) {
            PostFilterUsage.HOME_TYPE -> activity.getString(R.string.post_filter_usage_home)
            PostFilterUsage.SUBREDDIT_TYPE ->
                if (named) {
                    activity.getString(R.string.post_filter_usage_subreddit, usage.nameOfUsage)
                } else {
                    activity.getString(R.string.post_filter_usage_subreddit_all)
                }
            PostFilterUsage.USER_TYPE ->
                if (named) {
                    activity.getString(R.string.post_filter_usage_user, usage.nameOfUsage)
                } else {
                    activity.getString(R.string.post_filter_usage_user_all)
                }
            PostFilterUsage.MULTIREDDIT_TYPE ->
                if (named) {
                    activity.getString(R.string.post_filter_usage_multireddit, usage.nameOfUsage)
                } else {
                    activity.getString(R.string.post_filter_usage_multireddit_all)
                }
            PostFilterUsage.SEARCH_TYPE -> activity.getString(R.string.post_filter_usage_search)
            PostFilterUsage.HISTORY_TYPE -> activity.getString(R.string.post_filter_usage_history)
            PostFilterUsage.UPVOTED_TYPE -> activity.getString(R.string.post_filter_usage_upvoted)
            PostFilterUsage.DOWNVOTED_TYPE -> activity.getString(R.string.post_filter_usage_downvoted)
            PostFilterUsage.HIDDEN_TYPE -> activity.getString(R.string.post_filter_usage_hidden)
            PostFilterUsage.SAVED_TYPE -> activity.getString(R.string.post_filter_usage_saved)
            else -> usage.nameOfUsage
        }
    }

    // endregion

    private fun fieldLabel(field: RuleField): String = activity.getString(
        when (field) {
            RuleField.SUBREDDIT -> R.string.post_filter_field_subreddit
            RuleField.USER -> R.string.post_filter_field_user
            RuleField.FLAIR -> R.string.post_filter_field_flair
            RuleField.DOMAIN -> R.string.post_filter_field_domain
            RuleField.TITLE_KEYWORD -> R.string.post_filter_field_title_keyword
            RuleField.TITLE_REGEX -> R.string.post_filter_field_title_regex
        }
    )

    private fun applyCardTheme(cardView: MaterialCardView) {
        cardView.setCardBackgroundColor(filledCardViewBackgroundColor)
    }

    private fun applyTextInputTheme(textInputLayout: TextInputLayout, editText: TextInputEditText) {
        textInputLayout.boxStrokeColor = primaryTextColor
        textInputLayout.defaultHintTextColor = ColorStateList.valueOf(primaryTextColor)
        editText.setTextColor(primaryTextColor)
        activity.typeface?.let { editText.typeface = it }
    }

    private fun applyLabelTheme(vararg views: TextView) {
        for (view in views) {
            view.setTextColor(primaryTextColor)
            activity.typeface?.let { view.typeface = it }
        }
    }

    private fun applySummaryTheme(vararg views: TextView) {
        for (view in views) {
            view.setTextColor(secondaryTextColor)
            activity.typeface?.let { view.typeface = it }
        }
    }

    private fun afterTextChanged(editText: TextInputEditText, onChanged: (String) -> Unit) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(s: Editable?) = onChanged(s?.toString().orEmpty())
        })
    }

    private fun bindTypeChip(chip: Chip, checked: Boolean, onChanged: (Boolean) -> Unit) {
        chip.isChecked = checked
        chip.setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
    }

    private inner class NameViewHolder(binding: ItemPostFilterNameBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            setIsRecyclable(false)
            applyCardTheme(binding.cardViewItemPostFilterName)
            applyTextInputTheme(
                binding.nameTextInputLayoutItemPostFilterName,
                binding.nameTextInputEditTextCustomizePostFilterActivity
            )
            applySummaryTheme(binding.nameExplanationTextViewItemPostFilterName)
            binding.nameTextInputEditTextCustomizePostFilterActivity.setText(postFilter.name)
            afterTextChanged(binding.nameTextInputEditTextCustomizePostFilterActivity) {
                postFilter.name = it
            }
        }
    }

    private inner class AppliesToViewHolder(val binding: ItemPostFilterAppliesToBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            setIsRecyclable(false)
            applyCardTheme(binding.cardViewItemPostFilterAppliesTo)
            applyLabelTheme(binding.titleTextViewItemPostFilterAppliesTo)
            applySummaryTheme(binding.summaryTextViewItemPostFilterAppliesTo)
        }
    }

    private inner class PostTypesViewHolder(binding: ItemPostFilterPostTypesBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            setIsRecyclable(false)
            applyCardTheme(binding.cardViewItemPostFilterPostTypes)
            applyLabelTheme(binding.titleTextViewItemPostFilterPostTypes)
            applySummaryTheme(binding.summaryTextViewItemPostFilterPostTypes)
            chipStyler.styleAll(binding.postTypesChipGroupItemPostFilterPostTypes)

            bindTypeChip(binding.postTypeTextChipItemPostFilterPostTypes, postFilter.containTextType) {
                postFilter.containTextType = it
            }
            bindTypeChip(binding.postTypeLinkChipItemPostFilterPostTypes, postFilter.containLinkType) {
                postFilter.containLinkType = it
            }
            bindTypeChip(binding.postTypeImageChipItemPostFilterPostTypes, postFilter.containImageType) {
                postFilter.containImageType = it
            }
            bindTypeChip(binding.postTypeGifChipItemPostFilterPostTypes, postFilter.containGifType) {
                postFilter.containGifType = it
            }
            bindTypeChip(binding.postTypeVideoChipItemPostFilterPostTypes, postFilter.containVideoType) {
                postFilter.containVideoType = it
            }
            bindTypeChip(binding.postTypeGalleryChipItemPostFilterPostTypes, postFilter.containGalleryType) {
                postFilter.containGalleryType = it
            }
        }
    }

    private inner class ShowOnlyViewHolder(binding: ItemPostFilterShowOnlyBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            setIsRecyclable(false)
            applyCardTheme(binding.cardViewItemPostFilterShowOnly)
            applyLabelTheme(binding.titleTextViewItemPostFilterShowOnly)
            applySummaryTheme(binding.summaryTextViewItemPostFilterShowOnly)
            chipStyler.styleAll(binding.showOnlyChipGroupItemPostFilterShowOnly)

            bindTypeChip(binding.onlyNsfwChipItemPostFilterShowOnly, postFilter.onlyNSFW) {
                postFilter.onlyNSFW = it
            }
            bindTypeChip(binding.onlySpoilerChipItemPostFilterShowOnly, postFilter.onlySpoiler) {
                postFilter.onlySpoiler = it
            }
        }
    }

    private inner class LimitsViewHolder(binding: ItemPostFilterLimitsBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            setIsRecyclable(false)
            applyCardTheme(binding.cardViewItemPostFilterLimits)
            applyLabelTheme(binding.titleTextViewItemPostFilterLimits)
            applySummaryTheme(binding.summaryTextViewItemPostFilterLimits)

            bindRange(
                binding.voteTextInputLayoutItemPostFilterLimits,
                binding.voteTextInputEditTextItemPostFilterLimits,
                postFilter.minVote,
                postFilter.maxVote
            ) { min, max ->
                postFilter.minVote = min
                postFilter.maxVote = max
            }
            bindRange(
                binding.commentsTextInputLayoutItemPostFilterLimits,
                binding.commentsTextInputEditTextItemPostFilterLimits,
                postFilter.minComments,
                postFilter.maxComments
            ) { min, max ->
                postFilter.minComments = min
                postFilter.maxComments = max
            }
        }

        /** One box per limit, holding both ends as `100-5000`; see [PostFilterRange]. */
        private fun bindRange(
            textInputLayout: TextInputLayout,
            editText: TextInputEditText,
            min: Int,
            max: Int,
            onChanged: (Int, Int) -> Unit,
        ) {
            applyTextInputTheme(textInputLayout, editText)
            editText.setText(PostFilterRange.format(min, max))
            afterTextChanged(editText) {
                // An upper-only limit has to be written 0-5000; a bare -5000 reads as a negative
                // number, so it is called out rather than silently taken as an upper bound.
                textInputLayout.error = if (PostFilterRange.isMissingLowerBound(it)) {
                    activity.getString(R.string.post_filter_limit_needs_lower_bound)
                } else {
                    null
                }
                val (newMin, newMax) = PostFilterRange.parse(it)
                onChanged(newMin, newMax)
            }
        }
    }

    private inner class RulesHeaderViewHolder(val binding: ItemPostFilterRulesHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            setIsRecyclable(false)
            applyLabelTheme(binding.titleTextViewItemPostFilterRulesHeader)
            applySummaryTheme(binding.summaryTextViewItemPostFilterRulesHeader)
            binding.clearTextViewItemPostFilterRulesHeader.setTextColor(accentColor)
            activity.typeface?.let { binding.clearTextViewItemPostFilterRulesHeader.typeface = it }
            chipStyler.styleAll(binding.polarityChipGroupItemPostFilterRulesHeader)

            binding.excludeChipItemPostFilterRulesHeader.setOnCheckedChangeListener { _, isChecked ->
                togglePolarity(true, isChecked)
            }
            binding.includeChipItemPostFilterRulesHeader.setOnCheckedChangeListener { _, isChecked ->
                togglePolarity(false, isChecked)
            }
            binding.clearTextViewItemPostFilterRulesHeader.setOnClickListener {
                checkedPolarities.clear()
                checkedFields.clear()
                bindRulesHeaderChips(binding)
                refreshTail()
            }
        }

        private fun togglePolarity(exclude: Boolean, checked: Boolean) {
            if (bindingHeader) {
                return
            }
            if (checked) checkedPolarities.add(exclude) else checkedPolarities.remove(exclude)
            refreshTail()
            updateClearVisibility(binding)
        }
    }

    private inner class RuleViewHolder(private val binding: ItemPostFilterRuleBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            applyLabelTheme(binding.valueTextViewItemPostFilterRule)
            applySummaryTheme(binding.summaryTextViewItemPostFilterRule)
            binding.deleteImageViewItemPostFilterRule.imageTintList = ColorStateList.valueOf(primaryIconColor)
        }

        fun bind(rule: FilterRule) {
            binding.valueTextViewItemPostFilterRule.text = rule.value
            binding.summaryTextViewItemPostFilterRule.text = activity.getString(
                R.string.post_filter_rule_summary,
                activity.getString(
                    if (rule.exclude) R.string.post_filter_rule_exclude else R.string.post_filter_rule_include
                ),
                fieldLabel(rule.field)
            )
            binding.deleteImageViewItemPostFilterRule.contentDescription =
                activity.getString(R.string.content_description_remove_post_filter_rule, rule.value)
            binding.root.setOnClickListener { callback.onRuleClicked(rule) }
            binding.deleteImageViewItemPostFilterRule.setOnClickListener { callback.onRuleRemoved(rule) }
        }
    }

    private inner class EmptyViewHolder(private val binding: ItemPostFilterRulesEmptyBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            applySummaryTheme(binding.messageTextViewItemPostFilterRulesEmpty)
        }

        fun bind(noRulesAtAll: Boolean) {
            binding.messageTextViewItemPostFilterRulesEmpty.setText(
                if (noRulesAtAll) R.string.post_filter_rules_empty else R.string.post_filter_rules_no_match
            )
        }
    }
}
